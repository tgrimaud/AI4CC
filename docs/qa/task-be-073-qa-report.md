# QA Functional And Latency Report — TASK-BE-073

Ticket: TASK-BE-073 — invoice-difference cause catalogue (ADR-0057).
Branch: `task/TASK-BE-073-invoice-difference-causes`.
Date: 2026-10-15. QA owner: qa-functional-latency.
Adversarial review: 95/100 Pass (`docs/qa/task-be-073-adversarial-review.md`).

## Executive Summary

- **Overall readiness: GO** for the catalogue on the three eir B2C accounts. Each customer hears the confirmed causes and is not transferred. An unknown reference is transferred with no amount. A non-billing question does not speak invoice causes.
- **Main blockers:** none.
- **Residual risks:** a same-label price drop, an in-place option price change, and a discount whose amount changes on the same line stay unexplained and can still escalate. A one-off that is not a bundle, an activation fee, a late fee, or an ended installment stays unexplained. This run is not a voice pilot and not a live BSS call.

## Scope Tested

- **Epics / stories:** EPIC-004, TASK-BE-073.
- **Channels:** text explain through the answer engine (`BillingAnswerService`, channel `web`, DEC-002 guardrail). The same chain backs voice. No WebRTC, no Genesys, no phone.
- **Providers / fakes:** in-memory BSS over `EirB2cSampleFixtures`; real PDFBox extraction for the amount check. The language model is a fake that repeats the computed explanation, so the guardrail sees only grounded amounts. Customer directory is the in-memory adapter. Confidence gate is the default 5%.
- **Environment:** local `mvn -Dtest=RunKnowledgeBddTest,EirB2cBillingComparisonE2eTest test`. No DB, no Ollama, no network. Exit 0.

## Functional Results

| Area | Status | Evidence | Notes |
|---|---|---|---|
| 99226126, French | Pass | `invoice-difference-causes.feature` — new TV offer | Hears 66.51 €, new service 41.31 €, discount 7.83 €, activation fee 99.99 €. Not transferred. Log: `explained` / `ADDON_ADDED,DISCOUNT_ADDED,ACTIVATION_FEE`. |
| 99226126, English | Pass | same feature — English scenario | Hears "new service", "start of a discount", "activation fee". Not transferred. |
| 99224964, French | Pass | same feature — mobile customer | Hears 55.47 €, options 25.48 €, one-off bundle 29.99 €. Not transferred. Log: `ADDON_ADDED,ONE_OFF_BUNDLE`. |
| 99226337, French | Pass | same feature — fibre and TV customer | Hears 34.23 €, 101.82 €, 1.94 €, 15.66 €, 49.99 €. Does not hear "fin d'une remise". Not transferred. |
| Unknown reference | Pass | same feature | Transferred. No euro amount. Language model not called. Log: `identity_unresolved`. |
| Non-billing question | Pass | same feature | Not transferred. Language model not called. The 41.31 € add-on is not spoken. Log: `not_a_billing_request`. |
| Real PDF amounts | Pass | `EirB2cBillingComparisonE2eTest` | August vs September. Residual 0 on 99226126, 99224964 and 99226337. |

## Latency Results

Not a pilot sample. In-memory explain and local PDF extraction only. Voice slices (ingress, end-of-turn, STT, TTS, egress, Genesys) are out of scope and not measured.

| Slice | p50 | p95 | p99 | Sample | Warm/Cold | Notes |
|---|---:|---:|---:|---:|---|---|
| BSS/PDF evidence | 10 ms | 108 ms | 108 ms | 9 | Cold first extract 108 ms, then 0–15 ms | `bss` slice, provider `pdf`, outcome `success`. Min 0, max 108, mean 18 ms. |
| Billing explain | 0 ms | 2 ms | 2 ms | 6 | First call 2 ms, then 0 ms | `billing` slice, provider `billing`. In-memory invoices. Includes comparison. Min 0, max 2, mean 0.3 ms. |
| Deterministic comparison | — | — | — | — | — | Inside the billing slice. No separate span. |
| LLM wording | — | — | — | — | — | Fake echo of the computed text. Not a provider call. |
| RAG, STT, TTS, channel, Genesys | — | — | — | — | — | Not on this path. |

## Component Findings

| Brick | Status | Findings | Next action |
|---|---|---|---|
| Answer engine | Pass | The six Gherkin scenarios pass through identity, comparison, confidence and the output guardrail. | None for this ticket. |
| PDF comparison | Pass | Cause amounts on the three real bills match the catalogue, residual 0. | None. |
| Cause log | Pass | `[BILLING]` records the outcome and the cause enum names, with `correlation_id`. No amount on that line. | None. |

## Defects And Gaps

| Severity | Finding | Impact | Owner |
|---|---|---|---|
| — | No defect in this run. | — | — |

## Open Questions

- **Product:** none opened by this run. A same-label price drop and a one-off that is not a bundle stay unexplained until Product names them.
- **Architecture:** none.
- **Technical:** none. The run does not boot Spring and does not call a live BSS.

## Recommendation

- **Go** for TASK-BE-073 catalogue acceptance.
- **Required fixes before pilot:** none from this run. Voice, live BSS and Genesys handoff are outside this ticket.
