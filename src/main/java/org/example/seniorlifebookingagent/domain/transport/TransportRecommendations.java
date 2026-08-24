package org.example.seniorlifebookingagent.domain.transport;

import java.util.List;

public record TransportRecommendations(List<Recommendation> recommendations) {
    public TransportRecommendations {
        recommendations = List.copyOf(recommendations);
        if (recommendations.isEmpty()) {
            throw new IllegalArgumentException("추천할 교통편이 없습니다.");
        }
    }

    public record Recommendation(TransportPlan plan, String reason) {
    }
}
