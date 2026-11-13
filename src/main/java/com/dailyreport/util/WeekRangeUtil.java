package com.dailyreport.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Weekly reports use Monday–Sunday (ISO week), with “today” resolved in Asia/Shanghai. */
public final class WeekRangeUtil {

    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private WeekRangeUtil() {
    }

    public static LocalDate todayInShanghai() {
        return LocalDate.now(SHANGHAI);
    }

    /** Any calendar day in the week → that week's Monday. */
    public static LocalDate mondayOfWeek(LocalDate anyDayInWeek) {
        if (anyDayInWeek == null) {
            throw new IllegalArgumentException("date is required");
        }
        return anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate sundayOfWeek(LocalDate anyDayInWeek) {
        return mondayOfWeek(anyDayInWeek).plusDays(6);
    }

    public static LocalDate currentWeekMondayInShanghai() {
        return mondayOfWeek(todayInShanghai());
    }
}
