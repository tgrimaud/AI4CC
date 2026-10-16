package com.ai4cc.conversation.domain.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// Any invoice request that does not name an amount or a service is a clarification, not a
// recital (BUG-031). The first question asks which amount or service; the next one asks for
// the figure or the service name. A figure or a leftover service word is concrete enough to
// explain that part only.
public final class AmountClarify {

    public static final String QUESTION_FR =
            "Quel montant, ou quel service, de votre facture voulez-vous que je vous explique ?";
    public static final String QUESTION_EN =
            "Which amount, or which service, on your bill would you like me to explain?";
    public static final String FOLLOW_UP_FR =
            "Pouvez-vous me donner le montant, ou le nom du service concerné ?";
    public static final String FOLLOW_UP_EN =
            "Could you give me the amount, or the name of the service?";

    private static final Pattern MONEY = Pattern.compile("\\b\\d{1,4}[,.]\\d{2}\\b|\\b\\d{1,4}\\s+euros?\\b");
    private static final Set<String> STOP = Set.of(
            "je", "ne", "pas", "un", "une", "le", "la", "les", "de", "du", "des", "sur", "ma", "mon", "mes",
            "me", "moi", "que", "qui", "au", "aux", "en", "et", "ou", "a", "dans", "pour", "avec", "ce",
            "votre", "vos", "dont", "not", "the", "my", "facture", "facturation", "factures", "bill",
            "billing", "invoice", "invoices", "montant", "montants", "somme", "amount", "amounts",
            "charge", "charges", "ligne", "lignes", "line", "lines", "euro", "euros", "comprends",
            "comprend", "comprendre", "compris", "understand", "understood", "explain", "explique",
            "expliquer", "expliquez", "pourquoi", "comment", "combien", "quand", "quelle", "quelles",
            "quels", "which", "about", "augmente", "augmenter", "augmentation", "diminue", "diminuer",
            "change", "changee", "elevee", "eleve", "precedent", "precedente", "dernier", "derniere",
            "mois", "month", "compared", "rapport", "previous", "habitude", "probleme", "problemes",
            "souci", "soucis");

    private AmountClarify() {
    }

    public static boolean isQuestion(String assistantText) {
        return QUESTION_FR.equals(assistantText) || QUESTION_EN.equals(assistantText)
                || FOLLOW_UP_FR.equals(assistantText) || FOLLOW_UP_EN.equals(assistantText);
    }

    public static String question(String forcedLanguage, String transcript, int alreadyAsked) {
        boolean fr = french(forcedLanguage, transcript);
        if (alreadyAsked > 0) {
            return fr ? FOLLOW_UP_FR : FOLLOW_UP_EN;
        }
        return fr ? QUESTION_FR : QUESTION_EN;
    }

    public static boolean hasConcreteFocus(String transcript) {
        String normalized = normalize(transcript);
        if (MONEY.matcher(normalized).find()) {
            return true;
        }
        for (String token : normalized.split(" ")) {
            if (token.length() >= 5 && !STOP.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean french(String forcedLanguage, String transcript) {
        if (forcedLanguage != null && forcedLanguage.toLowerCase(Locale.ROOT).startsWith("en")) {
            return false;
        }
        if (forcedLanguage != null && forcedLanguage.toLowerCase(Locale.ROOT).startsWith("fr")) {
            return true;
        }
        String normalized = normalize(transcript);
        return normalized.contains("facture") || normalized.contains("montant") || normalized.contains("comprend");
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String folded = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return folded.replaceAll("[^a-z0-9.,]+", " ").strip();
    }
}
