package com.budgettracker.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public final class MoneyFormat {

    private MoneyFormat() {}

    /** Returns a display string like "PHP 1,234.50" */
    public static String format(BigDecimal amount, String currencyCode) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.US);
        nf.setCurrency(Currency.getInstance(currencyCode));
        return nf.format(amount.setScale(2, RoundingMode.HALF_EVEN));
    }

    /** Parses a plain numeric string (e.g. "1234.50") to BigDecimal. */
    public static BigDecimal parse(String text) {
        return new BigDecimal(text.trim());
    }
}
