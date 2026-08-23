package org.example.seniorlifebookingagent.domain.transport;

import java.util.List;

public record TransportOptions(List<TransportPlan> plans) {
    public TransportOptions {
        plans = List.copyOf(plans);
        if (plans.isEmpty()) {
            throw new IllegalArgumentException("지금 이용할 수 있는 교통편이 없습니다.");
        }
    }
}
