package org.example.seniorlifebookingagent.domain.visit;

public record AlternativeSelection(int optionNumber) {
    public AlternativeSelection {
        if (optionNumber < 1) {
            throw new IllegalArgumentException("대안 번호는 1 이상이어야 합니다.");
        }
    }
}
