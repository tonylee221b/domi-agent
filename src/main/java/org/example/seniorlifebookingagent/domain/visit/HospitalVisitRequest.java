package org.example.seniorlifebookingagent.domain.visit;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record HospitalVisitRequest(
    String origin,
    String region,
    String hospitalName,
    String department,
    LocalDate date,
    String preferredTime,
    LocalTime requestedAppointmentTime,
    List<TransportConstraint> transportConstraints
) {
    public HospitalVisitRequest {
        transportConstraints = transportConstraints == null ? List.of() : List.copyOf(transportConstraints);
    }
}
