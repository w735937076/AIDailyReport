package com.dailyreport.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeekRangeUtilTest {

    @Test
    void fridayMapsToMondayThroughSundayOfSameWeek() {
        LocalDate friday = LocalDate.of(2026, 11, 13);
        assertEquals(LocalDate.of(2026, 11, 9), WeekRangeUtil.mondayOfWeek(friday));
        assertEquals(LocalDate.of(2026, 11, 15), WeekRangeUtil.sundayOfWeek(friday));
    }

    @Test
    void mondayStaysSameWeekStart() {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        assertEquals(monday, WeekRangeUtil.mondayOfWeek(monday));
        assertEquals(LocalDate.of(2026, 11, 15), WeekRangeUtil.sundayOfWeek(monday));
    }

    @Test
    void sundayMapsToThatWeeksMonday() {
        LocalDate sunday = LocalDate.of(2026, 11, 15);
        assertEquals(LocalDate.of(2026, 11, 9), WeekRangeUtil.mondayOfWeek(sunday));
    }

    @Test
    void currentWeekMondayMatchesPreviousOrSameMondayInShanghai() {
        LocalDate today = WeekRangeUtil.todayInShanghai();
        LocalDate expected = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertEquals(expected, WeekRangeUtil.currentWeekMondayInShanghai());
    }
}
