package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.BillingCauseType;
import com.ai4cc.billing.domain.model.ChangeKind;
import com.ai4cc.billing.domain.model.Invoice;
import com.ai4cc.billing.domain.model.InvoiceComparison;
import com.ai4cc.billing.domain.model.InvoiceGroup;
import com.ai4cc.billing.domain.model.InvoiceItem;
import com.ai4cc.billing.domain.model.InvoiceSection;
import com.ai4cc.billing.domain.model.valueobject.AccountId;
import com.ai4cc.billing.domain.model.valueobject.BillingPeriod;
import com.ai4cc.billing.domain.model.valueobject.Evidence;
import com.ai4cc.billing.domain.model.valueobject.InvoiceId;
import com.ai4cc.billing.domain.model.valueobject.InvoiceLevel;
import com.ai4cc.billing.domain.model.valueobject.LineAmounts;
import com.ai4cc.billing.domain.model.valueobject.LineCategory;
import com.ai4cc.billing.domain.model.valueobject.Money;
import com.ai4cc.billing.infrastructure.fixtures.BssBillingFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InvoiceComparisonService (deterministic comparison)")
class InvoiceComparisonServiceTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private final InvoiceComparisonService service = new InvoiceComparisonService();

    @Test
    void identical_invoices_produce_no_delta_no_cause() {
        // GIVEN the nominal journey (eir-001): two identical invoices
        Invoice previous = fixture("eir-001", "2026-01");
        Invoice current = fixture("eir-001", "2026-02");

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN there is no change, no line delta, no cause and no residual
        assertThat(comparison.totalDelta().isZero()).isTrue();
        assertThat(comparison.lineDeltas()).isEmpty();
        assertThat(comparison.causes()).isEmpty();
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void an_expired_discount_is_attributed_to_discount_expiry() {
        // GIVEN the discount-expiry journey (eir-002): previous had a -500 discount, current does not
        InvoiceComparison comparison =
                service.compare(fixture("eir-002", "2026-01"), fixture("eir-002", "2026-02"));

        // WHEN / THEN the +500 change is attributed to a single DISCOUNT_EXPIRY cause, fully explained
        assertThat(comparison.totalDelta().minorUnits()).isEqualTo(500L);
        assertThat(comparison.lineDeltas()).singleElement().satisfies(delta -> {
            assertThat(delta.kind()).isEqualTo(ChangeKind.DISAPPEARED);
            assertThat(delta.contribution().minorUnits()).isEqualTo(500L);
        });
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.DISCOUNT_ENDED);
            assertThat(cause.impact().minorUnits()).isEqualTo(500L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_new_overage_line_is_attributed_to_usage_overage() {
        // GIVEN the overage journey (eir-003): current adds a +1200 overage line
        InvoiceComparison comparison =
                service.compare(fixture("eir-003", "2026-01"), fixture("eir-003", "2026-02"));

        // WHEN / THEN the +1200 change is an APPEARED line attributed to USAGE_OVERAGE
        assertThat(comparison.totalDelta().minorUnits()).isEqualTo(1200L);
        assertThat(comparison.lineDeltas()).singleElement().satisfies(delta ->
                assertThat(delta.kind()).isEqualTo(ChangeKind.APPEARED));
        assertThat(comparison.causes()).singleElement().satisfies(cause ->
                assertThat(cause.type()).isEqualTo(BillingCauseType.USAGE_OVERAGE));
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_prorata_with_no_new_service_stays_unexplained() {
        // GIVEN the proration journey (eir-004): a prorata appears, but no service or option appears with it
        InvoiceComparison comparison =
                service.compare(fixture("eir-004", "2026-01"), fixture("eir-004", "2026-02"));

        // WHEN / THEN it is not a cycle change and the +800 stays residual
        assertThat(comparison.totalDelta().minorUnits()).isEqualTo(800L);
        assertThat(comparison.causes()).isEmpty();
        assertThat(comparison.unexplainedAmount().minorUnits()).isEqualTo(800L);
    }

    @Test
    void surfaces_the_residual_when_line_deltas_do_not_reconcile_with_the_total() {
        // GIVEN a header total that changed by 1000 but only an 800 identified (usage) line
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", 1000L, List.of(line("data", LineCategory.USAGE, 800L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the 800 is an identified USAGE_OVERAGE cause and the 200 header/line gap is the residual
        assertThat(comparison.totalDelta().minorUnits()).isEqualTo(1000L);
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.USAGE_OVERAGE);
            assertThat(cause.impact().minorUnits()).isEqualTo(800L);
        });
        assertThat(comparison.unexplainedAmount().minorUnits()).isEqualTo(200L);
    }

    @Test
    void a_same_label_subscription_price_rise_is_a_tariff_increase() {
        // GIVEN the same subscription label on both invoices, with a higher amount
        Invoice previous = header("prev", 300L, List.of(line("base", LineCategory.SUBSCRIPTION, 300L)));
        Invoice current = header("curr", 500L, List.of(line("base", LineCategory.SUBSCRIPTION, 500L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the rise is a tariff increase and nothing is left unexplained
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.TARIFF_INCREASE);
            assertThat(cause.impact().minorUnits()).isEqualTo(200L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_same_label_subscription_price_drop_stays_unexplained() {
        // GIVEN the same subscription label costing less, which the catalogue does not name
        Invoice previous = header("prev", 500L, List.of(line("base", LineCategory.SUBSCRIPTION, 500L)));
        Invoice current = header("curr", 300L, List.of(line("base", LineCategory.SUBSCRIPTION, 300L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN no cause is claimed and the drop is residual
        assertThat(comparison.causes()).isEmpty();
        assertThat(comparison.unexplainedAmount().minorUnits()).isEqualTo(-200L);
    }

    @Test
    void a_new_subscription_line_is_attributed_to_service_added() {
        // GIVEN a brand-new service subscription appearing (absent in the previous bill) — TASK-BE-067
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", 1999L, List.of(line("tv", LineCategory.SUBSCRIPTION, 1999L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN it is a named SERVICE_ADDED cause and nothing is left unexplained
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.ADDON_ADDED);
            assertThat(cause.impact().minorUnits()).isEqualTo(1999L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_removed_subscription_line_is_attributed_to_service_removed() {
        // GIVEN a service subscription that was billed previously and is gone now — TASK-BE-067
        Invoice previous = header("prev", 1999L, List.of(line("tv", LineCategory.SUBSCRIPTION, 1999L)));
        Invoice current = header("curr", 0L, List.of());

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN it is a named SERVICE_REMOVED cause (negative impact) and nothing is left unexplained
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.ADDON_REMOVED);
            assertThat(cause.impact().minorUnits()).isEqualTo(-1999L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void never_drops_a_second_line_that_shares_a_matching_key() {
        // GIVEN two OPTION lines in the new invoice that resolve to the SAME matching key (same code) —
        // the BUG-028 shape: before the fix the second line was silently dropped by putIfAbsent and its
        // amount vanished from the diff into the residual.
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", 1698L, List.of(
                line("opt", LineCategory.OPTION, 1499L),
                line("opt", LineCategory.OPTION, 199L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN both lines contribute: OPTION_CHANGE sums to 1698 and nothing is left unexplained
        assertThat(comparison.totalDelta().minorUnits()).isEqualTo(1698L);
        assertThat(comparison.lineDeltas()).hasSize(2);
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.ADDON_ADDED);
            assertThat(cause.impact().minorUnits()).isEqualTo(1698L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_changed_subscription_label_is_a_plan_change() {
        // GIVEN the same subscription code whose description changed, with no speed token
        Invoice previous = header("prev", 3000L, List.of(described("fibre", "Fibre unlimited", LineCategory.SUBSCRIPTION, 3000L)));
        Invoice current = header("curr", 3500L, List.of(described("fibre", "Fibre max", LineCategory.SUBSCRIPTION, 3500L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the offer replacement is a plan change
        assertThat(comparison.causes()).singleElement().satisfies(cause ->
                assertThat(cause.type()).isEqualTo(BillingCauseType.PLAN_CHANGE));
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_renamed_offer_with_a_new_code_is_a_plan_change() {
        // GIVEN the only subscription replaced: the slug changes, so the old line disappears
        // and the new line appears (the eir PDF path, where the code is the label slug)
        Invoice previous = header("prev", 3000L,
                List.of(described("fibre-100", "Fibre 100", LineCategory.SUBSCRIPTION, 3000L)));
        Invoice current = header("curr", 3500L,
                List.of(described("fibre-500", "Fibre 500", LineCategory.SUBSCRIPTION, 3500L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN one plan change carries the net
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.PLAN_CHANGE);
            assertThat(cause.impact().minorUnits()).isEqualTo(500L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_higher_home_internet_speed_is_a_speed_upgrade() {
        // GIVEN the same broadband line whose speed token and price both rise
        Invoice previous = header("prev", 3000L, List.of(described("fibre", "Broadband 100 Mb", LineCategory.SUBSCRIPTION, 3000L)));
        Invoice current = header("curr", 4000L, List.of(described("fibre", "Broadband 500 Mb", LineCategory.SUBSCRIPTION, 4000L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN it is a speed upgrade, not a generic plan change
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.SPEED_UPGRADE);
            assertThat(cause.impact().minorUnits()).isEqualTo(1000L);
        });
    }

    @Test
    void an_activation_fee_on_one_invoice_is_an_activation_fee() {
        // GIVEN a broadband activation fee that was billed only on the previous invoice
        Invoice previous = header("prev", 4999L, List.of(described("fee", "Broadband activation fee", LineCategory.ONE_OFF, 4999L)));
        Invoice current = header("curr", 0L, List.of());

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the fee is named and the drop is explained
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.ACTIVATION_FEE);
            assertThat(cause.impact().minorUnits()).isEqualTo(-4999L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void an_activation_fee_on_the_current_invoice_only_is_an_activation_fee() {
        // GIVEN a broadband activation fee billed only on the current invoice
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", 4999L,
                List.of(described("fee", "Broadband activation fee", LineCategory.ONE_OFF, 4999L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the fee is named and the rise is explained
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.ACTIVATION_FEE);
            assertThat(cause.impact().minorUnits()).isEqualTo(4999L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_one_off_bundle_is_named() {
        // GIVEN a one-off bundle billed only on the current invoice
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", 2999L, List.of(described(
                "roam", "1GB USA/Canada Roaming Bundle 25 Sep 26", LineCategory.ONE_OFF, 2999L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the bundle is named and the rise is explained
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.ONE_OFF_BUNDLE);
            assertThat(cause.impact().minorUnits()).isEqualTo(2999L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_one_off_bundle_on_the_previous_invoice_only_is_named() {
        // GIVEN a one-off bundle that was billed only on the previous invoice
        Invoice previous = header("prev", 2999L, List.of(described(
                "roam", "1GB USA/Canada Roaming Bundle 25 Sep 26", LineCategory.ONE_OFF, 2999L)));
        Invoice current = header("curr", 0L, List.of());

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the bundle is named and the drop is explained
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.ONE_OFF_BUNDLE);
            assertThat(cause.impact().minorUnits()).isEqualTo(-2999L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_one_off_without_a_bundle_label_stays_unexplained() {
        // GIVEN a one-off that is neither activation, a late fee, an installment, nor a bundle
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", 2999L, List.of(described("paper", "Paper bill fee", LineCategory.ONE_OFF, 2999L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN it is not guessed into a named cause
        assertThat(comparison.causes()).isEmpty();
        assertThat(comparison.unexplainedAmount().minorUnits()).isEqualTo(2999L);
    }

    @Test
    void a_late_fee_fires_only_when_its_label_is_configured() {
        // GIVEN a late-payment line and an explicit label list
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", 700L, List.of(described("fee", "Late payment fee", LineCategory.ONE_OFF, 700L)));
        InvoiceComparisonService configured = new InvoiceComparisonService(List.of("late payment"), List.of());

        // WHEN compared with the list, and with the empty default
        InvoiceComparison named = configured.compare(previous, current);
        InvoiceComparison ignored = service.compare(previous, current);

        // THEN only the configured list names the fee
        assertThat(named.causes()).singleElement().satisfies(cause ->
                assertThat(cause.type()).isEqualTo(BillingCauseType.LATE_PAYMENT_FEE));
        assertThat(ignored.causes()).isEmpty();
    }

    @Test
    void a_prorata_of_a_new_service_is_part_of_the_add_on() {
        // GIVEN a new TV service and the partial month that starts it
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", 3132L, List.of(
                described("tv", "eir TV", LineCategory.SUBSCRIPTION, 1999L),
                described("tv-prorata", "eir TV from 25 Sep until 11 Oct", LineCategory.PRORATA, 1133L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN both lines are the add-on and nothing is left unexplained
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.ADDON_ADDED);
            assertThat(cause.impact().minorUnits()).isEqualTo(3132L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_partial_period_that_drops_off_stays_part_of_the_add_on() {
        // GIVEN eir TV stays, while its first four days and that period's discount are only on the previous bill
        Invoice previous = header("prev", 2193L, List.of(
                described("tv", "eir TV", LineCategory.SUBSCRIPTION, 1999L),
                described("tv-prorata", "eir TV from 08 Sep 26 until 11 Sep 26", LineCategory.PRORATA, 258L),
                described("tv-discount", "eir TV discount from 08 Sep until 11 Sep", LineCategory.DISCOUNT, -64L)));
        Invoice current = header("curr", 1999L, List.of(
                described("tv", "eir TV", LineCategory.SUBSCRIPTION, 1999L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN both lines are the add-on and the discount is not one that ended
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.ADDON_ADDED);
            assertThat(cause.impact().minorUnits()).isEqualTo(-194L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_discount_that_ends_is_not_part_of_a_staying_add_on() {
        // GIVEN the TV stays and a discount without a short period disappears
        Invoice previous = header("prev", 1499L, List.of(
                described("tv", "eir TV", LineCategory.SUBSCRIPTION, 1999L),
                described("promo", "eir TV welcome credit", LineCategory.DISCOUNT, -500L)));
        Invoice current = header("curr", 1999L, List.of(
                described("tv", "eir TV", LineCategory.SUBSCRIPTION, 1999L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the discount ended, and it is not rewritten as an add-on
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.DISCOUNT_ENDED);
            assertThat(cause.impact().minorUnits()).isEqualTo(500L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_dated_discount_on_another_window_stays_a_discount_that_ended() {
        // GIVEN the TV partial period drops off, and a TV discount for a different window ends too
        Invoice previous = header("prev", 1757L, List.of(
                described("tv", "eir TV", LineCategory.SUBSCRIPTION, 1999L),
                described("tv-prorata", "eir TV from 08 Sep 26 until 11 Sep 26", LineCategory.PRORATA, 258L),
                described("loyalty", "eir TV loyalty from 01 Jan 26 until 31 Dec 26", LineCategory.DISCOUNT, -500L)));
        Invoice current = header("curr", 1999L, List.of(
                described("tv", "eir TV", LineCategory.SUBSCRIPTION, 1999L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the partial period stays the add-on and the other discount ended
        assertThat(impact(comparison, BillingCauseType.ADDON_ADDED)).isEqualTo(-258L);
        assertThat(impact(comparison, BillingCauseType.DISCOUNT_ENDED)).isEqualTo(500L);
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_discount_on_another_service_stays_a_discount_that_ended() {
        // GIVEN the TV partial period drops off, and a fibre discount covers those same four days
        Invoice previous = header("prev", 10396L, List.of(
                described("tv", "eir TV", LineCategory.SUBSCRIPTION, 1999L),
                described("fibre", "eir Fibre", LineCategory.SUBSCRIPTION, 8599L),
                described("tv-prorata", "eir TV from 08 Sep 26 until 11 Sep 26", LineCategory.PRORATA, 258L),
                described("fibre-discount", "eir Fibre loyalty from 08 Sep until 11 Sep", LineCategory.DISCOUNT, -460L)));
        Invoice current = header("curr", 10598L, List.of(
                described("tv", "eir TV", LineCategory.SUBSCRIPTION, 1999L),
                described("fibre", "eir Fibre", LineCategory.SUBSCRIPTION, 8599L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the fibre discount is not pulled into the TV add-on
        assertThat(impact(comparison, BillingCauseType.ADDON_ADDED)).isEqualTo(-258L);
        assertThat(impact(comparison, BillingCauseType.DISCOUNT_ENDED)).isEqualTo(460L);
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_tax_line_that_follows_another_cause_is_not_voiced_as_tax() {
        // GIVEN a new service whose embedded VAT equals a separate tax line
        InvoiceItem tv = described("tv", "eir TV", LineCategory.SUBSCRIPTION, 1200L);
        long tax = tv.amounts().tax().minorUnits();
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", 1200L + tax, List.of(tv, line("vat", LineCategory.TAX, tax)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN only the add-on is voiced and the tax is not a second cause
        assertThat(comparison.causes()).singleElement().satisfies(cause ->
                assertThat(cause.type()).isEqualTo(BillingCauseType.ADDON_ADDED));
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_discount_that_starts_is_a_discount_added() {
        // GIVEN a discount present only on the current invoice
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", -500L, List.of(line("promo", LineCategory.DISCOUNT, -500L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the new discount is named and the drop is explained
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.DISCOUNT_ADDED);
            assertThat(cause.impact().minorUnits()).isEqualTo(-500L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void a_discount_whose_amount_changes_in_place_stays_unexplained() {
        // GIVEN the same discount code, with a larger reduction on the current invoice
        Invoice previous = header("prev", -500L, List.of(line("promo", LineCategory.DISCOUNT, -500L)));
        Invoice current = header("curr", -800L, List.of(line("promo", LineCategory.DISCOUNT, -800L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN a changed discount amount is not a discount that started or ended
        assertThat(comparison.causes()).isEmpty();
        assertThat(comparison.unexplainedAmount().minorUnits()).isEqualTo(-300L);
    }

    @Test
    void a_tax_line_on_its_own_is_a_tax_change() {
        // GIVEN a tax line with no other named cause
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", 100L, List.of(line("vat", LineCategory.TAX, 100L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN the tax movement is voiced on its own
        assertThat(comparison.causes()).singleElement().satisfies(cause ->
                assertThat(cause.type()).isEqualTo(BillingCauseType.TAX_CHANGE));
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void an_adjustment_is_a_billing_adjustment() {
        // GIVEN a credit on the current invoice
        Invoice previous = header("prev", 0L, List.of());
        Invoice current = header("curr", -300L, List.of(line("credit", LineCategory.ADJUSTMENT, -300L)));

        // WHEN they are compared
        InvoiceComparison comparison = service.compare(previous, current);

        // THEN it is a billing adjustment and fully explained
        assertThat(comparison.causes()).singleElement().satisfies(cause -> {
            assertThat(cause.type()).isEqualTo(BillingCauseType.BILLING_ADJUSTMENT);
            assertThat(cause.impact().minorUnits()).isEqualTo(-300L);
        });
        assertThat(comparison.unexplainedAmount().isZero()).isTrue();
    }

    @Test
    void an_ended_installment_fires_only_when_its_label_is_configured() {
        // GIVEN a one-off handset line billed only on the previous invoice
        Invoice previous = header("prev", 1500L,
                List.of(described("phone", "Handset installment", LineCategory.ONE_OFF, 1500L)));
        Invoice current = header("curr", 0L, List.of());
        InvoiceComparisonService configured = new InvoiceComparisonService(List.of(), List.of("installment"));

        // WHEN compared with the list, and with the empty default
        InvoiceComparison named = configured.compare(previous, current);
        InvoiceComparison ignored = service.compare(previous, current);

        // THEN only the configured list names the ended installment
        assertThat(named.causes()).singleElement().satisfies(cause ->
                assertThat(cause.type()).isEqualTo(BillingCauseType.DEVICE_INSTALLMENT_ENDED));
        assertThat(ignored.causes()).isEmpty();
    }

    @Test
    void throws_when_an_invoice_is_null() {
        // GIVEN a valid current invoice
        Invoice current = fixture("eir-001", "2026-02");

        // WHEN / THEN a null previous invoice is rejected
        assertThatThrownBy(() -> service.compare(null, current))
                .isInstanceOf(NullPointerException.class);
    }

    private static Invoice fixture(String account, String periodSuffix) {
        return BssBillingFixtures.all().get(AccountId.of(account)).stream()
                .filter(invoice -> invoice.period().id().endsWith(periodSuffix))
                .findFirst().orElseThrow();
    }

    private static Invoice header(String id, long totalTaxIncludedCents, List<InvoiceItem> items) {
        InvoiceGroup group = new InvoiceGroup(id + "-g", "Charges", null, 0, amounts(0L), items);
        InvoiceSection section = new InvoiceSection(id + "-s", "Invoice", 0, true, amounts(0L), List.of(group));
        return new Invoice(InvoiceId.of(id), AccountId.of("eir-x"), InvoiceLevel.BILLING_ACCOUNT,
                new BillingPeriod(id, LocalDate.of(2026, 2, 15)), amounts(totalTaxIncludedCents),
                List.of(section));
    }

    private static InvoiceItem line(String id, LineCategory category, long taxIncludedCents) {
        return described(id, id, category, taxIncludedCents);
    }

    private static InvoiceItem described(String id, String text, LineCategory category, long taxIncludedCents) {
        return new InvoiceItem(id, category.name(), id, "S", category, amounts(taxIncludedCents),
                new Evidence("test", null, text));
    }

    private static LineAmounts amounts(long taxIncludedCents) {
        long tax = taxIncludedCents / 6L;
        long taxExcluded = taxIncludedCents - tax;
        return new LineAmounts(cents(taxIncludedCents), cents(taxExcluded), cents(tax));
    }

    private static Money cents(long value) {
        return Money.ofMinorUnits(value, EUR);
    }

    private static long impact(InvoiceComparison comparison, BillingCauseType type) {
        return comparison.causes().stream()
                .filter(cause -> cause.type() == type)
                .mapToLong(cause -> cause.impact().minorUnits())
                .findFirst().orElseThrow();
    }
}
