# QA Functional And Latency Report — TASK-BE-072 (configurable billing clarify sub-types)

## Executive Summary

- **Overall readiness:** GO (functional). Config-driven billing clarify **sub-type steering** (OQ-043-b)
  is validated at domain, adapter, config-binding and product-acceptance (BDD) levels. Backend suite
  **778 green** incl. ArchUnit and the Cucumber runner.
- **Main blockers:** none.
- **Residual risks:** sub-type → escalation-tag detection is **deferred** (documented follow-up); the
  live-LLM "the spoken clarify actually offers sub-types" is a post-deploy smoke (the feature is not yet
  released to the pilot). Low prompt-wording tension accepted (backstopped by the single-question
  directive + DEC-002 OutputGuardrail).

## Scope Tested

- Epics / stories: EPIC-005 / US-043 (ADR-0056 sub-type steering amendment), TASK-BE-072.
- Channels: backend `/converse` + `/converse-stream` (channel-agnostic clarify flow).
- Providers / fakes: fake `ClarifyingQuestionGeneratorPort` (deterministic), real `OutputGuardrail`,
  `ProblemOpenerDetector`, `LanguageDetector`, `BackendTelemetry` (SimpleMeterRegistry), real conversation
  memory; Spring Boot `Binder` for the config properties.
- Environment: `mvn test` (no DB/Ollama; no `@SpringBootTest`).

## Functional Results

| Area | Status | Evidence | Notes |
|---|---|---|---|
| Configured sub-types steer the single clarify question | ✅ Pass | BDD `The clarifying question narrows among the configured billing sub-types`; `BillingDiagnosticConversationServiceTest.sub_types_steer_the_clarify_question`; `ClarifyingQuestionAdapterTest.subTypeHintsSteerThePrompt` | Hints resolved in session language, woven into ONE question |
| Empty/absent config ⇒ generic TASK-BE-071 clarify | ✅ Pass | `no_sub_types_passes_empty_hints`; `BillingClarifyPropertiesTest.absentListIsEmpty`; existing clarify scenarios unchanged | Regression-safe default |
| DEC-002 preserved on a steered clarify (no amount) | ✅ Pass | BDD `the response contains no amount` on the steered scenario; `OutputGuardrail` vetting unchanged | A hint carrying an amount would still be blocked → hand-off |
| Config is add/remove/relabel only (FR/EN), drops malformed entries | ✅ Pass | `BillingClarifyPropertiesTest` (bind+map, drop id-less); 7 starter sub-types in `application.yml` | No code change to add a sub-type |
| Steering observable in telemetry | ✅ Pass | `records_sub_type_count_telemetry` (`sub_types` tag); `[BILLING-CLARIFY] … sub_types=…` log | Bounded count tag (0..N) |
| Bound / cap / BR5 / language behaviour unchanged | ✅ Pass | existing `billing-clarify.feature` scenarios + service tests all green | No regression on TASK-BE-070/071 behaviour |

## Latency Results

| Slice | p50 | p95 | p99 | Sample | Warm/Cold | Notes |
|---|---:|---:|---:|---:|---|---|
| LLM wording (clarify) | n/m | n/m | n/m | 0 | — | Not re-measured on the pilot: the feature is **not yet released**. The clarify call stays on the existing `llm_wording` slice (TASK-BE-071 pilot baseline ≈ 0.86 s p50). The only change is a modest prompt-size increase (the sub-type steering block, ≈ a few hundred chars for the 7-item set) — no new slice, no new network hop, well within the prefill budget. |

Post-deploy QA action: run one FR + one EN vague-billing-opener `/converse` turn on the pilot after the
release, confirm the spoken clarify offers 2-3 configured sub-types, no amount is voiced, and re-read the
`llm_wording` clarify slice to confirm no regression vs the TASK-BE-071 baseline.

## Component Findings

| Brick | Status | Findings | Next action |
|---|---|---|---|
| Domain `BillingClarifyTopic` | ✅ | Pure record, `hint(language)` FR/EN | — |
| LLM clarify adapter steering | ✅ | Steering block appended before the recency-last directive; empty hints ⇒ generic prompt | Optional base-prompt wording tune (Low) |
| `BillingClarifyProperties` binding | ✅ | Relaxed binding of the list; drops id-less entries; empty on absence | — |
| Telemetry | ✅ | `sub_types` count tag + log; clarify timed on `llm_wording` | Re-measure on pilot post-deploy |

## Defects And Gaps

| Severity | Finding | Impact | Owner |
|---|---|---|---|
| Low | Base `CLARIFY_SYSTEM_PROMPT` "do not list options" vs the steering block's "brief choice" tension | Prompt-wording only; backstopped by the single-question directive + guardrail | Backend (optional later tune) |
| Info | Sub-type → escalation-tag detection deferred | No tag in advisor handoff yet | TASK-BE-072 follow-up |

## Open Questions

- Product: none (OQ-043-b resolved — 7 configurable sub-types confirmed).
- Architecture: none (ADR-0056 updated).
- Technical: none blocking.

## Recommendation

- **Go / No-go:** GO for functional acceptance (778 green, review 93/100 Pass).
- **Required fixes before pilot:** none. Post-deploy: run the live FR/EN clarify smoke + re-read the
  `llm_wording` clarify slice.
