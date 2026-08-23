package org.example.seniorlifebookingagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.example.seniorlifebookingagent.domain.hospital.HospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalRequest;
import org.example.seniorlifebookingagent.domain.transport.TransportLeg;
import org.example.seniorlifebookingagent.domain.transport.TransportMode;
import org.example.seniorlifebookingagent.domain.transport.TransportPlan;
import org.example.seniorlifebookingagent.domain.transport.TransportRequest;
import org.example.seniorlifebookingagent.domain.visit.ApprovedHospitalVisitPlan;
import org.example.seniorlifebookingagent.domain.visit.ConflictReason;
import org.example.seniorlifebookingagent.domain.visit.HospitalVisitRequest;
import org.example.seniorlifebookingagent.domain.visit.TransportConstraint;
import org.junit.jupiter.api.Test;

class HospitalVisitBookingAgentTest {

    @Test
    void plansAndReservesHospitalVisitWithDoorToDoorTransport() {
        var request = new HospitalVisitRequest(
            "대전",
            "서울 관악구",
            "서울대병원",
            "정형외과",
            LocalDate.of(2026, 8, 26),
            "오전",
            null,
            List.of()
        );
        var agent = new HospitalVisitBookingAgent(
            this::appointments, this::transportPlans, new MockHospitalReservation(() -> 1.0));

        var plan = agent.planVisit(request).matchingPlans().getFirst();
        var completed = agent.reserve(new ApprovedHospitalVisitPlan(plan));

        assertEquals(LocalTime.of(10, 30), plan.appointment().appointmentTime().toLocalTime());
        assertEquals(TransportMode.TRAIN, plan.transportPlan().primaryMode());
        assertTrue(plan.arrivalBufferMinutes() >= 20);
        assertEquals(60_900, plan.totalPrice());
        assertTrue(completed.hospitalReservationNumber().startsWith("DEMO-H-"));
        assertTrue(completed.transportReservationNumber().startsWith("DEMO-T-"));
        assertTrue(completed.getContent().contains("8월 26일 수요일 오전 10시 30분"));
        assertTrue(completed.getContent().contains("진료비 예상(별도): 30,000원"));
        assertTrue(completed.getContent().contains("총 결제: 60,900원"));
    }

    @Test
    void offersLaterAppointmentOrRelaxedTransportConstraintWhenPreferencesConflict() {
        var request = new HospitalVisitRequest(
            "대전",
            "서울 관악구",
            "서울대병원",
            "정형외과",
            LocalDate.of(2026, 8, 26),
            "오전",
            LocalTime.of(10, 30),
            List.of(new TransportConstraint(TransportMode.TRAIN, LocalTime.of(8, 30)))
        );
        var agent = new HospitalVisitBookingAgent(
            this::appointments, this::transportPlans, new MockHospitalReservation(() -> 1.0));

        var result = agent.planVisit(request);

        assertTrue(result.hasConflict());
        assertEquals(2, result.alternatives().size());
        assertEquals(ConflictReason.APPOINTMENT_TIME_CHANGED, result.alternatives().get(0).reason());
        assertEquals(LocalTime.of(11, 20),
            result.alternatives().get(0).plan().appointment().appointmentTime().toLocalTime());
        assertEquals(ConflictReason.TRANSPORT_CONSTRAINT_RELAXED, result.alternatives().get(1).reason());
        assertEquals(LocalTime.of(10, 30),
            result.alternatives().get(1).plan().appointment().appointmentTime().toLocalTime());
    }

    private List<HospitalAppointment> appointments(HospitalRequest request) {
        return List.of(
            new HospitalAppointment("서울대병원", "정형외과", request.date().atTime(10, 30),
                "서울", 30_000, LocalTime.of(9, 0), LocalTime.of(17, 30)),
            new HospitalAppointment("서울대병원", "정형외과", request.date().atTime(11, 20),
                "서울", 30_000, LocalTime.of(9, 0), LocalTime.of(17, 30))
        );
    }

    private List<TransportPlan> transportPlans(TransportRequest request) {
        return List.of(
            new TransportPlan(TransportMode.TRAIN, List.of(new TransportLeg(
                TransportMode.TRAIN, "KTX 123호", "대전", "서울",
                request.date().atTime(8, 10), request.date().atTime(10, 0), 60_900))),
            new TransportPlan(TransportMode.EXPRESS_BUS, List.of(new TransportLeg(
                TransportMode.EXPRESS_BUS, "우등 노선 E001", "대전", "서울",
                request.date().atTime(8, 30), request.date().atTime(10, 45), 22_000)))
        );
    }
}
