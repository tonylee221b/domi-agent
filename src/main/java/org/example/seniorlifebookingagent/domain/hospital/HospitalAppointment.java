package org.example.seniorlifebookingagent.domain.hospital;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record HospitalAppointment(
    String hospitalName,
    String department,
    LocalDateTime appointmentTime,
    String address,
    int fee,
    LocalTime opensAt,
    LocalTime closesAt
) {
    public String operatingHours() {
        return opensAt == null || closesAt == null
            ? "운영시간 정보 없음"
            : "%s ~ %s".formatted(opensAt, closesAt);
    }
}
