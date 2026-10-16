package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.BillingCause;
import com.ai4cc.billing.domain.model.BillingCauseType;
import com.ai4cc.billing.domain.model.LineDelta;
import com.ai4cc.billing.domain.model.valueobject.Money;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// When the customer names one amount or one service, speak only that part of the comparison.
// A whole-bill question (no figure, no service word) stays a full explanation (BUG-031).
public final class AmountFocus {

    public enum Kind { FULL, DETAIL, UNKNOWN }

    public record Spoken(BillingCauseType type, Money impact) {
    }

    public record Result(Kind kind, List<Spoken> spoken) {

        public List<String> codes() {
            return spoken.stream().map(line -> line.type().name()).distinct().toList();
        }

        private static Result full() {
            return new Result(Kind.FULL, List.of());
        }

        private static Result unknown() {
            return new Result(Kind.UNKNOWN, List.of());
        }

        private static Result detail(List<Spoken> spoken) {
            return new Result(Kind.DETAIL, List.copyOf(spoken));
        }
    }

    private static final Pattern DECIMAL = Pattern.compile("(\\d{1,4})[,.](\\d{2})");
    private static final Pattern EUROS = Pattern.compile("(\\d{1,4}) euros?");
    private static final Set<String> STOP = Set.of(
            "facture", "facturation", "factures", "bill", "billing", "invoice", "invoices",
            "montant", "montants", "somme", "amount", "amounts", "charge", "charges",
            "ligne", "lignes", "line", "lines", "euro", "euros", "comprends", "comprend",
            "understand", "explain", "explique", "pourquoi", "comment", "combien", "quand",
            "quelle", "quelles", "quels", "which", "about", "augmente", "augmenter",
            "augmentation", "diminue", "diminuer", "diminution", "precedent", "dernier",
            "mois", "month", "compared", "rapport", "previous", "change", "changed",
            "changes", "higher", "lower", "increase", "increased", "decrease", "decreased",
            "differ", "difference", "different", "usual");

    public static boolean hasMoney(String transcript) {
        return !Mention.of(transcript).cents().isEmpty();
    }

    public static boolean mentioned(String transcript) {
        return !Mention.of(transcript).isEmpty();
    }

    public Result select(List<BillingCause> causes, Money totalDelta, String transcript) {
        Mention mention = Mention.of(transcript);
        if (mention.isEmpty()) {
            return Result.full();
        }
        List<Spoken> matched = match(causes, mention);
        if (!matched.isEmpty()) {
            return Result.detail(matched);
        }
        return mention.matches(totalDelta) ? Result.full() : Result.unknown();
    }

    private List<Spoken> match(List<BillingCause> causes, Mention mention) {
        List<Spoken> spoken = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (BillingCause cause : causes) {
            addImpacts(cause, mention, spoken, seen);
            addDeltas(cause, mention, spoken, seen);
        }
        return spoken;
    }

    private void addImpacts(BillingCause cause, Mention mention, List<Spoken> spoken, Set<String> seen) {
        for (Money impact : AddonFamilies.impacts(cause)) {
            if (mention.matches(impact) && seen.add(key(cause.type(), impact))) {
                spoken.add(new Spoken(cause.type(), impact));
            }
        }
    }

    private void addDeltas(BillingCause cause, Mention mention, List<Spoken> spoken, Set<String> seen) {
        for (LineDelta delta : cause.deltas()) {
            Money amount = amountOf(delta, mention);
            if (amount != null && seen.add(key(cause.type(), amount))) {
                spoken.add(new Spoken(cause.type(), amount));
            }
        }
    }

    private Money amountOf(LineDelta delta, Mention mention) {
        if (mention.matches(delta.contribution())) {
            return delta.contribution();
        }
        if (mention.matches(delta.currentAmount())) {
            return delta.currentAmount();
        }
        if (mention.matches(delta.previousAmount())) {
            return delta.previousAmount();
        }
        return mention.matchesLabel(delta.label()) ? delta.contribution() : null;
    }

    private static String key(BillingCauseType type, Money amount) {
        return type.name() + ":" + amount.minorUnits();
    }

    private record Mention(Set<Long> cents, Set<String> tokens) {

        private static Mention of(String transcript) {
            String normalized = normalize(transcript);
            return new Mention(cents(normalized), tokens(normalized));
        }

        private boolean isEmpty() {
            return cents.isEmpty() && tokens.isEmpty();
        }

        private boolean matches(Money amount) {
            return amount != null && cents.contains(Math.abs(amount.minorUnits()));
        }

        private boolean matchesLabel(String label) {
            String normalized = normalize(label).replace('-', ' ');
            for (String token : tokens) {
                if (normalized.contains(token)) {
                    return true;
                }
            }
            return false;
        }

        private static Set<Long> cents(String normalized) {
            Set<Long> values = new LinkedHashSet<>();
            Matcher decimals = DECIMAL.matcher(normalized);
            while (decimals.find()) {
                values.add(Long.parseLong(decimals.group(1)) * 100 + Long.parseLong(decimals.group(2)));
            }
            Matcher euros = EUROS.matcher(normalized);
            while (euros.find()) {
                values.add(Long.parseLong(euros.group(1)) * 100);
            }
            return values;
        }

        private static Set<String> tokens(String normalized) {
            Set<String> words = new LinkedHashSet<>();
            for (String token : normalized.split(" ")) {
                if (token.length() >= 5 && !STOP.contains(token) && !token.chars().allMatch(Character::isDigit)) {
                    words.add(token);
                }
            }
            return words;
        }

        private static String normalize(String text) {
            if (text == null) {
                return "";
            }
            String folded = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "");
            return folded.replaceAll("[^a-z0-9.,]+", " ").replace('-', ' ').strip();
        }
    }
}
