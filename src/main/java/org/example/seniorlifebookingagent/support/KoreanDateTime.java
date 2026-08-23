package org.example.seniorlifebookingagent.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class KoreanDateTime {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("a h시", Locale.KOREAN);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private KoreanDateTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(KOREA);
    }

    public static LocalDate resolveRelativeDate(String text, LocalDate parsedDate) {
        if (text.contains("오늘")) return today();
        if (text.contains("내일")) return today().plusDays(1);
        if (text.contains("모레")) return today().plusDays(2);
        return parsedDate;
    }

    public static String format(LocalDateTime dateTime) {
        return "%s %s".formatted(formatDate(dateTime), formatTime(dateTime));
    }

    public static String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DATE);
    }

    public static String formatTime(LocalDateTime dateTime) {
        var minute = dateTime.getMinute() == 0 ? "" : " %d분".formatted(dateTime.getMinute());
        return "%s%s".formatted(dateTime.format(TIME), minute);
    }
}
