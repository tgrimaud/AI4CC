package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.BillingCause;
import com.ai4cc.billing.domain.model.BillingCauseType;
import com.ai4cc.billing.domain.model.ChangeKind;
import com.ai4cc.billing.domain.model.LineDelta;
import com.ai4cc.billing.domain.model.valueobject.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AmountFocusTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private final AmountFocus focus = new AmountFocus();

    @Test
    void a_named_amount_selects_only_that_cause() {
        // GIVEN a one-off bundle of 29.99 € beside another cause
        BillingCause bundle = cause(BillingCauseType.ONE_OFF_BUNDLE, 2999, "1gb-usa-roaming-bundle");
        BillingCause addon = cause(BillingCauseType.ADDON_ADDED, 2548, "15gb-bundle");

        // WHEN the customer names 29,99
        AmountFocus.Result result = focus.select(List.of(bundle, addon), euros(5547), "le 29,99");

        // THEN only the one-off bundle is spoken
        assertThat(result.kind()).isEqualTo(AmountFocus.Kind.DETAIL);
        assertThat(result.spoken()).hasSize(1);
        assertThat(result.spoken().get(0).type()).isEqualTo(BillingCauseType.ONE_OFF_BUNDLE);
        assertThat(result.spoken().get(0).impact().minorUnits()).isEqualTo(2999);
    }

    @Test
    void a_service_word_selects_the_matching_line() {
        // GIVEN a roaming bundle line
        BillingCause bundle = cause(BillingCauseType.ONE_OFF_BUNDLE, 2999, "1gb-usa-roaming-bundle");

        // WHEN the customer says the bundle
        AmountFocus.Result result = focus.select(List.of(bundle), euros(2999), "le bundle");

        // THEN that line is the one to explain
        assertThat(result.kind()).isEqualTo(AmountFocus.Kind.DETAIL);
        assertThat(result.codes()).containsExactly("ONE_OFF_BUNDLE");
    }

    @Test
    void a_whole_bill_question_stays_a_full_explanation() {
        // GIVEN a bill difference the customer did not point at
        BillingCause bundle = cause(BillingCauseType.ONE_OFF_BUNDLE, 2999, "1gb-usa-roaming-bundle");

        // WHEN they ask why the bill changed, without naming an amount
        AmountFocus.Result result = focus.select(
                List.of(bundle), euros(2999), "pourquoi ma facture a augmenté");

        // THEN the full explanation is kept
        assertThat(result.kind()).isEqualTo(AmountFocus.Kind.FULL);
    }

    @Test
    void an_english_whole_bill_question_stays_a_full_explanation() {
        // GIVEN a bill difference
        BillingCause bundle = cause(BillingCauseType.ONE_OFF_BUNDLE, 2999, "1gb-usa-roaming-bundle");

        // WHEN the customer asks in English why the bill changed, without naming an amount
        AmountFocus.Result result = focus.select(
                List.of(bundle), euros(2999), "Why did my bill change this month?");

        // THEN the full explanation is kept
        assertThat(result.kind()).isEqualTo(AmountFocus.Kind.FULL);
    }

    @Test
    void a_higher_than_usual_bill_is_not_an_unknown_amount() {
        // GIVEN a bill difference
        BillingCause bundle = cause(BillingCauseType.ONE_OFF_BUNDLE, 2999, "1gb-usa-roaming-bundle");

        // WHEN the customer says the bill is higher than usual, without naming a line
        AmountFocus.Result result = focus.select(List.of(bundle), euros(2999), "my bill is higher than usual");

        // THEN it is not treated as a missing amount
        assertThat(result.kind()).isEqualTo(AmountFocus.Kind.FULL);
    }

    @Test
    void an_unrecognised_charge_is_not_an_unknown_amount() {
        // GIVEN a bill difference
        BillingCause bundle = cause(BillingCauseType.ONE_OFF_BUNDLE, 2999, "1gb-usa-roaming-bundle");

        // WHEN the customer does not recognise a charge, without naming it
        AmountFocus.Result result = focus.select(
                List.of(bundle), euros(2999), "je ne reconnais pas un prélèvement sur ma facture");

        // THEN it is not treated as a missing amount
        assertThat(result.kind()).isEqualTo(AmountFocus.Kind.FULL);
    }

    @Test
    void an_unknown_amount_is_not_the_whole_bill() {
        // GIVEN a bundle of 29.99 €
        BillingCause bundle = cause(BillingCauseType.ONE_OFF_BUNDLE, 2999, "1gb-usa-roaming-bundle");

        // WHEN the customer names an amount that is not on the difference
        AmountFocus.Result result = focus.select(List.of(bundle), euros(2999), "le 12,34");

        // THEN the answer asks again instead of reciting the bill
        assertThat(result.kind()).isEqualTo(AmountFocus.Kind.UNKNOWN);
    }

    private static BillingCause cause(BillingCauseType type, long cents, String label) {
        Money amount = euros(cents);
        LineDelta delta = new LineDelta(label, ChangeKind.APPEARED, Money.zero(EUR), amount, amount);
        return new BillingCause(type, amount, List.of(delta));
    }

    private static Money euros(long cents) {
        return Money.ofMinorUnits(cents, EUR);
    }
}
