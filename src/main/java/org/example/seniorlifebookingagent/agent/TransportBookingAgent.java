package org.example.seniorlifebookingagent.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.hitl.WaitFor;
import com.embabel.agent.core.ActionRetryPolicy;
import com.embabel.agent.domain.io.UserInput;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.example.seniorlifebookingagent.domain.transport.ApprovedTransportPlan;
import org.example.seniorlifebookingagent.domain.transport.ReservationCompleted;
import org.example.seniorlifebookingagent.domain.transport.TransportOptions;
import org.example.seniorlifebookingagent.domain.transport.TransportPlan;
import org.example.seniorlifebookingagent.domain.transport.TransportRecommendations;
import org.example.seniorlifebookingagent.domain.transport.TransportRequest;
import org.example.seniorlifebookingagent.domain.transport.TransportSelection;
import org.example.seniorlifebookingagent.support.KoreanDateTime;
import org.example.seniorlifebookingagent.tool.TransportTool;

@Agent(description = "Find and reserve transportation for the senior")
public class TransportBookingAgent {

    private final TransportTool transportTool;

    TransportBookingAgent(TransportTool tool) {
        this.transportTool = tool;
    }

    @Action
    public TransportRequest understandRequest(UserInput userInput, OperationContext context) {
        var request = context.ai()
                             .withDefaultLlm()
                             .createObject(
                          """
                          Parse the travel request.
                          Today in Korea is %s. Resolve relative dates from this date.
                          The request may be Korean or English. Return place names and broad time values
                          as their canonical Korean names for public-data lookup.
                          Treat broad time words such as morning or afternoon as preferredDepartureTime,
                          unless the user explicitly says they want to arrive by that time.
                          Keep broad Korean time words such as "아침", "오전", "오후", "저녁" unchanged.
                          Never convert a broad time word to an exact clock time and never fill both time fields
                          from one broad time expression.
                          
                          Request:
                          %s
                          """.formatted(KoreanDateTime.today(), userInput)
                          , TransportRequest.class
                      );
        return new TransportRequest(
            request.origin(), request.destination(),
            KoreanDateTime.resolveRelativeDate(userInput.toString(), request.date()),
            request.preferredDepartureTime(), request.preferredArrivalTime()
        );
    }

    @Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
    public TransportOptions searchTransport(TransportRequest request) {
        return new TransportOptions(transportTool.search(request));
    }

    @Action
    public TransportRecommendations recommendTransportation(
        TransportRequest request,
        TransportOptions options,
        OperationContext context
    ) {
        var candidates = IntStream.range(0, options.plans().size())
                                  .mapToObj(i -> "%d번: %s, 소요시간 %d분, 환승 %d회".formatted(
                                      i + 1,
                                      options.plans().get(i).choiceSummary(),
                                      Duration.between(
                                          options.plans().get(i).departAt(), options.plans().get(i).arriveAt()).toMinutes(),
                                      options.plans().get(i).legs().size() - 1))
                                  .collect(Collectors.joining("\n"));
        var ranking = context.ai().withDefaultLlm().createObject(
            """
            시니어 사용자를 위한 교통편 추천 전문가입니다.
            실제 조회 후보만 비교하고 모든 후보 번호를 정확히 한 번씩 순위대로 반환하세요.
            사용자의 출발/도착 시간 조건 적합성을 가장 우선하고, 소요시간과 환승 횟수,
            요금을 차례로 비교하세요. reasons에는 사실에 근거한 12자 이내의 짧은 명사구를
            불릿 기호 없이 1~2개만 반환하세요. 예: ["가장 저렴", "편안한 우등 좌석"]
            후보에 없는 시간, 요금, 교통편은 만들지 마세요.

            요청: %s
            후보:
            %s
            """.formatted(request, candidates),
            Ranking.class
        );
        return applyRanking(options, ranking);
    }

    TransportRecommendations applyRanking(TransportOptions options, Ranking ranking) {
        var numbers = ranking.rankedCandidates().stream().map(RankedCandidate::candidateNumber).toList();
        if (numbers.size() != options.plans().size()
            || new HashSet<>(numbers).size() != numbers.size()
            || numbers.stream().anyMatch(number -> number < 1 || number > options.plans().size())
            || ranking.rankedCandidates().stream().anyMatch(candidate ->
                candidate.reasons() == null || candidate.reasons().isEmpty() || candidate.reasons().size() > 2
                    || candidate.reasons().stream().anyMatch(String::isBlank))) {
            throw new IllegalArgumentException("AI가 교통편 추천 순위를 올바르게 만들지 못했습니다.");
        }
        return new TransportRecommendations(ranking.rankedCandidates().stream()
            .map(candidate -> new TransportRecommendations.Recommendation(
                options.plans().get(candidate.candidateNumber() - 1),
                candidate.reasons().stream().map(reason -> "• " + reason).collect(Collectors.joining("\n"))))
            .toList());
    }

    public record Ranking(List<RankedCandidate> rankedCandidates) {
    }

    public record RankedCandidate(int candidateNumber, List<String> reasons) {
    }

    @Action
    public TransportPlan chooseTransportation(TransportOptions options) {
        var choices = IntStream.range(0, options.plans().size())
                               .mapToObj(i -> "%d번: %s".formatted(
                                   i + 1,
                                   options.plans().get(i).choiceSummary()))
                               .collect(Collectors.joining("\n"));
        var selection = WaitFor.formSubmission(
            "이용할 수 있는 교통편을 찾았습니다.\n"
                + "아래 내용을 보시고 원하시는 번호를 골라 주세요.\n\n"
                + choices,
            TransportSelection.class
        );
        if (selection.optionNumber() > options.plans().size()) {
            throw new IllegalArgumentException("화면에 보이는 교통편 번호 중에서 골라 주세요.");
        }
        return options.plans().get(selection.optionNumber() - 1);
    }

    @Action
    public ApprovedTransportPlan confirmTransportation(TransportPlan plan) {
        var confirmAskMessage = """
                                교통편 예약 내용을 확인해 주세요.

                                가는 방법:
                                %s
                                떠나는 시간: %s
                                도착하는 시간: %s
                                교통비 모두: %,d원

                                이대로 예약할까요?
                                예약하려면 '네', 하지 않으려면 '아니요'라고 말씀해 주세요.
                                """
            .formatted(
                plan.summary(),
                KoreanDateTime.format(plan.departAt()),
                KoreanDateTime.format(plan.arriveAt()),
                plan.totalPrice()
            );

        return WaitFor.confirmation(new ApprovedTransportPlan(plan), confirmAskMessage);
    }

    @Action
    @AchievesGoal(description = "Transportation reservation completed")
    public ReservationCompleted reserve(ApprovedTransportPlan approvedTransportPlan) {
        var reservationNumber = "DEMO-T-%s-%04d".formatted(
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
            ThreadLocalRandom.current().nextInt(10_000)
        );

        return new ReservationCompleted(reservationNumber, approvedTransportPlan.plan());
    }
}
