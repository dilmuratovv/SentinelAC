package uz.sentinel.ac.diagnostics;

import java.util.UUID;
import java.util.regex.Pattern;

public record Finding(long timeMillis, UUID playerId, String playerName, String check, String detail, boolean core) {
    private static final Pattern COLORS = Pattern.compile("(?i)(?:§.|&(?:#[0-9a-f]{6}|[0-9a-fk-orx]))");

    public Finding {
        playerName = clean(playerName, 32);
        check = clean(check, 64);
        detail = clean(detail, 512);
    }

    public static String clean(String text, int max) {
        if (text == null) return "";
        String value = COLORS.matcher(text.substring(0, Math.min(text.length(), max * 4))).replaceAll("")
                .replaceAll("[\\p{Cntrl}\\p{Cf}\\u2028\\u2029]", " ");
        return value.substring(0, Math.min(value.length(), max));
    }
}
