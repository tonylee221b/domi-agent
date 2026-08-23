package org.example.seniorlifebookingagent.domain.hospital;

import java.time.LocalDate;

public record HospitalRequest(
    String region,
    String hospitalName,
    String department,
    LocalDate date,
    String preferredTime
) {
}
