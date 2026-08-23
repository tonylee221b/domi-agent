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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.example.seniorlifebookingagent.domain.hospital.HospitalRequest;
import org.example.seniorlifebookingagent.domain.transport.TransportRequest;
import org.example.seniorlifebookingagent.domain.visit.ApprovedHospitalVisitPlan;
import org.example.seniorlifebookingagent.domain.visit.AlternativeSelection;
import org.example.seniorlifebookingagent.domain.visit.ConflictReason;
import org.example.seniorlifebookingagent.domain.visit.HospitalVisitAlternative;
import org.example.seniorlifebookingagent.domain.visit.HospitalVisitPlan;
import org.example.seniorlifebookingagent.domain.visit.HospitalVisitPlanningResult;
import org.example.seniorlifebookingagent.domain.visit.HospitalVisitRequest;
import org.example.seniorlifebookingagent.domain.visit.HospitalVisitReservationCompleted;
import org.example.seniorlifebookingagent.support.KoreanDateTime;
import org.example.seniorlifebookingagent.tool.HospitalTool;
import org.example.seniorlifebookingagent.tool.TransportTool;

@Agent(description = "Find and reserve both a hospital appointment and door-to-door transportation for the senior")
public class HospitalVisitBookingAgent {

    private static final int MINIMUM_ARRIVAL_BUFFER_MINUTES = 20;

    private final HospitalTool hospitalTool;
    private final TransportTool transportTool;
    private final MockHospitalReservation mockReservation;

    HospitalVisitBookingAgent(
        HospitalTool hospitalTool,
        TransportTool transportTool,
        MockHospitalReservation mockReservation
    ) {
        this.hospitalTool = hospitalTool;
        this.transportTool = transportTool;
        this.mockReservation = mockReservation;
    }

    @Action
    public HospitalVisitRequest understandRequest(UserInput userInput, OperationContext context) {
        var request = context.ai()
                             .withDefaultLlm()
                             .createObject(
                          """
                          Parse a request that needs both a hospital appointment and transportation.
                          Today in Korea is %s. Resolve relative dates from this date.
                          Respond in Korean and keep place, hospital, and department names in Korean.
                          origin is the departure place before "에서"; never leave it null when stated.
                          region is the hospital's region, not the departure place.
                          If no hospital is named, keep hospitalName null so hospitals can be recommended
                          from region and department. Never invent a hospital name.
                          Example: "대전에서 서울대병원 정형외과 가고 싶어" means
                          origin="대전", region="서울", hospitalName="서울대병원", department="정형외과".
                          Set requestedAppointmentTime only when the user gives an exact appointment time.
                          Convert transport restrictions such as "8시 30분 이전 기차는 싫어" into
                          transportConstraints using mode TRAIN and notBefore 08:30.
                          Allowed transport modes: TRAIN, INTERCITY_BUS, EXPRESS_BUS.

                          Request:
                          %s
                          """.formatted(KoreanDateTime.today(), userInput),
                          HospitalVisitRequest.class
                      );
        return new HospitalVisitRequest(
            request.origin(), request.region(), request.hospitalName(), request.department(),
            KoreanDateTime.resolveRelativeDate(userInput.toString(), request.date()), request.preferredTime(),
            request.requestedAppointmentTime(), request.transportConstraints()
        );
    }

    @Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
    public HospitalVisitPlanningResult planVisit(HospitalVisitRequest request) {
        return planVisitPage(request, 1, 10).result();
    }

    public PlanningPage planVisitPage(HospitalVisitRequest request, int page, int size) {
        var appointmentPage = hospitalTool.searchPage(new HospitalRequest(
            request.region(),
            request.hospitalName(),
            request.department(),
            request.date(),
            request.preferredTime()
        ), page, size);

        var safePlans = appointmentPage.appointments().stream()
                                    .flatMap(appointment -> transportTool.search(new TransportRequest(
                                        request.origin(),
                                        appointment.address(),
                                        request.date(),
                                        null,
                                        appointment.appointmentTime().toLocalTime().toString()
                                    )).stream().map(plan -> new HospitalVisitPlan(appointment, plan)))
                                    .filter(this::arrivesSafely)
                                    .toList();
        var matchingPlans = safePlans.stream()
                                     .filter(plan -> matchesAppointmentTime(request, plan))
                                     .filter(plan -> matchesTransportConstraints(request, plan))
                                     .toList();

        if (!matchingPlans.isEmpty()) {
            return new PlanningPage(
                new HospitalVisitPlanningResult(matchingPlans, List.of()), appointmentPage.hasMore());
        }

        var alternatives = new ArrayList<HospitalVisitAlternative>();
        safePlans.stream()
                 .filter(plan -> !matchesAppointmentTime(request, plan))
                 .filter(plan -> matchesTransportConstraints(request, plan))
                 .findFirst()
                 .ifPresent(plan -> alternatives.add(new HospitalVisitAlternative(
                     plan, ConflictReason.APPOINTMENT_TIME_CHANGED)));
        safePlans.stream()
                 .filter(plan -> matchesAppointmentTime(request, plan))
                 .filter(plan -> !matchesTransportConstraints(request, plan))
                 .findFirst()
                 .ifPresent(plan -> alternatives.add(new HospitalVisitAlternative(
                     plan, ConflictReason.TRANSPORT_CONSTRAINT_RELAXED)));

        return new PlanningPage(
            new HospitalVisitPlanningResult(matchingPlans, alternatives), appointmentPage.hasMore());
    }

    @Action
    public HospitalVisitPlan choosePlan(HospitalVisitPlanningResult result) {
        if (result.hasConflict() && result.alternatives().isEmpty()) {
            throw new IllegalStateException("지금 예약할 수 있는 일정이 없습니다. 다른 날짜나 시간을 말씀해 주세요.");
        }

        var plans = result.hasConflict()
            ? result.alternatives().stream().map(HospitalVisitAlternative::plan).toList()
            : result.matchingPlans();
        var options = IntStream.range(0, plans.size())
                               .mapToObj(i -> "%d번: %s".formatted(
                                   i + 1,
                                   result.hasConflict()
                                       ? result.alternatives().get(i).summary()
                                       : plans.get(i).selectionSummary()))
                               .collect(Collectors.joining("\n"));
        var selection = WaitFor.formSubmission(
            (result.hasConflict()
                ? "말씀하신 조건에 꼭 맞는 일정이 없습니다. 대신 가능한 일정을 찾았습니다.\n"
                : "병원에 가실 때 이용할 수 있는 교통편을 찾았습니다.\n")
                + "아래 내용을 보시고 원하시는 번호를 골라 주세요.\n\n"
                + options,
            AlternativeSelection.class
        );
        if (selection.optionNumber() > plans.size()) {
            throw new IllegalArgumentException("화면에 보이는 번호 중에서 골라 주세요.");
        }
        return plans.get(selection.optionNumber() - 1);
    }

    @Action
    public ApprovedHospitalVisitPlan confirmVisit(HospitalVisitPlan plan) {
        var message = """
                      병원과 교통편 예약 내용을 확인해 주세요.

                      병원: %s
                      진료과: %s
                      병원 가는 날과 시간: %s
                      병원 운영시간: %s
                      가는 방법:
                      %s
                      진료비 예상(결제 제외): %,d원
                      교통비: %,d원
                      총 결제: %,d원

                      이대로 모두 예약할까요?
                      예약하려면 '네', 하지 않으려면 '아니요'라고 말씀해 주세요.
                      """.formatted(
            plan.appointment().hospitalName(),
            plan.appointment().department(),
            KoreanDateTime.format(plan.appointment().appointmentTime()),
            plan.appointment().operatingHours(),
            plan.transportPlan().summary(),
            plan.appointment().fee(),
            plan.transportPlan().totalPrice(),
            plan.totalPrice()
        );

        return WaitFor.confirmation(new ApprovedHospitalVisitPlan(plan), message);
    }

    @Action
    @AchievesGoal(description = "Hospital appointment and transportation reservations completed")
    public HospitalVisitReservationCompleted reserve(ApprovedHospitalVisitPlan approvedPlan) {
        mockReservation.reserve();
        return new HospitalVisitReservationCompleted(
            reservationNumber("H"),
            reservationNumber("T"),
            approvedPlan.plan()
        );
    }

    private String reservationNumber(String type) {
        return "DEMO-%s-%s-%04d".formatted(
            type,
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
            ThreadLocalRandom.current().nextInt(10_000)
        );
    }

    private boolean arrivesSafely(HospitalVisitPlan plan) {
        return !plan.transportPlan().arriveAt()
                    .plusMinutes(MINIMUM_ARRIVAL_BUFFER_MINUTES)
                    .isAfter(plan.appointment().appointmentTime());
    }

    private boolean matchesAppointmentTime(HospitalVisitRequest request, HospitalVisitPlan plan) {
        return request.requestedAppointmentTime() == null
            || request.requestedAppointmentTime().equals(plan.appointment().appointmentTime().toLocalTime());
    }

    private boolean matchesTransportConstraints(HospitalVisitRequest request, HospitalVisitPlan plan) {
        return request.transportConstraints().stream()
                      .allMatch(constraint -> constraint.allows(plan.transportPlan()));
    }

    public record PlanningPage(HospitalVisitPlanningResult result, boolean hasMore) {
    }
}
