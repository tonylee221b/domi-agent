package org.example.seniorlifebookingagent.domain.visit;

import java.util.List;

public record HospitalVisitPlanningResult(
    List<HospitalVisitPlan> matchingPlans,
    List<HospitalVisitAlternative> alternatives
) {
    public HospitalVisitPlanningResult {
        matchingPlans = List.copyOf(matchingPlans);
        alternatives = List.copyOf(alternatives);
    }

    public boolean hasConflict() {
        return matchingPlans.isEmpty();
    }
}
