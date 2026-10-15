package com.ai4cc.billing.infrastructure.pdf;

import com.ai4cc.billing.domain.model.BillingCause;
import com.ai4cc.billing.domain.model.BillingCauseType;
import com.ai4cc.billing.domain.model.Invoice;
import com.ai4cc.billing.domain.model.InvoiceComparison;
import com.ai4cc.billing.domain.model.valueobject.AccountId;
import com.ai4cc.billing.domain.model.valueobject.InvoiceSummary;
import com.ai4cc.billing.domain.port.in.CompareInvoicesUseCase;
import com.ai4cc.billing.domain.port.in.RetrieveComparableInvoicesUseCase;
import com.ai4cc.billing.domain.port.out.BssBillingPort;
import com.ai4cc.billing.domain.service.ComparableInvoiceService;
import com.ai4cc.billing.domain.service.InvoiceComparisonService;
import com.ai4cc.billing.infrastructure.adapter.out.bss.pdf.PdfBssBillingAdapter;
import com.ai4cc.billing.infrastructure.adapter.out.bss.pdf.SampleEirB2cBillRunDocumentAdapter;
import com.ai4cc.billing.infrastructure.adapter.out.pdf.PdfBoxInvoiceExtractorAdapter;
import com.ai4cc.shared.observability.BackendTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

// TASK-BE-066 — the user's question "why do I pay more this month?" for account 99224964, answered
// end to end from the REAL eir B2C PDFs with no LLM and no database: the sample PDF bytes are served
// by SampleEirB2cBillRunDocumentAdapter, parsed by the eir B2C PDFBox layout parser through the
// PdfBssBillingAdapter, listed most-recent-first by ComparableInvoiceService, then diffed by the
// deterministic InvoiceComparisonService. This proves the explanation is driven by the real parsed
// invoice amounts (the LLM only phrases this grounded, pre-computed result — DEC-002).
class EirB2cBillingComparisonE2eTest {

    private final BssBillingPort bss = new PdfBssBillingAdapter(
            new SampleEirB2cBillRunDocumentAdapter(),
            new PdfBoxInvoiceExtractorAdapter(new EirB2cInvoiceLayoutParser()),
            new BackendTelemetry(new SimpleMeterRegistry()));
    private final RetrieveComparableInvoicesUseCase comparables = new ComparableInvoiceService(bss);
    private final CompareInvoicesUseCase comparison = new InvoiceComparisonService();

    // 99224964 (single mobile line). The new 15GB bundle, its first partial month and Mobile Security
    // are one add-on (ADR-0057). The USA/Canada line is a one-off bundle, not usage. BUG-028: the two
    // OPTION lines stay distinct instead of collapsing by category name.
    @Test
    void account99224964_newBundlesAndTheOneOffBundleAreExplained() {
        InvoiceComparison result = compareSeptemberVsAugust("99224964", "2609129000000008", "2608129000000039",
                1999, 7546);

        assertEquals(5547, result.totalDelta().minorUnits(), "total delta +€55.47 from the real invoices");
        Map<BillingCauseType, Long> impact = impacts(result);
        assertEquals(2548L, impact.get(BillingCauseType.ADDON_ADDED),
                "15GB prorata €8.50 + 15GB Bundle €14.99 + Mobile Security €1.99");
        assertEquals(2999L, impact.get(BillingCauseType.ONE_OFF_BUNDLE), "1GB USA/Canada roaming bundle");
        assertEquals(0L, result.unexplainedAmount().minorUnits(), "the one-off bundle is named");
    }

    // 99226126 (fibre → fibre + new eir TV). The installation fee is an activation fee. The new TV
    // base, its extra pack and its first partial month are one add-on. Both new TV discounts start
    // this month and are one DISCOUNT_ADDED (BUG-028 keeps the two lines, summed into that cause).
    @Test
    void account99226126_newTvAndItsNewDiscountsAreExplained() {
        InvoiceComparison result = compareSeptemberVsAugust("99226126", "2609129000000009", "2608129000000040",
                13998, 7347);

        assertEquals(-6651, result.totalDelta().minorUnits(), "net change −€66.51 (install fee gone, TV added)");
        Map<BillingCauseType, Long> impact = impacts(result);
        assertEquals(-9999L, impact.get(BillingCauseType.ACTIVATION_FEE), "fibre installation fee no longer billed");
        assertEquals(4131L, impact.get(BillingCauseType.ADDON_ADDED),
                "eir TV €19.99 + Extra pack €9.99 + first partial month €11.33");
        assertEquals(-783L, impact.get(BillingCauseType.DISCOUNT_ADDED),
                "BOTH new TV discounts: −€2.83 prorata + −€5.00 (BUG-028: not collapsed)");
        assertEquals(0L, result.unexplainedAmount().minorUnits(), "the new discounts are named");
    }

    // 99226337 (fibre + TV → fibre + TV + new mobile 5G). The TV partial period of 8–11 Sep and its
    // discount prorata stay part of that add-on. The new mobile is a separate add-on. Residual is zero.
    @Test
    void account99226337_tvPartialPeriodAndNewMobileAreExplained() {
        InvoiceComparison result = compareSeptemberVsAugust("99226337", "2609129000000011", "2608129000000042",
                14689, 18112);

        assertEquals(3423, result.totalDelta().minorUnits(), "net change +€34.23");
        Map<BillingCauseType, Long> impact = impacts(result);
        assertEquals(-4999L, impact.get(BillingCauseType.ACTIVATION_FEE), "broadband activation fee no longer billed");
        assertEquals(9988L, impact.get(BillingCauseType.ADDON_ADDED),
                "mobile +101.82 € and the TV partial period net −1.94 € (2.58 − 0.64)");
        assertEquals(-1566L, impact.get(BillingCauseType.DISCOUNT_ADDED),
                "the two new mobile discounts −€5.66 −€10.00");
        assertEquals(0L, result.unexplainedAmount().minorUnits(), "the TV partial period is part of the add-on");
    }

    private InvoiceComparison compareSeptemberVsAugust(String accountId, String septemberId, String augustId,
            long augustTotalCents, long septemberTotalCents) {
        AccountId account = AccountId.of(accountId);
        List<InvoiceSummary> available = comparables.availableInvoices(account);
        assertEquals(2, available.size(), "two bill runs for " + accountId);
        assertEquals(septemberId, available.get(0).id().value(), "most recent = September for " + accountId);
        assertEquals(augustId, available.get(1).id().value(), "previous = August for " + accountId);
        Invoice september = bss.fetchInvoice(account, available.get(0).id()).orElseThrow();
        Invoice august = bss.fetchInvoice(account, available.get(1).id()).orElseThrow();
        assertEquals(augustTotalCents, august.totals().taxIncluded().minorUnits(), "August total " + accountId);
        assertEquals(septemberTotalCents, september.totals().taxIncluded().minorUnits(),
                "September total " + accountId);
        return comparison.compare(august, september);
    }

    private static Map<BillingCauseType, Long> impacts(InvoiceComparison result) {
        return result.causes().stream()
                .collect(Collectors.toMap(BillingCause::type, cause -> cause.impact().minorUnits()));
    }
}
