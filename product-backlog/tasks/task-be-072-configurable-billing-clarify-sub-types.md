# TASK-BE-072 — Make the billing clarify sub-types a configurable, enrichable list

**Type:** Technical task (backend — conversation flow + clarify prompt + escalation context)
**Status:** 🟢 Open — **not scheduled** (backlog, pending prioritisation into a future sprint). No branch yet.
**Priority:** Medium
**Epic:** EPIC-005 (Answer engine / knowledge base)
**Delivers:** OQ-043-b resolution (US-043). Builds on TASK-BE-071 (increment C) which shipped a **generic** clarify prompt.
**Related:** US-043, TASK-BE-070, TASK-BE-071, ADR-0056 (bounded billing clarify dialogue), BUG-025,
ADR-0019 (escalation + collected context).
**Surfaced by:** Product decision on OQ-043-b (2026-10-09) — the sub-types are a per-deployment
configurable list, enriched over time; a confirmed **starter set of 7** was agreed.

## Context

Increment C (TASK-BE-071) asks **one** natural, LLM-worded clarifying question when a billing problem is
under-specified, bounded by `billing-clarify.max-questions`. The clarify prompt (`CLARIFY_SYSTEM_PROMPT`)
is **generic** today — it does not steer the question toward a known set of billing problem sub-types, and
it carries no sub-type label into the ADR-0019 escalation context.

Product resolved OQ-043-b: the offered sub-types should be a **configurable list per deployment**,
enriched over time, starting from a confirmed set of **7** grounded 1:1 in the eir billing KB and the
billing comparison engine causes.

## Scope & decision

**Starter set of 7 sub-types (Product-confirmed, 2026-10-09):**

| id | Label (FR / EN) | Steers the clarify toward | KB / comparison anchor |
|----|-----------------|---------------------------|------------------------|
| `higher_than_usual`   | Facture plus élevée que d'habitude / Higher than usual        | depuis quand / premier mois | annual price increase, proration |
| `unrecognised_charge` | Montant ou prélèvement non reconnu / Unrecognised charge      | quelle ligne / libellé      | "additional charges" article |
| `service_billed_wrong`| Service facturé en trop ou non souhaité / Service billed wrongly | quel service / depuis quand | comparison SERVICE_ADDED |
| `out_of_bundle`       | Hors-forfait (data/appels/SMS/roaming/international) / Out-of-bundle | quel type d'usage       | roaming / premium SMS / international / bundle-allowances |
| `first_or_moving_bill`| Première facture ou facture de déménagement / First or moving-home bill | nouveau client / déménagement | proration (first bill, moving home) |
| `payment_or_refund`   | Problème de paiement, prélèvement ou remboursement / Payment or refund issue | paiement refusé / en attente / remboursement | payment-options, refunds, non-payment |
| `promo_ended`         | Promotion ou remise expirée / Promotion or discount ended     | fin de promo connue         | comparison end-of-discount / price rise |

**1. Configurable list (no code to add a sub-type):**
- Externalise the sub-types as a config list (e.g. under `voice-support.conversation.billing-clarify`),
  each entry carrying a **stable id**, **FR + EN label**, an **example phrasing** to steer the single
  clarifying question, and an **optional escalation tag**.
- Empty / absent list ⇒ fall back to the current generic `CLARIFY_SYSTEM_PROMPT` behaviour (TASK-BE-071).

**2. Steer the clarify prompt:**
- Inject the configured sub-type labels/examples into the clarify prompt so the single question offers at
  most ~2–3 of them, without turning into an enumeration (keep the DEC-002 "ask only, no amount" rule and
  the voice-friendly single-question framing).

**3. Carry the sub-type into the escalation context:**
- When a sub-type is understood (or at cap), include its escalation tag in the ADR-0019 collected context
  handed to the advisor (the real pilot win — no live BSS).

**Observability (mandatory):**
- Extend `voice_support.billing_clarify` so the (anonymous) sub-type id/outcome is observable (QA can
  verify which sub-type steered the question), correlation-id as usual.

## Acceptance criteria

- Adding/removing/relabelling a sub-type is a **config change only** (no code change), FR/EN.
- The clarify question is steered by the configured sub-types but stays a single natural question
  (no enumeration), DEC-002 preserved (no amount) — covered by service/adapter tests with a fake LLM.
- Empty/absent config ⇒ identical behaviour to TASK-BE-071 (regression-safe default).
- The understood sub-type (when any) appears in the escalation context handed off to the advisor.
- Backend `mvn test` green incl. ArchUnit; pure domain Spring-free; beans wired in config. Manual fakes,
  GIVEN/WHEN/THEN, no Mockito.
- Sub-type/outcome telemetry present and asserted.

## Risks / open questions

- Starter labels/examples are the Product-confirmed set; wording may be tuned per deployment (that is the
  whole point of making it configurable).
- Keep the clarify oral-friendly: offering too many sub-types in one spoken question hurts the voice UX —
  cap how many are surfaced per turn.
- Measure the clarify turn latency as its own slice; the extra prompt context must not regress the voice SLO.
