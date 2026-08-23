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
import org.example.seniorlifebookingagent.domain.hospital.ApprovedHospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalRequest;
import org.example.seniorlifebookingagent.domain.hospital.HospitalReservationCompleted;
import org.example.seniorlifebookingagent.support.KoreanDateTime;
import org.example.seniorlifebookingagent.tool.HospitalTool;

@Agent(description = "Find and book an appointment for the senior")
public class HospitalAppointmentAgent {

    private final HospitalTool hospitalTool;
    private final MockHospitalReservation mockReservation;

    HospitalAppointmentAgent(HospitalTool hospitalTool, MockHospitalReservation mockReservation) {
        this.hospitalTool = hospitalTool;
        this.mockReservation = mockReservation;
    }

    @Action
    public HospitalRequest understandRequest(UserInput userInput, OperationContext context) {
        var request = context.ai()
                             .withDefaultLlm()
                             .createObject(
                          """
                          Parse the hospital appointment request.
                          Today in Korea is %s. Resolve relative dates from this date.
                          The request may be Korean or English. Return hospital, department, region,
                          and broad time values as their canonical Korean names for public-data lookup.
                          region must be one of 서울, 부산, 인천, 대구, 광주, 대전, 울산, 세종, 경기,
                          강원, 충북, 충남, 전북, 전남, 경북, 경남, 제주. Convert a city or district
                          to its containing region, for example 수원 to 경기 and 천안 to 충남.
                          If no hospital is named, keep hospitalName null so hospitals can be recommended
                          from region and department. Never invent a hospital name.
                          
                          Request:
                          %s
                          """.formatted(KoreanDateTime.today(), userInput),
                          HospitalRequest.class
                      );
        return new HospitalRequest(
            request.region(), request.hospitalName(), request.department(),
            KoreanDateTime.resolveRelativeDate(userInput.toString(), request.date()), request.preferredTime()
        );
    }

    @Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
    public HospitalAppointment searchHospital(HospitalRequest request) {
        return hospitalTool.search(request).getFirst();
    }

    public HospitalTool.SearchPage searchPage(HospitalRequest request, int page, int size) {
        return hospitalTool.searchPage(request, page, size);
    }

    @Action
    public ApprovedHospitalAppointment confirmAppointment(HospitalAppointment appointment) {
        var confirmAskMessage = """
                                병원 예약 내용을 확인해 주세요.

                                병원: %s
                                진료과: %s
                                가는 날과 시간: %s
                                병원 주소: %s
                                병원 운영시간: %s
                                진료비 예상(예약 결제 제외): 약 %,d원

                                이대로 예약할까요?
                                예약하려면 '네', 하지 않으려면 '아니요'라고 말씀해 주세요.
                                """.formatted(
            appointment.hospitalName(),
            appointment.department(),
            KoreanDateTime.format(appointment.appointmentTime()),
            appointment.address(),
            appointment.operatingHours(),
            appointment.fee()
        );

        return WaitFor.confirmation(new ApprovedHospitalAppointment(appointment), confirmAskMessage);
    }

    @Action
    @AchievesGoal(description = "Hospital appointment reservation completed")
    public HospitalReservationCompleted reserve(ApprovedHospitalAppointment approvedAppointment) {
        mockReservation.reserve();
        var reservationNumber = "DEMO-H-%s-%04d".formatted(
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
            ThreadLocalRandom.current().nextInt(10_000)
        );

        return new HospitalReservationCompleted(
            reservationNumber,
            approvedAppointment.appointment()
        );
    }
}
