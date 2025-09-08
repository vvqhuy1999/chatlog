package com.example.chatlog.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.regex.*;

public class DateParser {
    private static final Pattern DATE_RANGE_PATTERN =
            Pattern.compile("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}).*?(\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})");

    private static final DateTimeFormatter INPUT_FORMATTER1 =
            DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final DateTimeFormatter INPUT_FORMATTER2 =
            DateTimeFormatter.ofPattern("d-M-yyyy");

    private static final DateTimeFormatter OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    public static String[] extractDateRange(String message) {
        Matcher matcher = DATE_RANGE_PATTERN.matcher(message);
        if (matcher.find()) {
            String from = matcher.group(1);
            String to = matcher.group(2);

            LocalDate startDate = parseDate(from);
            LocalDate endDate = parseDate(to);

            // Chuyển sang ZonedDateTime (múi giờ +07:00)
            ZoneOffset offset = ZoneOffset.ofHours(7);
            OffsetDateTime startDateTime = startDate.atStartOfDay().atOffset(offset);
            OffsetDateTime endDateTime = endDate.atTime(LocalTime.MAX).atOffset(offset);


            String gte = startDateTime.format(OUTPUT_FORMATTER);
            String lte = endDateTime.format(OUTPUT_FORMATTER);

            return new String[]{gte, lte};
        }
        return null;
    }

    private static LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, INPUT_FORMATTER1);
        } catch (Exception e) {
            return LocalDate.parse(dateStr, INPUT_FORMATTER2);
        }
    }
}
