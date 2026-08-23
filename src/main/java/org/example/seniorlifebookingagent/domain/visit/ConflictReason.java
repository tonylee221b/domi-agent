package org.example.seniorlifebookingagent.domain.visit;

public enum ConflictReason {
    APPOINTMENT_TIME_CHANGED("진료 시간을 바꾼 일정입니다"),
    TRANSPORT_CONSTRAINT_RELAXED("원하신 시간보다 일찍 출발하는 일정입니다");

    private final String description;

    ConflictReason(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
