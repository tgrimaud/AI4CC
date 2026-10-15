# Adversarial Code Review — TASK-BE-073 (invoice-difference cause catalogue)

- **Scope:** `task/TASK-BE-073-invoice-difference-causes` (uncommitted working tree)
- **Change:** ADR-0057 catalogue, including `DISCOUNT_ADDED`, the rolled-off add-on on 99226337, and `ONE_OFF_BUNDLE` for a one-off whose description contains "bundle" (99224964, +29.99 €).
- **Reviewer:** adversarial-code-review skill
- **Date:** 2026-10-15 (third pass, after `ONE_OFF_BUNDLE`)

## Verdict

Proceed.

## Satisfaction Score

Score: 95/100
QA gate: Pass

## Blocking Findings

| Severity | Finding | Evidence | Required fix |
|---|---|---|---|
| — | None. The three eir B2C accounts explain with residual 0. 99224964 speaks the new options and the one-off bundle and does not escalate. | `mvn test` 797 tests, 0 failures, including ArchUnit | — |

## Non-Blocking Findings

| Severity | Finding | Evidence | Recommendation |
|---|---|---|---|
| Low (fixed) | A one-off bundle on the previous invoice only is named and the drop is explained (−29.99 €, residual 0). | `a_one_off_bundle_on_the_previous_invoice_only_is_named` | None. |
| Low | "bundle" is a substring match, same style as "activation". A one-off that mentions the word for another reason would be named. Activation still wins when the label also says activation or installation. | `InvoiceLineText.oneOffBundle`; `InvoiceCauseAttributor.oneOff` | Leave. The 15GB Bundle stays an add-on because it is an option, not a one-off. The PDF e2e locks that split (+25.48 € add-on, +29.99 € one-off bundle). |
| Medium | Replacing one subscription while another subscription code stays is `ADDON_REMOVED` + `ADDON_ADDED`, not `PLAN_CHANGE`. That is how a new TV beside a staying offer stays an add-on. The same rule misses a fibre-offer rename on a fibre+TV account. | `InvoiceCauseAttributor.subscription` | Accept for V1. |
| Low | On 99226337 the two add-on amounts share one phrase. The customer hears "l'ajout d'un nouveau service" for −1.94 € and again for +101.82 €. The −1.94 € is the TV partial period leaving the bill (2.58 € and −0.64 €, netted). | `AddonFamilies.impacts`; smoke test asserts `101.82` and `1.94` | Acceptable under one phrase per cause. |
| Low | English speech is locked for 99226126 only. 99224964 and 99226337 are locked in French. | `BillingCauseCatalogueSmokeTest` | QA can spot-check English. The phrase table already covers every cause in both languages. |
| Info | `InvoiceCauseAttributor` is 200 non-blank lines, on the class cap. | class non-blank count | Split before the next cause rule. |

## Story Coverage

| Acceptance criterion | Covered? | Evidence |
|---|---|---|
| Each in-scope rule has a domain test, and the attributed amount is out of the residual | Yes | Tariff increase, speed upgrade, add-on add/remove, discount ended, discount added, usage appear, tax alone, tax absorbed, adjustment, activation both directions, late fee, installment, prorata of a new service, lone appeared prorata, renamed offer, rolled-off partial period, one-off bundle appear, plain one-off stays unexplained. A same-line discount amount change stays unexplained. |
| Same subscription label, higher amount → `TARIFF_INCREASE` | Yes | `a_same_label_subscription_price_rise_is_a_tariff_increase`; `EirBssBillingAdapterTest.fetchInvoice_recurringPriceRise_isATariffIncreaseNotAPlanChange` |
| eir TV / eir Mobile → `ADDON_ADDED`, including prorata, residual 0 | Yes | E2E 99226126 +41.31 €. E2E 99226337 +99.88 € (mobile +101.82 € and TV partial period −1.94 €). |
| "Broadband activation fee" on one invoice only → `ACTIVATION_FEE` | Yes | E2E 99226337 −49.99 €. Unit tests cover both directions. |
| Discount present before and absent now → `DISCOUNT_ENDED`, except the same-window prorata discount | Yes | `a_discount_that_ends_is_not_part_of_a_staying_add_on`. A different window and another service stay `DISCOUNT_ENDED`. The 99226337 −0.64 € line stays inside the add-on. |
| Discount absent before and present now → `DISCOUNT_ADDED` | Yes | Unit test −5.00 €; E2E 99226126 −7.83 €; E2E 99226337 −15.66 €. |
| One-off whose description contains "bundle" → `ONE_OFF_BUNDLE` | Yes | Unit tests cover both directions (+29.99 € and −29.99 €, residual 0). E2E 99224964 +29.99 € residual 0. Smoke speaks "bundle ponctuel" and "29.99" and does not escalate. |
| Tax that only follows another cause is not also `TAX_CHANGE` | Yes | `a_tax_line_that_follows_another_cause_is_not_voiced_as_tax` |
| Empty late-fee and installment lists do not fire on current eir samples | Yes | Default `InvoiceComparisonService()` in the e2e. |
| No sample asserts `SPEED_UPGRADE` or `CHANGE_CYCLE` | Yes | Speed upgrade is a synthetic domain test. No path emits `CHANGE_CYCLE`. |
| FR and EN wording for every voiced cause; `UNEXPLAINED` is not spoken | Yes | `every_voiced_cause_has_a_french_and_an_english_phrase` includes "bundle ponctuel" / "one-off bundle". |
| Backend unit tests and ArchUnit green | Yes | `mvn test`: 797 tests, 0 failures, 0 errors, 0 skipped. |

## Test Evidence

- Developer tests: `InvoiceComparisonServiceTest`, `BillingCauseCatalogueSmokeTest` (three accounts explained in French, 99226126 also in English, unknown reference, non-billing question), `EirB2cBillingComparisonE2eTest`, `BillingExplanationComposerTest`, `BillingExplanationServiceTest`, `ComparisonConfidenceServiceTest`, `InProcBillingExplanationAdapterTest`, `EirBssBillingAdapterTest`.
- Missing tests: English speech for 99224964 and 99226337.
- QA scenarios to run: August vs September for the three accounts. Expect a spoken explanation and no advisor. 99226126: +41.31 € once. 99226337: bill +34.23 €, TV partial period −1.94 €, mobile +101.82 €, discounts −15.66 €, activation fee −49.99 €. 99224964: bill +55.47 €, new options +25.48 €, one-off bundle +29.99 €. A same-label price rise is a tariff increase. A same-label price drop escalates.

## Observability And Latency

- Relevant slices: `billing` (identity → invoices → comparison → confidence → grounded text). Comparison stays in-process.
- OpenTelemetry traces: unchanged. The billing latency span still wraps the explain call.
- Metrics: `Slices.BILLING` latency by provider `billing`, outcome and reason. No cause-code tag.
- Structured logs: `[BILLING]` records `outcome`, comma-separated cause enum names, and `correlation_id`. Codes outside `[A-Z0-9_]+` are dropped. Amounts, labels and the spoken text stay off that line.
- Missing: one `ADDON_ADDED` log code when two add-on amounts are spoken. QA reads the amounts from the explanation text.
- Risk: low. Escalated turns still log the attached cause codes.

## Security And Privacy

- Sensitive data risk: attribution reads labels already on the invoice. No new persistence and no BSS write.
- Identity/access risk: none. Comparison does not resolve identity.
- Logging risk: `[BILLING]` writes enum names only. The phrase "un bundle ponctuel" contains the amount and is not logged.

## Required Developer Actions

1. None before QA.

## Residual Risk If Accepted

- On fibre+TV, renaming the fibre offer is spoken as a service removed plus a service added, as long as the TV subscription code stays.
- A one-off that contains the word "bundle" for another reason is named `ONE_OFF_BUNDLE`.
- `CHANGE_CYCLE` is never emitted. `SPEED_UPGRADE`, late-fee and device-installment causes stay dark on the current samples.
- A same-label price drop, an in-place option price change, and a discount whose amount changes on the same line stay `UNEXPLAINED`.
- 99226337 speaks the TV partial period as "l'ajout d'un nouveau service" for −1.94 €, without naming eir TV or the mobile offer.
