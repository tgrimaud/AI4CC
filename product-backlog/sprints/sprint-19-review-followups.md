# Sprint 19 — Adversarial review follow-ups

## Sprint Objective

Close the actionable findings from the 2026-10-13 full adversarial review
(`docs/qa/2026-10-13-full-adversarial-review.md`): fix the one real code bug surfaced on the
voice runtime (BUG-030) and reconcile the stale authoritative docs (`architecture.md`,
`CLAUDE.md`) so the project description matches the shipped reality (billing + Genesys handoff
live since `v0.9.4`+, AI4CC rebrand).

## Scope

### In Scope

- **BUG-030** — streaming STT `CancelFrame` socket leak: discard the in-flight STT session on
  abrupt cancel (not only the pre-warm spare) + regression test.
- **TASK-DOC-011** — refresh `docs/architecture/architecture.md` + reconcile `CLAUDE.md`:
  billing/BSS/Genesys handoff shipped (not "target-only"), billing routing on `/converse`
  (ADR-0055/0056), pilot LLM provider `openai` (not pinned to `mistral-api`), AI4CC naming.

### Follow-ups done this sprint (TASK-OPS-018 tails)

- ✅ Recreated `voice-agent/.venv` (stale shebangs after the dir rename) — rebuilt against public
  PyPI (corporate JFrog mirror was unreachable); `./.venv/bin/behave` works directly again;
  733 unittest + 43 behave scenarios green on the fresh venv. (Local env only — git-ignored.)
- ✅ Finished display rebrand on the remaining current-doc headers (`docs/README.md`,
  `adrs/README.md`, `operations/backlog.md`, `product/cahier-des-charges-fonctionnel.md`, the two
  Genesys integration/ops docs, Galaxion docs). ADR bodies + QA review docs keep the historical
  "Voice Support Bot" name on purpose.

### Still out of scope (needs a user decision)

- Release tag + pilot redeploy under `ghcr.io/tgrimaud/ai4cc-*` (TASK-OPS-018 operational tail):
  gated on Sprint 19 closure + an explicit deploy request; the pilot `.env` image refs must be
  flipped to the `ai4cc-*` names in the same operation, else the pilot points at non-existent images.

## Tickets

| Ticket | Title | Status |
|--------|-------|--------|
| BUG-030 | Streaming STT CancelFrame socket leak | ✅ Fixed + merged into the sprint (`--no-ff`); adversarial review 95/100 (Pass); 733 unittest + 43 behave green |
| TASK-DOC-011 | Refresh architecture.md + reconcile CLAUDE.md | ✅ Done + merged into the sprint (`--no-ff`); `git diff --check` clean |

## Status

🟢 In progress (opened 2026-10-13) — both tickets merged into the sprint branch
`feat/sprint-19-review-followups` (`--no-ff`). Sprint closure (merge into
`feat/restart-from-scratch` + status flip) awaits the user's explicit request.
