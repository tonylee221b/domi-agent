package org.example.seniorlifebookingagent.domain.visit;

public record HospitalVisitAlternative(
    HospitalVisitPlan plan,
    ConflictReason reason
) {
    public String summary() {
        return "%s. %s".formatted(
            reason.description(),
            plan.selectionSummary()
        );
    }
}
