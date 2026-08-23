package org.example.seniorlifebookingagent.domain.transport;

import com.embabel.agent.domain.library.HasContent;
import org.example.seniorlifebookingagent.support.KoreanDateTime;

public record ReservationCompleted(
    String reservationNumber,
    TransportPlan transportPlan
) implements HasContent {

    @Override
    public String getContent() {
        return """
               교통편 예약이 잘 끝났습니다.

               가는 방법:
               %s
               떠나는 시간: %s
               도착하는 시간: %s
               교통비 모두: %,d원
               기억해 두실 예약번호: %s
               """.formatted(
            transportPlan.summary(),
            KoreanDateTime.format(transportPlan.departAt()),
            KoreanDateTime.format(transportPlan.arriveAt()),
            transportPlan.totalPrice(),
            reservationNumber
        );
    }
}
