# TASK-BE-073 — Attribute invoice differences to the ADR-0057 cause catalogue

**Type:** Technical task (backend billing domain)
**Status:** 🟢 Open (2026-10-15) — Sprint 20 (`feat/sprint-20-invoice-difference-causes`).
Implementation is on `task/TASK-BE-073-invoice-difference-causes`. Backend tests
green (797). Not merged.
**Adversarial review 95/100 (Pass, 2026-10-15)** — third pass, after
`ONE_OFF_BUNDLE`. The three eir accounts are explained, including 99224964
(+25.48 € options and +29.99 € one-off bundle). A one-off bundle on the previous
invoice only is tested (−29.99 €). Residual (accepted): a plan rename beside a
staying subscription is an add-on pair; a discount whose amount changes on the
same line stays `UNEXPLAINED`.
**Smoke (2026-10-15): GO.** Superseded by the acceptance run the same day.
**QA (2026-10-15): GO.** Six Cucumber journeys through the answer engine, plus the real PDF comparison. Report: `docs/qa/task-be-073-qa-report.md`.
Full review: `docs/qa/task-be-073-adversarial-review.md`.
**Priority:** Medium
**Epic:** EPIC-004 (Billing explanation)
**Delivers:** ADR-0057. Refines the ADR-0003 / TASK-BE-067 cause attribution.
**Related:** ADR-0003, ADR-0005, ADR-0052, DEC-002, BR-003, TASK-BE-042, TASK-BE-067.

## Context

The comparison engine explains an invoice delta with a short cause list
(`DISCOUNT_EXPIRY`, `USAGE_OVERAGE`, `OPTION_CHANGE`, `SERVICE_ADDED`,
`SERVICE_REMOVED`, `PRORATION`, `TAX`, `ONE_OFF_FEE`, `ADJUSTMENT`). Anything else,
including a subscription whose price changes in place, falls into `UNEXPLAINED`
and can escalate.

Product (2026-10-15) confirmed a finer catalogue: the nine causes in the supplied
table, plus four that do not fit those nine. A new TV or mobile is an add-on, not
a plan change. Detection uses the two extracted invoices only — not Siebel, not a
CRM cycle event, and not the Spanish keywords in the source table. The decision
is ADR-0057. This ticket implements it.

## In scope

- Replace the voiced cause set with the ADR-0057 catalogue. `UNEXPLAINED` stays
  the fail-closed residual (BR-003) and is still not voiced as a cause.
- Attribute from the two invoices using the rules in ADR-0057:
  - subscription label changes → `PLAN_CHANGE`;
  - same subscription label, higher amount → `TARIFF_INCREASE`;
  - home-internet speed token changes and the amount rises → `SPEED_UPGRADE`;
  - an option or a whole service appears, including that service's mid-cycle
    prorata → `ADDON_ADDED`;
  - an option or a whole service disappears → `ADDON_REMOVED`;
  - a discount line present before and absent now → `DISCOUNT_ENDED`;
  - a discount line absent before and present now → `DISCOUNT_ADDED`;
  - a usage or overage line appears, disappears, or changes amount →
    `USAGE_OVERAGE`;
  - a tax movement that is not already inside another named cause →
    `TAX_CHANGE`;
  - an adjustment line appears, disappears, or changes amount →
    `BILLING_ADJUSTMENT`;
  - an activation or installation line on one invoice only (the known eir
    example is "Broadband activation fee") → `ACTIVATION_FEE`;
  - a one-off line whose description contains "bundle", on one invoice only
    (the known eir example is "1GB USA/Canada Roaming Bundle") → `ONE_OFF_BUNDLE`.
- `DEVICE_INSTALLMENT_ENDED` and `LATE_PAYMENT_FEE` exist in the catalogue and
  fire only against an explicit label list. With the list empty, those lines
  stay `UNEXPLAINED`. Do not match `mora`, `atrasad`, or any other unconfirmed
  token.
- Do not emit `CHANGE_CYCLE` from invoice lines. A prorata that belongs to a
  service which just appeared is `ADDON_ADDED`. Any other prorata stays
  `UNEXPLAINED`.
- One FR phrase and one EN phrase per voiced cause, in the existing explanation
  composer. Amounts stay the comparison's amounts.
- The cause code already carried on the billing explanation (logs / telemetry)
  uses the new code. No new personal data.

## Out of scope

- A Siebel history feed or a CRM `CYCLE_CHANGE` event.
- Guessing device-installment or late-fee labels. Filling that list is a
  missing input (below), not part of this ticket.
- A same-label price drop, an in-place option price change, and a discount
  whose amount changes on the same line. Those stay `UNEXPLAINED`.
- Letting the LLM choose the cause.

## Acceptance

- Each in-scope rule has a domain test that attributes the delta to that cause
  and leaves the amount out of the residual.
- A subscription price rise with an unchanged label is `TARIFF_INCREASE`, not
  `UNEXPLAINED`.
- Adding eir TV or eir Mobile on the existing eir B2C samples is `ADDON_ADDED`
  (base line and that service's prorata), not `PLAN_CHANGE`, and the residual
  for those explained lines stays zero.
- "Broadband activation fee" on the current invoice only is `ACTIVATION_FEE`.
- A discount present on the previous invoice and absent now is `DISCOUNT_ENDED`.
- A discount absent before and present now is `DISCOUNT_ADDED`.
- A tax line that only follows another named cause is not also `TAX_CHANGE`.
- An empty late-fee and installment label list does not classify any current
  eir sample line as `LATE_PAYMENT_FEE` or `DEVICE_INSTALLMENT_ENDED`.
- No sample asserts `SPEED_UPGRADE` or `CHANGE_CYCLE`.
- FR and EN wording covers every voiced cause. `UNEXPLAINED` is not spoken as
  a cause.
- Backend unit tests and ArchUnit are green. The story is runtime-affecting:
  the spoken cause and the existing cause code on the billing explanation change.

## Missing inputs

- Confirmed eir description labels for a late-payment fee and for a device
  installment. Until they exist, those two causes do not fire.
- A speed token on the home-internet line. Current eir B2C samples do not have
  "Internet N megas"; `SPEED_UPGRADE` stays unused on those samples.

## Open questions

- A same-label price **drop**, an option whose price changes in place, and a
  discount that **starts** are not in the catalogue. They stay `UNEXPLAINED`.
  Product names them only if an explanation is required.
