package org.example.seniorlifebookingagent.agent;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import org.springframework.stereotype.Component;

@Component
class MockHospitalReservation {

    private static final double FAILURE_RATE = 0.3;
    private final DoubleSupplier random;

    MockHospitalReservation() {
        this(() -> ThreadLocalRandom.current().nextDouble());
    }

    MockHospitalReservation(DoubleSupplier random) {
        this.random = random;
    }

    void reserve() {
        if (random.getAsDouble() < FAILURE_RATE) {
            throw new IllegalStateException("선택한 병원 예약 시간이 방금 마감되었습니다. 다른 시간을 선택해 주세요.");
        }
    }
}
