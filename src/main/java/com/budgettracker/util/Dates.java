package com.budgettracker.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

public final class Dates {

    private Dates() {}

    public static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public static java.sql.Date toSqlDate(LocalDate date) {
        return date == null ? null : java.sql.Date.valueOf(date);
    }

    public static LocalDate toLocalDate(java.sql.Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
