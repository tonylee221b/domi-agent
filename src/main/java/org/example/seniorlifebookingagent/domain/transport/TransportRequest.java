package org.example.seniorlifebookingagent.domain.transport;

import java.time.LocalDate;

public record TransportRequest(
    String origin,
    String destination,
    LocalDate date,
    String preferredDepartureTime,
    String preferredArrivalTime
) {
}
