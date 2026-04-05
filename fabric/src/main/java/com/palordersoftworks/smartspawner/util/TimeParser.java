package com.palordersoftworks.smartspawner.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParser {

    private static final Pattern SIMPLE = Pattern.compile("^([0-9]+)([smhdw])$", Pattern.CASE_INSENSITIVE);

    private TimeParser() {
    }

    /**
     * Parses Paper-style duration strings (e.g. 25s, 5m) into game ticks (20 tps).
     */
    public static long parseTicks(String raw, long defaultTicks) {
        if (raw == null || raw.isBlank()) {
            return defaultTicks;
        }
        String s = raw.trim();
        Matcher m = SIMPLE.matcher(s);
        if (m.matches()) {
            long n = Long.parseLong(m.group(1));
            char u = Character.toLowerCase(m.group(2).charAt(0));
            long seconds = switch (u) {
                case 's' -> n;
                case 'm' -> n * 60L;
                case 'h' -> n * 3600L;
                case 'd' -> n * 86400L;
                case 'w' -> n * 604800L;
                default -> n;
            };
            return Math.max(1, seconds * 20L);
        }
        try {
            long asLong = Long.parseLong(s);
            return Math.max(1, asLong);
        } catch (NumberFormatException e) {
            return defaultTicks;
        }
    }
}
