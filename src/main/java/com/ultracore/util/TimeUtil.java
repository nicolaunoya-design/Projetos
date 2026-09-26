package com.ultracore.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private TimeUtil() {
    }

    /** Converte "1d2h30m" em milissegundos. Retorna -1 se inválido. */
    public static long parse(String input) {
        if (input == null || !input.matches("(?i)(\\d+[smhdw])+")) return -1;
        Matcher m = PART.matcher(input);
        long total = 0;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            total += switch (Character.toLowerCase(m.group(2).charAt(0))) {
                case 's' -> n * 1000L;
                case 'm' -> n * 60_000L;
                case 'h' -> n * 3_600_000L;
                case 'd' -> n * 86_400_000L;
                case 'w' -> n * 604_800_000L;
                default -> 0;
            };
        }
        return total;
    }

    /** Formata milissegundos como "1d 2h 30m 5s". */
    public static String format(long millis) {
        long s = Math.max(0, millis / 1000);
        long d = s / 86400;
        s %= 86400;
        long h = s / 3600;
        s %= 3600;
        long m = s / 60;
        s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.isEmpty()) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
