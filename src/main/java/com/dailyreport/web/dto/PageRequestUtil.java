package com.dailyreport.web.dto;

public final class PageRequestUtil {
    private PageRequestUtil() {}

    public static int clampPage(int page) {
        return Math.max(1, page);
    }

    public static int clampSize(int size) {
        return Math.min(100, Math.max(1, size));
    }

    public static int offset(int page, int size) {
        return (clampPage(page) - 1) * clampSize(size);
    }
}
