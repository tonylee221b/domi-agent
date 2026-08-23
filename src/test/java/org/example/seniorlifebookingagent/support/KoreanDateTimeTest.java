package org.example.seniorlifebookingagent.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class KoreanDateTimeTest {

    @Test
    void resolvesKoreanRelativeDates() {
        var today = KoreanDateTime.today();

        assertEquals(today, KoreanDateTime.resolveRelativeDate("오늘 오전", LocalDate.MIN));
        assertEquals(today.plusDays(1), KoreanDateTime.resolveRelativeDate("내일 오후", LocalDate.MIN));
        assertEquals(today.plusDays(2), KoreanDateTime.resolveRelativeDate("모레 저녁", LocalDate.MIN));
    }
}
