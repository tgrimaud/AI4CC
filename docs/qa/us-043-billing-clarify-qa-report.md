# QA Functional And Latency Report — US-043 (billing clarify, increments D + C)

Date: 2026-10-08 · Branch: `feat/sprint-16-billing-clarify` · Tickets: TASK-BE-070 (D), TASK-BE-071 (C)

## Executive Summary

- **Overall readiness:** GO for functional acceptance of US-043 on the backend conversation flow.
  All six product-acceptance scenarios are automated and green; the full backend suite is **758
  tests, 0 failures** (incl. ArchUnit and the 48-scenario Cucumber BDD suite).
- **Main blockers:** none.
- **Residual risks:** (a) latency confirmed on the pilot (2026-10-12) — clarify turn ~0.86 s p50
  (backend+LLM, no retrieval) vs ~1.69 s for a normal answer turn, no regression; end-to-end voice
  mouth-to-ear still rides the unchanged STT/TTS slices (voice-runtime report);
  (b) on the pilot `/converse` has no live BSS, so clarify improves retrieval/escalation context,
  not amount accuracy (DEC-002 unchanged); (c) memory-derived clarify streak resets on an
  interleaved non-opener turn (by design, ADR-0056).

## Scope Tested

- **Epics / stories:** EPIC-005 / US-043 (ask a clarifying question before answering a billing
  problem), via TASK-BE-070 (deterministic single clarify, one question at a time) and TASK-BE-071
  (bounded, LLM-worded, configurable clarify dialogue).
- **Channels:** backend conversation flow for both the blocking (`/converse`, text) and streaming
  (`/converse-stream`, web voice) paths — validated through the use-case ports, not the live media
  layer.
- **Providers / fakes:** fake converse delegates, fake `ClarifyingQuestionGeneratorPort`, with the
  **real** `ProblemOpenerDetector`, `OutputGuardrail`, `LanguageDetector`, `ConversationMemoryPort`
  (in-memory adapter) and `BackendTelemetry` (SimpleMeterRegistry). No live LLM/STT/TTS.
- **Environment:** `mvn -o test` (JUnit 5 + Cucumber-JVM), no DB / no Ollama (no `@SpringBootTest`).

## Functional Results

| Area (US-043 acceptance) | Status | Evidence | Notes |
|---|---|---|---|
| Under-specified billing problem → one short question, no enumeration (BR1/BR2/BR4) | ✅ Pass | `billing-clarify.feature` S1; `BillingDiagnosticConversationServiceTest.first_billing_opener_asks_llm_clarify`; D: `conversation-grounding.feature` "under-specified billing opener with a weak marker is clarified" | Clarify produced before any retrieval. |
| Clarifying dialogue is bounded → then answer-or-advisor, no further question (BR3) | ✅ Pass | `billing-clarify.feature` S2; `bounds_clarifies_then_escalates`, `bound_is_configurable` | Cap → advisor hand-off with collected context (ADR-0019). |
| Specific/answerable billing question answered directly (BR5) | ✅ Pass | `billing-clarify.feature` S3; `answerable_billing_opener_proceeds`; D: `conversation-grounding.feature` "specific billing problem still reaches retrieval" | Amount-anchored opener bypasses the clarify. |
| Explicit human request not intercepted (BR5) | ✅ Pass | `billing-clarify.feature` S4; `human_request_proceeds` | Flows to the escalation path, not the clarify flow. |
| Clarifying wording follows session language FR/EN (BR6) | ✅ Pass | `billing-clarify.feature` S5; `ClarifyingQuestionAdapterTest.nullLanguageFallsBackToEnglish`; `AnswerLanguageTest` clarify directive | Decorator resolves `AnswerLanguage` and passes it to the generator. |
| No invented amount in a clarifying question (BR4 / DEC-002) | ✅ Pass | `billing-clarify.feature` S6; `clarify_voicing_an_amount_is_blocked` | An amount-bearing clarify is DEC-002-vetted out → safe hand-off. |
| Configurable max questions (OQ-043-a, default 2; `<=0` disables) | ✅ Pass | `bound_is_configurable`, `disabled_when_max_non_positive`; `application.yml` `billing-clarify.max-questions:2` | Env `CONVERSATION_BILLING_CLARIFY_MAX_QUESTIONS`. |
| Streaming path parity (web voice) | ✅ Pass | `streaming_billing_opener_emits_clarify`, `streaming_non_opener_delegates` | Clarify emitted as one chunk; non-opener delegates to the stream. |

## Latency Results

Measured on the **eir-ai4cc-tst pilot** (2026-10-12, v0.9.6), backend `/api/conversation/converse`
HTTP round-trip from a backend host (`vla-ai4cc-t03`, loopback `:8080`), warm, LLM provider
`mistral-api`. Backend+LLM slice only — STT/TTS/channel slices are unchanged by this story and are
reported by the voice-runtime streaming latency report.

| Turn type | p50 | p95 | min–max | Sample | Warm/Cold | Notes |
|---|---:|---:|---:|---:|---|---|
| Clarify turn (LLM wording, **no retrieval**) | ~0.86 s | ~0.96 s | 0.75–0.96 s | 6 | Warm | US-043 clarify (billing opener). |
| Cap escalation (no LLM) | — | — | ~0.012 s | 1 | Warm | Deterministic hand-off at the cap; no provider call. |
| Normal answer turn (retrieval + LLM) | ~1.69 s | ~2.65 s | 1.60–2.65 s | 6 | Warm | Amount-anchored billing question (RAG baseline). |

Latency verdict: **GO**. A clarify turn is ~2× faster than a normal answer turn because it skips
retrieval, and the cap escalation is effectively instant (no provider call). The clarify dialogue
therefore **does not regress** the backend latency budget — confirmed live on the pilot. End-to-end
voice mouth-to-ear still rides the unchanged STT/TTS slices (voice-runtime report).

## Component Findings

| Brick | Status | Findings | Next action |
|---|---|---|---|
| `ProblemOpenerDetector` (trigger) | ✅ | Deterministic, accent-folded, word-boundary; billing opener vs anchored question; escalation short-circuit | — |
| `BillingDiagnosticConversationService` (decorator) | ✅ | Memory-derived streak, PROCEED/ASK_CLARIFY/ESCALATE_AT_CAP, both converse ports | — |
| `ClarifyingQuestionGeneratorPort` + LLM adapter | ✅ | No-RAG ask-one-question prompt, clarify directive recency-last, DEC-002-vetted | — |
| `OutputGuardrail` (DEC-002 on clarify) | ✅ | Empty-evidence check drops amount-bearing clarify | — |
| Observability | ✅ | `voice_support.billing_clarify` (asked/cap_escalated, tags language+channel), clarify timed on `llm_wording` | Confirm meter cardinality on the pilot collector |

## Defects And Gaps

| Severity | Finding | Impact | Owner |
|---|---|---|---|
| ~~Low~~ Resolved | Clarify-turn latency measured on the pilot (2026-10-12): ~0.86 s p50 backend+LLM, ~2× faster than a normal answer turn (no retrieval); cap escalation ~0.012 s | No voice-SLO regression | Closed |
| Low | Pilot clarify improves retrieval/escalation context only, not amounts (no live BSS) | Expected per US-043 scope; DEC-002 unchanged | Product / OQ-002 |
| Low | Per-sub-type clarify (increase / unknown charge / promo expiry) not specialised | Single generic clarify prompt | Product / OQ-043-b |

## Open Questions

- **Product:** OQ-043-b (configurable billing sub-types for the clarify) — still open.
- **Architecture:** OQ-043-c resolved by ADR-0056; OQ-002 (live BSS feeding the collected context)
  remains for the post-pilot increment.
- **Technical:** confirm the clarify-turn latency distribution on the pilot vs a normal answer turn.

## Recommendation

- **Go / No-go:** **GO** for functional acceptance of US-043 (D + C) on the backend.
- **Required fixes before pilot:** none functional. Before claiming a voice-SLO pass, run the pilot
  latency measurement for a clarify turn (live LLM) and record p50/p95/p99 alongside a normal answer
  turn.
