package org.example.seniorlifebookingagent.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MockHospitalReservationTest {

    @Test
    void succeedsSeventyPercentOfRandomRange() {
        assertThrows(IllegalStateException.class, () -> new MockHospitalReservation(() -> 0.299).reserve());
        assertDoesNotThrow(() -> new MockHospitalReservation(() -> 0.3).reserve());
    }
}
