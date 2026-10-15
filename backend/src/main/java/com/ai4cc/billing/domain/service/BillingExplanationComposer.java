package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.BillingCause;
import com.ai4cc.billing.domain.model.BillingCauseType;
import com.ai4cc.billing.domain.model.ExplanationConfidence;
import com.ai4cc.billing.domain.model.ExplanationReadiness;
import com.ai4cc.billing.domain.model.InvoiceComparison;
import com.ai4cc.billing.domain.model.valueobject.Money;

import java.math.BigDecimal;
import java.util.Locale;

// Turns a deterministic comparison + confidence verdict into grounded, language-aware explanation
// text (TASK-BE-045, ADR-0052 D1a). Every currency amount it voices comes from the computed comparison
// (delta, per-cause impact, residual), so the downstream OutputGuardrail (DEC-002) passes: the LLM may
// only rephrase this text and can never introduce an amount that is not here. Also owns the safe
// operational / hand-off messages for the degraded branches, kept language-consistent with answers.
public class BillingExplanationComposer {

    public String compose(InvoiceComparison comparison, ExplanationReadiness readiness, String languageCode) {
        boolean fr = isFrench(languageCode);
        StringBuilder text = new StringBuilder(openingLine(comparison.totalDelta(), fr));
        for (BillingCause cause : comparison.causes()) {
            for (Money impact : AddonFamilies.impacts(cause)) {
                text.append(' ').append(phrase(cause.type(), impact, fr));
            }
        }
        if (readiness.confidence() == ExplanationConfidence.PARTIAL) {
            text.append(' ').append(residualLine(readiness.unexplainedAmount(), fr));
        }
        return text.toString().strip();
    }

    public String askReference(String languageCode) {
        return isFrench(languageCode)
                ? "Pour analyser votre facture, pouvez-vous me communiquer votre référence client ?"
                : "To review your bill, could you give me your customer reference?";
    }

    public String cannotVerify(String languageCode) {
        return isFrench(languageCode)
                ? "Je ne parviens pas à vérifier votre identité, je vous transfère à un conseiller."
                : "I can't verify your identity, I'll transfer you to an advisor.";
    }

    public String notEnoughData(String languageCode) {
        return isFrench(languageCode)
                ? "Je n'ai pas assez d'éléments pour comparer vos factures, je vous transfère à un conseiller."
                : "I don't have enough information to compare your bills, I'll transfer you to an advisor.";
    }

    public String notABillingRequest(String languageCode) {
        return isFrench(languageCode)
                ? "Je peux vous aider à comprendre votre facture. Sur quelle facture porte votre question ?"
                : "I can help you understand your bill. Which bill is your question about?";
    }

    private String openingLine(Money delta, boolean fr) {
        if (delta.isZero()) {
            return fr ? "Votre facture est identique au mois précédent."
                    : "Your bill is unchanged from last month.";
        }
        boolean increased = !delta.isNegative();
        if (fr) {
            return "Votre facture a " + (increased ? "augmenté" : "diminué") + " de " + money(delta.abs())
                    + " par rapport au mois précédent.";
        }
        return "Your bill " + (increased ? "increased" : "decreased") + " by " + money(delta.abs())
                + " compared with last month.";
    }

    private String phrase(BillingCauseType type, Money impact, boolean fr) {
        String label = label(type, fr);
        if (fr) {
            return "En cause : " + label + " pour " + signed(impact) + ".";
        }
        return "This is due to " + label + " (" + signed(impact) + ").";
    }

    private String residualLine(Money residual, boolean fr) {
        if (fr) {
            return "Il reste " + money(residual.abs()) + " que je ne peux pas détailler précisément.";
        }
        return "A remaining " + money(residual.abs()) + " could not be fully detailed.";
    }

    private String signed(Money amount) {
        return (amount.isNegative() ? "-" : "+") + money(amount.abs());
    }

    private String money(Money amount) {
        BigDecimal value = BigDecimal.valueOf(amount.minorUnits(), 2);
        return value.toPlainString() + " " + symbol(amount);
    }

    private String symbol(Money amount) {
        return switch (amount.currency().getCurrencyCode()) {
            case "EUR" -> "€";
            case "USD" -> "$";
            case "GBP" -> "£";
            default -> amount.currency().getCurrencyCode();
        };
    }

    private String label(BillingCauseType type, boolean fr) {
        return switch (type) {
            case PLAN_CHANGE -> fr ? "un changement d'offre" : "a plan change";
            case TARIFF_INCREASE -> fr ? "une hausse de tarif" : "a tariff increase";
            case SPEED_UPGRADE -> fr ? "un changement de débit" : "a speed change";
            case ADDON_ADDED -> fr ? "l'ajout d'un nouveau service" : "a new service added";
            case ADDON_REMOVED -> fr ? "le retrait d'un service" : "a service removed";
            case DISCOUNT_ENDED -> fr ? "la fin d'une remise" : "the end of a discount";
            case DISCOUNT_ADDED -> fr ? "le début d'une remise" : "the start of a discount";
            case DEVICE_INSTALLMENT_ENDED -> fr ? "la fin des mensualités d'un appareil" : "the end of a device installment";
            case LATE_PAYMENT_FEE -> fr ? "des frais de retard" : "a late-payment fee";
            case CHANGE_CYCLE -> fr ? "un changement de cycle de facturation" : "a billing-cycle change";
            case USAGE_OVERAGE -> fr ? "un dépassement de consommation" : "usage over your plan";
            case TAX_CHANGE -> fr ? "une variation de taxe" : "a tax change";
            case BILLING_ADJUSTMENT -> fr ? "un ajustement" : "an adjustment";
            case ACTIVATION_FEE -> fr ? "des frais d'activation" : "an activation fee";
            case ONE_OFF_BUNDLE -> fr ? "un bundle ponctuel" : "a one-off bundle";
            case UNEXPLAINED -> fr ? "une part non expliquée" : "an unexplained part";
        };
    }

    private boolean isFrench(String languageCode) {
        return languageCode != null && languageCode.strip().toLowerCase(Locale.ROOT).startsWith("fr");
    }
}
