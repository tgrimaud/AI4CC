# TASK-DOC-011 — Refresh architecture.md + reconcile CLAUDE.md

**Type:** Technical task (documentation)
**Sprint:** 19 — Adversarial review follow-ups
**Branch:** `task/TASK-DOC-011-refresh-architecture-claude`
**Status:** 🟢 In progress (2026-10-13)

## Context

The 2026-10-13 full adversarial review (`docs/qa/2026-10-13-full-adversarial-review.md`) flagged the
authoritative project docs as stale: they still describe billing/BSS/Genesys handoff as *target-only*
and pin the pilot LLM to `mistral-api`, while the shipped reality (pilot `v0.9.4`+) is billing
explanation live with routing on `/converse` (ADR-0055/0056) and pilot `llm_provider: openai`
(ADR-0051). The project was also rebranded to **AI4CC** (TASK-OPS-018).

## Acceptance Criteria

- [x] `docs/architecture/architecture.md` title + overview use **AI4CC** (not "Voice Support Bot").
- [x] The authoritative "What is actually built today" block reflects **Sprint 18** reality:
      billing explanation, billing routing + clarify on `/converse`, Genesys Audio Connector handoff
      shipped; only telephony + Genesys live-org legs remain target-only.
- [x] `CLAUDE.md` no longer lists billing/BSS/invoice comparison as "target-only" and no longer
      claims the pilot pins `LLM_PROVIDER=mistral-api` (now `llm_provider: openai`).
- [x] `git diff --check` clean; no contradiction left between the two docs and the pilot config.

## Notes

- Deep route/port tables further down `architecture.md` still show the legacy `main` contract; they are
  explicitly disclaimed by the top note ("authoritative current contract is the one in this block") and
  left as-is (out of scope for this refresh).
- Remaining display-rebrand headers (`docs/README.md`, `adrs/README.md`) and the `.venv` recreation are
  separate Sprint 19 out-of-scope follow-ups.
