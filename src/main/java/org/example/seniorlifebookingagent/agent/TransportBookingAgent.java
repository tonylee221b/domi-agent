package org.example.seniorlifebookingagent.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.hitl.WaitFor;
import com.embabel.agent.core.ActionRetryPolicy;
import com.embabel.agent.domain.io.UserInput;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.example.seniorlifebookingagent.domain.transport.ApprovedTransportPlan;
import org.example.seniorlifebookingagent.domain.transport.ReservationCompleted;
import org.example.seniorlifebookingagent.domain.transport.TransportOptions;
import org.example.seniorlifebookingagent.domain.transport.TransportPlan;
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
