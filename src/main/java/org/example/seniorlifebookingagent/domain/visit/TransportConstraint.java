package org.example.seniorlifebookingagent.domain.visit;

import java.time.LocalTime;
import java.util.Objects;
import org.example.seniorlifebookingagent.domain.transport.TransportMode;
import org.example.seniorlifebookingagent.domain.transport.TransportPlan;

public record TransportConstraint(
    TransportMode mode,
    LocalTime notBefore
) {
    public TransportConstraint {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(notBefore);
    }

    public boolean allows(TransportPlan plan) {
        return plan.legs().stream()
                   .filter(leg -> leg.mode() == mode)
                   .allMatch(leg -> !leg.departAt().toLocalTime().isBefore(notBefore));
    }
}
