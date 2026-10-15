# QA Functional And Latency Report — TASK-BE-073 smoke

Superseded by the acceptance report `docs/qa/task-be-073-qa-report.md` (2026-10-15).
The smoke below was the earlier in-process pass. The acceptance run speaks the
three accounts through the answer engine.

Ticket: TASK-BE-073 — invoice-difference cause catalogue (ADR-0057).
Branch: `task/TASK-BE-073-invoice-difference-causes`.
Date: 2026-10-15. QA owner: qa-functional-latency.
This is a smoke of the gated explain path and of the real eir B2C PDF comparison.
It is not a voice pilot and not a full regression.

## Executive Summary

- **Overall readiness: GO** for the catalogue smoke. The three real accounts are spoken: 99226126, 99224964 and 99226337. The guards fail closed.
- **Main blockers: none.**
- **Residual risks:** a discount whose amount changes on the same line stays `UNEXPLAINED`. A one-off that is not a bundle, an activation fee, a late fee, or an ended installment stays `UNEXPLAINED`.

## Scope Tested

- **Epic / ticket:** EPIC-004, TASK-BE-073.
- **Channel:** text explain use case (`BillingExplanationQuery`, channel `web`). The same chain backs voice. No WebRTC, no Genesys, no LLM call.
- **Providers / fakes:** in-memory BSS over `EirB2cSampleFixtures` for the spoken path; real PDFBox extraction for the amount check (`EirB2cBillingComparisonE2eTest`). Customer directory is the in-memory adapter. Confidence gate is the default 5%.
- **Environment:** `mvn -Dtest=BillingCauseCatalogueSmokeTest,EirB2cBillingComparisonE2eTest test` on the ticket branch. No DB, no Ollama, no network. Exit 0.

## Functional Results

| Area | Status | Evidence | Notes |
|---|---|---|---|
| 99226126, French | Pass | `account_99226126_is_spoken_in_french` | Explained, no escalation. Spoken: decreased by 66.51 €, new service +41.31 €, discount starting −7.83 €, activation fee −99.99 €. Causes `ADDON_ADDED`, `DISCOUNT_ADDED`, `ACTIVATION_FEE`. |
| 99226126, English | Pass | `account_99226126_is_spoken_in_english` | Same three causes: "new service", "start of a discount", "activation fee". |
| 99224964, French | Pass | `account_99224964_explains_the_new_options_and_the_one_off_bundle` | Explained, no escalation. Spoken: bill up 55.47 €, new options +25.48 €, one-off bundle +29.99 €. Causes `ADDON_ADDED`, `ONE_OFF_BUNDLE`. |
| 99226337, French | Pass | `account_99226337_explains_the_tv_partial_period_and_the_new_mobile` | Explained, no escalation. Spoken: bill up 34.23 €, TV partial period net −1.94 €, new mobile +101.82 €, new discounts −15.66 €, activation fee −49.99 €. The 2.58 € prorata and the −0.64 € discount prorata are one add-on, not a discount that ended. |
| Unknown reference | Pass | `an_unknown_reference_is_handed_to_an_advisor` | `IDENTITY_UNRESOLVED`, escalates, no cause codes. |
| Non-billing question | Pass | `a_non_billing_question_is_redirected` | `NOT_A_BILLING_REQUEST`, no escalation, catalogue not applied. |
| Real PDF amounts | Pass | `EirB2cBillingComparisonE2eTest` | August vs September on the three accounts. Residual 0 on 99226126, 99224964 and 99226337. |

## Latency Results

| Slice | p50 | p95 | p99 | Sample | Warm/Cold | Notes |
|---|---:|---:|---:|---:|---|---|
| BSS/PDF evidence | 10 ms | 113 ms | 113 ms | 9 | Cold first extract 113 ms, then 0–14 ms | `bss` slice, provider `pdf`, outcome `success`, from the PDF e2e log. Not a pilot SLO. |
| Deterministic comparison | — | — | — | — | — | In-process. Not a separate span on this path. |
| LLM wording | — | — | — | — | — | Not on this path. The composer writes the grounded text (DEC-002). |
| STT / TTS / channel / Genesys | — | — | — | — | — | Out of smoke scope. |

## Component Findings

| Brick | Status | Findings | Next action |
|---|---|---|---|
| Explain use case | Pass | Spoken text matches the named causes on the three accounts. Escalated turns use the canned hand-off and still carry cause codes. | None for this smoke. |
| PDF comparison | Pass | Amounts match the catalogue rules on the three real bills. | None. |
| One-off bundle | Pass | "1GB USA/Canada Roaming Bundle" is `ONE_OFF_BUNDLE` (+29.99 €), not usage. | None. |

## Defects And Gaps

| Severity | Finding | Impact | Owner |
|---|---|---|---|
| — | No new defect in this smoke. | — | — |

## Open Questions

- **Product:** none opened by this smoke. A one-off bundle is `ONE_OFF_BUNDLE`.
- **Architecture:** none opened by this smoke.
- **Technical:** none. The smoke does not boot Spring and does not call a live BSS.

## Recommendation

- **Go** for the TASK-BE-073 catalogue smoke.
- **Required fixes before pilot:** none from this run. Voice and live BSS are outside this smoke.
