package org.example.seniorlifebookingagent.domain.transport;

public record TransportSelection(int optionNumber) {
    public TransportSelection {
        if (optionNumber < 1) {
            throw new IllegalArgumentException("교통편 번호는 1 이상이어야 합니다.");
        }
    }
}
