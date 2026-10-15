package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.InvoiceItem;
import com.ai4cc.billing.domain.model.valueobject.Money;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Label reading for cause attribution (ADR-0057): descriptions, speed tokens, activation wording.
final class InvoiceLineText {

    private static final Pattern SPEED = Pattern.compile(
            "(\\d+)\\s*(gbps|mbps|mbit|megas|mega|mb|gb)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WINDOW = Pattern.compile(" from (.+?) until (.+)$");

    private InvoiceLineText() {
    }

    static String describe(InvoiceItem item) {
        if (item == null) {
            return "";
        }
        if (present(item.evidence().text())) {
            return item.evidence().text();
        }
        if (present(item.type())) {
            return item.type();
        }
        return item.code() == null ? "" : item.code();
    }

    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    static List<String> normalizeAll(List<String> labels) {
        if (labels == null) {
            return List.of();
        }
        return labels.stream().map(label -> label.toLowerCase(Locale.ROOT).strip())
                .filter(label -> !label.isEmpty()).toList();
    }

    static boolean containsAny(List<String> labels, String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return labels.stream().anyMatch(normalized::contains);
    }

    static boolean activation(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("activation") || normalized.contains("installation");
    }

    static boolean oneOffBundle(String text) {
        return text.toLowerCase(Locale.ROOT).contains("bundle");
    }

    static boolean belongsTo(String prorataText, List<String> addedLabels) {
        String prorata = prorataText.toLowerCase(Locale.ROOT);
        return addedLabels.stream().anyMatch(prorata::contains);
    }

    // "08 Sep" and "08 Sep 26" are the same short window. A year on only one side still matches.
    static boolean sameShortWindow(String left, String right) {
        String first = windowKey(left);
        String second = windowKey(right);
        return first != null && first.equals(second);
    }

    static boolean speedUpgrade(String before, String after, Money contribution) {
        if (contribution.isNegative() || contribution.isZero()) {
            return false;
        }
        if (!homeInternet(before) && !homeInternet(after)) {
            return false;
        }
        Long left = speedKbps(before);
        Long right = speedKbps(after);
        return left != null && right != null && !left.equals(right);
    }

    private static boolean homeInternet(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("broadband") || normalized.contains("fibre")
                || normalized.contains("fiber") || normalized.contains("internet");
    }

    private static Long speedKbps(String text) {
        Matcher matcher = SPEED.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        long value = Long.parseLong(matcher.group(1));
        long factor = matcher.group(2).toLowerCase(Locale.ROOT).startsWith("g") ? 1_000_000L : 1_000L;
        return value * factor;
    }

    private static String windowKey(String text) {
        Matcher matcher = WINDOW.matcher(normalize(text));
        if (!matcher.find()) {
            return null;
        }
        return dayMonth(matcher.group(1)) + "|" + dayMonth(matcher.group(2));
    }

    private static String dayMonth(String token) {
        return token.replaceAll("\\s+\\d{2,4}$", "").strip();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
