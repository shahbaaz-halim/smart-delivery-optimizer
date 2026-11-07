package com.logismart.algorithm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Delivery window in minutes from midnight. "Flexible" windows are
 * represented with {@link #flexible()} and are resolved against the
 * vehicle's shift at scheduling time.
 */
public record TimeWindow(int startMin, int endMin, boolean isFlexible) {

    private static final Pattern RANGE =
            Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})\\s*$");

    public static TimeWindow flexible() {
        return new TimeWindow(0, 24 * 60, true);
    }

    /**
     * Parses "HH:MM-HH:MM" or "Flexible" (case-insensitive). Blank values are
     * treated as Flexible. Returns {@code null} for any malformed value or a
     * window whose end is not after its start.
     */
    public static TimeWindow parse(String value) {

        if (value == null || value.isBlank()
                || value.trim().equalsIgnoreCase("flexible")) {
            return flexible();
        }

        Matcher m = RANGE.matcher(value);

        if (!m.matches()) {
            return null;
        }

        int sh = Integer.parseInt(m.group(1));
        int sm = Integer.parseInt(m.group(2));
        int eh = Integer.parseInt(m.group(3));
        int em = Integer.parseInt(m.group(4));

        if (sh > 23 || eh > 24 || sm > 59 || em > 59) {
            return null;
        }

        int start = sh * 60 + sm;
        int end = eh * 60 + em;

        if (end <= start) {
            return null;
        }

        return new TimeWindow(start, end, false);
    }

    public static String format(double minutesFromMidnight) {
        int total = (int) Math.round(minutesFromMidnight);
        return String.format("%02d:%02d", total / 60, total % 60);
    }
}
