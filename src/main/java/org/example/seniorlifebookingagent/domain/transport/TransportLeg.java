package org.example.seniorlifebookingagent.domain.transport;

import java.time.LocalDateTime;
import java.util.Objects;

public record TransportLeg(
    TransportMode mode,
    String serviceInfo,
    String origin,
    String destination,
    LocalDateTime departAt,
    LocalDateTime arriveAt,
    int price
) {
    public TransportLeg {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(serviceInfo);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(departAt);
        Objects.requireNonNull(arriveAt);
        if (arriveAt.isBefore(departAt) || price < 0) {
            throw new IllegalArgumentException("교통 구간의 시간과 금액은 유효해야 합니다.");
        }
    }
}
