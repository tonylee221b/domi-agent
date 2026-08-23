package org.example.seniorlifebookingagent.domain.visit;

import com.embabel.agent.domain.library.HasContent;
import org.example.seniorlifebookingagent.support.KoreanDateTime;

public record HospitalVisitReservationCompleted(
    String hospitalReservationNumber,
    String transportReservationNumber,
    HospitalVisitPlan plan
) implements HasContent {

    @Override
    public String getContent() {
        return """
               병원과 교통편 예약이 모두 잘 끝났습니다.

               병원: %s %s
               병원 가는 날과 시간: %s
               가는 방법:
               %s
               진료비 예상(별도): %,d원
               교통비 결제: %,d원
               총 결제: %,d원
               기억해 두실 병원 예약번호: %s
               기억해 두실 교통 예약번호: %s
               """.formatted(
            plan.appointment().hospitalName(),
            plan.appointment().department(),
            KoreanDateTime.format(plan.appointment().appointmentTime()),
            plan.transportPlan().summary(),
            plan.appointment().fee(),
            plan.transportPlan().totalPrice(),
            plan.totalPrice(),
            hospitalReservationNumber,
            transportReservationNumber
        );
    }
}
