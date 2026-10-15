# ADR-0057: Invoice-difference cause catalogue

## Status

Accepted (target, 2026-10-15). Not implemented. The running comparison engine still
emits the coarser codes from the ADR-0003 amendment (TASK-BE-067) until
**TASK-BE-073** lands.

## Context

Customers ask why one invoice differs from the previous one. The deterministic
comparison (ADR-0003) already attributes each line delta to a `BillingCauseType`
and the LLM only phrases that result (ADR-0052, DEC-002). The codes in the
engine today are too coarse for the explanation Product wants:

- an option and a whole new service (eir TV, eir Mobile) share no distinct
  "something was added" versus "the existing offer was replaced";
- a subscription whose amount changes in place stays `UNEXPLAINED` and escalates,
  even when the label is unchanged (a tariff increase) or the offer name changed
  (a plan change);
- usage, tax, a credit, and an activation fee have no place in the nine-cause
  table Product supplied (2026-10-15).

That table's detection column (Siebel history, a CRM `CYCLE_CHANGE` event,
Spanish keywords such as `mora` / `atrasad` / `Servicios Basicos`) does not match
this product. The source of truth here is two invoices extracted from the bill
(ADR-0005). There is no Siebel feed and no CRM cycle event on the comparison path.

Product confirmed, in conversation on 2026-10-15:

- keep the nine named causes from the table;
- a new TV or mobile, or an option that appears or disappears, is an add-on, not
  a plan change;
- a plan change is the existing offer being replaced (the subscription label
  changes);
- the first partial month of a newly added service belongs to that add-on, not
  to a cycle change;
- four further causes cover what the nine do not: out-of-bundle usage, a tax
  change that is not merely the VAT on another named cause, a billing
  adjustment, and an activation or installation fee.

## Decision

The comparison attributes every line delta to one cause in the catalogue below,
or to `UNEXPLAINED`. `UNEXPLAINED` is not a business cause: its amount stays in
the residual and can still escalate (BR-003). No LLM chooses the cause.

| Cause | Meaning | Direction | How the two invoices show it |
|---|---|---|---|
| `PLAN_CHANGE` | The customer replaced the offer they already had. | up or down | The subscription line's label changes. |
| `TARIFF_INCREASE` | The same basic service costs more. | up | The same subscription label, a higher amount, no label change. |
| `SPEED_UPGRADE` | The home-internet speed changed. | up | The home-internet line's speed token changes and the amount rises. |
| `ADDON_ADDED` | An extra service or option was added (TV, mobile, pack), including that service's mid-cycle prorata. | up | An option or a whole service line appears. Its prorata on the same new service is part of this cause. |
| `ADDON_REMOVED` | An extra service or option was removed. | down | An option or a whole service line disappears. |
| `DISCOUNT_ENDED` | A discount that was on the previous invoice is absent now. | up | A discount line present before, absent now. |
| `DEVICE_INSTALLMENT_ENDED` | Device installments stopped. | down | A device-installment line present before, absent now. |
| `LATE_PAYMENT_FEE` | A late-payment fee was charged. | up | A charge whose description matches the confirmed late-fee labels. |
| `CHANGE_CYCLE` | Proration because the billing cycle itself changed. | up or down | Not decided from the two invoices alone. |
| `USAGE_OVERAGE` | Out-of-bundle usage changed (calls, data, roaming). | up or down | A usage or overage line appears, disappears, or changes amount. |
| `TAX_CHANGE` | The tax moved on its own. | up or down | The tax line amount differs, and that difference is not the VAT already carried by another named cause. |
| `BILLING_ADJUSTMENT` | A credit, goodwill gesture, or correction. | up or down | An adjustment line appears, disappears, or changes amount. |
| `ACTIVATION_FEE` | A one-time activation or installation fee. | up or down | An activation/installation line is on one invoice and not the other (for example "Broadband activation fee"). |

Rules that keep the story from being told twice:

- Adding eir TV or eir Mobile beside an offer that stays is `ADDON_ADDED`. It is
  not `PLAN_CHANGE`.
- Replacing the offer (the subscription label changes) is `PLAN_CHANGE`.
- The partial first period of a service that just appeared ("eir TV from 25 Sep
  until 11 Oct") is part of `ADDON_ADDED`, not `CHANGE_CYCLE`.
- If VAT rises only because a named cause added taxable amount, the explanation
  voices that cause. `TAX_CHANGE` is used only when the tax movement is not
  already inside another named cause.
- `DEVICE_INSTALLMENT_ENDED` and `LATE_PAYMENT_FEE` are not guessed from Spanish
  keywords or from a generic one-off line. They fire only against a confirmed
  eir label list. Until that list exists, those lines stay `UNEXPLAINED`.
- `SPEED_UPGRADE` fires only when a speed token on the home-internet line
  changes and the amount rises. Current eir B2C samples do not carry an
  "Internet N megas" token; a broadband offer rename is `PLAN_CHANGE`, and a
  same-label price rise is `TARIFF_INCREASE`.
- `CHANGE_CYCLE` stays in the catalogue and is not attributed from invoice lines
  alone. There is no CRM cycle-change signal on this path.

The spoken explanation uses one FR and one EN phrase per cause (owned next to
the existing composer phrases). Amounts stay those computed by the comparison.
The LLM does not invent a cause or an amount.

## Consequences

- `BillingCauseType` and the comparison attribution change. `SERVICE_ADDED` /
  `SERVICE_REMOVED` fold into `ADDON_ADDED` / `ADDON_REMOVED`. `DISCOUNT_EXPIRY`
  becomes `DISCOUNT_ENDED`. `OPTION_CHANGE`, `PRORATION`, and `ONE_OFF_FEE` are
  no longer voiced as their own causes.
- An in-place subscription price rise with an unchanged label becomes
  `TARIFF_INCREASE` and is explained, instead of escalating as `UNEXPLAINED`.
- A same-label price drop, an in-place option price change, a discount that
  appears, and any one-off that is neither activation, a confirmed late fee, nor
  a confirmed ended installment stay `UNEXPLAINED`.
- Confidence and escalation (BR-003) are unchanged: only `UNEXPLAINED` amounts
  count as residual.
- Wording, domain tests, and the eir B2C comparison fixtures must expect the new
  codes. Samples that cannot show speed, late fees, device installments, or a
  cycle change must not assert those causes.

## Alternatives Considered

- **Keep the nine table rows only and drop usage, tax, adjustment, and
  activation.** Rejected. Product confirmed those four do not fit a plan change
  or an add-on, and dropping them would make true explanations fail closed.
- **Detect causes from Siebel or a CRM `CYCLE_CHANGE` event, as in the source
  table.** Rejected for V1. Those signals are not on the invoice-comparison path.
- **Let the LLM pick the cause from the two invoices.** Rejected. ADR-0003 and
  DEC-002: the model phrases a cause the engine already computed.

## Related Documents

- ADR-0003 (deterministic comparison; the TASK-BE-067 amendment remains the
  behaviour until this ADR is implemented)
- ADR-0005 (extract the PDF before comparison)
- ADR-0052 (the LLM phrases the pre-computed explanation)
- `product-backlog/tasks/task-be-073-invoice-difference-causes.md`
