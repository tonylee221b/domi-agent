package org.example.seniorlifebookingagent.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.example.seniorlifebookingagent.domain.hospital.ApprovedHospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalRequest;
import org.junit.jupiter.api.Test;

class HospitalAppointmentAgentTest {

    @Test
    void createsReadableMockReservationNumber() {
        var request = new HospitalRequest(
            "서울",
            "서울대병원",
            "정형외과",
            LocalDate.of(2026, 8, 26),
            "오전"
        );
        var tool = (org.example.seniorlifebookingagent.tool.HospitalTool) ignored -> List.of(new HospitalAppointment(
            "서울대병원", "정형외과", request.date().atTime(10, 30), "서울", 30_000,
            LocalTime.of(9, 0), LocalTime.of(17, 30)
        ));
        var agent = new HospitalAppointmentAgent(tool, new MockHospitalReservation(() -> 1.0));
        var appointment = agent.searchHospital(request);

        assertTrue(agent.reserve(new ApprovedHospitalAppointment(appointment)).reservationNumber()
                        .matches("DEMO-H-\\d{8}-\\d{4}"));
        assertThrows(IllegalStateException.class, () ->
            new HospitalAppointmentAgent(tool, new MockHospitalReservation(() -> 0.0))
                .reserve(new ApprovedHospitalAppointment(appointment)));
    }
}
