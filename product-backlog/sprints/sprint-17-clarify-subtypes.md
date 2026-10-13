# Sprint 17 — Configurable billing clarify sub-types

## Sprint Objective

Resolve **OQ-043-b**: make the billing problem **sub-types** offered during the bounded clarify
dialogue (US-043 / ADR-0056) a **per-deployment, enrichable configuration** instead of a single
generic prompt. The clarify question can then gently narrow among the common billing issues the
assistant can actually explain (knowledge base + comparison engine), while staying one natural,
amount-free question. Product confirmed a starter set of **7** sub-types (2026-10-09).

## Scope

### In Scope

- **TASK-BE-072** — config-driven billing clarify sub-type steering:
  - domain `BillingClarifyTopic` + `ClarifyingQuestionGeneratorPort` sub-type hints;
  - `AnswerLanguage.clarifySubTypeDirective` (FR/EN) appended before the recency-last clarify directive;
  - `BillingClarifyProperties` (`@ConfigurationProperties`) binding `billing-clarify.sub-types`;
  - 7 starter sub-types shipped as the `application.yml` default; empty list ⇒ generic TASK-BE-071 prompt;
  - `sub_types` count on the `billing_clarify` telemetry.

### Out Of Scope / Deferred

- **Sub-type → escalation tag.** Detecting *which* sub-type the customer meant and carrying its tag
  into the ADR-0019 escalation context (needs a deterministic classifier + escalation-assembly change)
  — deferred as a follow-up on TASK-BE-072.
- Any live-BSS amount reasoning (unchanged; OQ-002).

## Tickets

| Ticket | Title | Status |
|--------|-------|--------|
| TASK-BE-072 | Configurable billing clarify sub-types (OQ-043-b) | 🔵 Implemented, review 93/100 (Pass), pending QA + user validation — `tasks/task-be-072-configurable-billing-clarify-sub-types.md` |

## Status

🟡 In progress (opened 2026-10-13). Branch `feat/sprint-17-clarify-subtypes` off `feat/restart-from-scratch`;
ticket branch `task/TASK-BE-072-configurable-billing-clarify-sub-types`. TASK-BE-072 implemented
(suite 777 green incl. ArchUnit), adversarial review 93/100 (Pass). Pending user validation + merge.
