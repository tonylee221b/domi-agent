package org.example.seniorlifebookingagent.domain.hospital;

import com.embabel.agent.domain.library.HasContent;
import org.example.seniorlifebookingagent.support.KoreanDateTime;

public record HospitalReservationCompleted(
    String reservationNumber,
    HospitalAppointment appointment
) implements HasContent {

    @Override
    public String getContent() {
        return """
               병원 예약이 잘 끝났습니다.

               병원: %s
               진료과: %s
               가는 날과 시간: %s
               병원 주소: %s
               병원 운영시간: %s
               진료비 예상(별도): 약 %,d원
               기억해 두실 예약번호: %s
               """.formatted(
            appointment.hospitalName(),
            appointment.department(),
            KoreanDateTime.format(appointment.appointmentTime()),
            appointment.address(),
            appointment.operatingHours(),
            appointment.fee(),
            reservationNumber
        );
    }
}
