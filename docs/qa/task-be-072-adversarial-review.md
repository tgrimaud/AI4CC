# TASK-BE-072 — Adversarial code review

**Ticket:** TASK-BE-072 — Make the billing clarify sub-types a configurable, enrichable list (OQ-043-b)
**Story / ADR:** US-043 / ADR-0056 (sub-type steering amendment)
**Branch:** `task/TASK-BE-072-configurable-billing-clarify-sub-types` (Sprint 17)
**Reviewed commit:** `5cf5c71`
**Date:** 2026-10-13
**Reviewer:** adversarial-code-review skill

## Verdict

Proceed.

## Satisfaction Score

Score: 93/100
QA gate: Pass

## Blocking Findings

| Severity | Finding | Evidence | Required fix |
|---|---|---|---|
| — | None | — | — |

## Non-Blocking Findings

| Severity | Finding | Evidence | Recommendation |
|---|---|---|---|
| Low | Prompt-rule tension: the base `CLARIFY_SYSTEM_PROMPT` says "do NOT list several options" while the appended steering block permits "a brief choice among two or three". The steering is recency-after the base, so it should win, but the signals conflict within one prompt. | `AbstractChatClientAnswerAdapter.CLARIFY_SYSTEM_PROMPT` vs `AnswerLanguage.clarifySubTypeDirective` | Tune the base prompt wording ("do not enumerate the whole list" instead of "no options") in a later pass; behaviour is backstopped by the single-question clarify directive and is a wording risk only. |
| Low | A deployment could configure a sub-type hint that contains an amount (e.g. "a €10 charge"); that text would enter the prompt. | config `sub-types[].fr/en` are free text | Documented that hints must be amount-free; the `OutputGuardrail` vets the final question and drops any voiced amount → safe hand-off, so DEC-002 holds even on a misconfigured hint (defense-in-depth). |
| Info | `AbstractChatClientAnswerAdapter` stays above the 200-line class budget (+4 lines modifying the existing method, not a new method). | pre-existing size since TASK-BE-053/068 | No new method added; revisit in a dedicated size pass if it grows further. |
| Info | The decorator constructor now takes 11 params (wiring constructor). | `BillingDiagnosticConversationService` ctor | Acceptable for a config wiring seam; a parameter object could be introduced if it keeps growing. |

## Story Coverage

| Acceptance criterion | Covered? | Evidence |
|---|---|---|
| Adding/removing/relabelling a sub-type is config only (FR/EN) | Yes | `BillingClarifyProperties` binds `voice-support.conversation.billing-clarify.sub-types`; 7 starter entries in `application.yml`; `BillingClarifyPropertiesTest` binds + maps + drops id-less entries |
| Clarify steered by configured sub-types, single natural question, DEC-002 (no amount) | Yes | `clarifySubTypeDirective` offers ≤2-3 inline, clarify directive keeps ONE question; `ClarifyingQuestionAdapterTest.subTypeHintsSteerThePrompt`; `OutputGuardrail` vetting unchanged (amount → hand-off) |
| Empty/absent config ⇒ identical to TASK-BE-071 | Yes | empty hints ⇒ no steering block appended (`AbstractChatClientAnswerAdapter`); `no_sub_types_passes_empty_hints`; `absentListIsEmpty` |
| Understood sub-type appears in escalation context | **Deferred** | needs a classifier + escalation-assembly change — documented as a follow-up on the ticket + ADR-0056 residual |
| `mvn test` green incl. ArchUnit; pure domain Spring-free; manual fakes; no Mockito | Yes | suite **777 green**; `BillingClarifyTopic` is a pure record; `@ConfigurationProperties` lives in infrastructure |
| Sub-type steering telemetry present and asserted | Yes | `BackendTelemetry.recordBillingClarify(...,subTypes)` adds a `sub_types` count tag + log; `records_sub_type_count_telemetry` |

## Test Evidence

- Developer tests: `BillingDiagnosticConversationServiceTest` (+3: hints passed, empty hints, `sub_types` telemetry), `ClarifyingQuestionAdapterTest` (+1 steering + 3 migrated to 4-arg), `BillingClarifyPropertiesTest` (3 Binder tests: bind+map, absent→empty, drop id-less), updated BDD glue + wiring fakes to the new port signature. Suite 777 green incl. ArchUnit.
- Missing tests: none blocking. A live-LLM smoke (does the spoken clarify actually offer sub-types) is a QA task, not unit-assertable with a fixed fake.
- QA scenarios to run: live `/converse` turn on a vague billing opener (FR + EN) → the clarify question offers 2-3 of the configured sub-types; confirm no amount is voiced; `billing_clarify sub_types` tag > 0 in telemetry.

## Observability And Latency

- Relevant slices: `llm_wording` (the clarify LLM call is timed on it, same bounded-call + timeout budget as answers).
- OpenTelemetry traces: unchanged; backend continues the derived cross-tier trace.
- Metrics: `voice_support.billing_clarify` now carries `sub_types` (0..N bounded count) alongside `event`/`language`/`channel`.
- Structured logs: `[BILLING-CLARIFY] … sub_types=… correlation_id=…` — technical dimensions only, no transcript/question text.
- Missing: none for this change.
- Risk: low — bounded tag cardinality; no PII added.

## Security And Privacy

- Sensitive data risk: none — hints are operator config text; no customer data enters metrics/logs.
- Identity/access risk: none — no identity/BSS path touched.
- Logging risk: none — only the integer `sub_types` count is logged.

## Required Developer Actions

1. None blocking. (Optional) soften the base clarify prompt wording in a later pass to remove the "no options" tension.

## Residual Risk If Accepted

- Sub-type → escalation-tag detection is **deferred** (config-only steering shipped). Accepted: the pilot win of this increment is a more relevant clarify question; carrying the understood sub-type into the advisor hand-off needs a deterministic classifier + escalation-assembly change, tracked as a follow-up on TASK-BE-072.
- Prompt-rule wording tension (Low) accepted — behaviour is backstopped by the single-question directive and the DEC-002 OutputGuardrail.
