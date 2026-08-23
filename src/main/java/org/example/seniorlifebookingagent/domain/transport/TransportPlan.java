package org.example.seniorlifebookingagent.domain.transport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.example.seniorlifebookingagent.support.KoreanDateTime;

public record TransportPlan(TransportMode primaryMode, List<TransportLeg> legs) {
    public TransportPlan {
        legs = List.copyOf(legs);
        if (legs.isEmpty() || legs.stream().noneMatch(leg -> leg.mode() == primaryMode)) {
            throw new IllegalArgumentException("대표 교통수단은 이동 경로에 포함되어야 합니다.");
        }
    }

    public LocalDateTime departAt() {
        return legs.getFirst().departAt();
    }

    public LocalDateTime arriveAt() {
        return legs.getLast().arriveAt();
    }

    public int totalPrice() {
        return legs.stream().mapToInt(TransportLeg::price).sum();
    }

    public String serviceInfo() {
        return legs.stream().filter(leg -> leg.mode() == primaryMode).findFirst().orElseThrow().serviceInfo();
    }

    public String choiceSummary() {
        return "%s, %s → %s, %s 출발, %s 도착, %,d원".formatted(
            primaryMode.displayName(),
            legs.getFirst().origin(),
            legs.getLast().destination(),
            KoreanDateTime.formatTime(departAt()),
            KoreanDateTime.formatTime(arriveAt()),
            totalPrice()
        );
    }

    public String summary() {
        return legs.stream()
                   .map(leg -> "%s %s: %s에서 %s까지\n  %s 출발, %s 도착\n  요금: %,d원".formatted(
                       leg.mode().displayName(),
                       leg.serviceInfo(),
                       leg.origin(),
                       leg.destination(),
                       KoreanDateTime.format(leg.departAt()),
                       KoreanDateTime.format(leg.arriveAt()),
                       leg.price()
                   ))
                   .collect(Collectors.joining("\n"));
    }
}
