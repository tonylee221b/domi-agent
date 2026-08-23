package org.example.seniorlifebookingagent.domain.visit;

import java.time.Duration;
import org.example.seniorlifebookingagent.domain.hospital.HospitalAppointment;
import org.example.seniorlifebookingagent.domain.transport.TransportPlan;
import org.example.seniorlifebookingagent.support.KoreanDateTime;

public record HospitalVisitPlan(
    HospitalAppointment appointment,
    TransportPlan transportPlan
) {
    public long arrivalBufferMinutes() {
        return Duration.between(transportPlan.arriveAt(), appointment.appointmentTime()).toMinutes();
    }

    public int totalPrice() {
        return transportPlan.totalPrice();
    }

    public String selectionSummary() {
        return "%s, 병원 %s, 결제 금액 %,d원".formatted(
            transportPlan.choiceSummary(),
            KoreanDateTime.formatTime(appointment.appointmentTime()),
            totalPrice()
        );
    }
}
