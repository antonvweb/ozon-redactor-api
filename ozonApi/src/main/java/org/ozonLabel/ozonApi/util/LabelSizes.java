package org.ozonLabel.ozonApi.util;

import java.math.BigDecimal;

/**
 * Предустановленные размеры этикеток.
 */
public final class LabelSizes {

    public static final BigDecimal WIDTH_58 = new BigDecimal("58");
    public static final BigDecimal HEIGHT_40 = new BigDecimal("40");

    public static final BigDecimal WIDTH_43 = new BigDecimal("43");
    public static final BigDecimal HEIGHT_25 = new BigDecimal("25");

    public static final BigDecimal WIDTH_75 = new BigDecimal("75");
    public static final BigDecimal HEIGHT_120 = new BigDecimal("120");

    private LabelSizes() {
        // Utility class
    }
}
