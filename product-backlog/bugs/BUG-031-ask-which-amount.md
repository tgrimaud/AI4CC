# BUG-031 — An unspecified invoice amount is recited instead of asked

**Type:** Bug (conversation billing)
**Status:** 🔵 In progress (2026-10-15) — `fix/BUG-031-ask-which-amount` off `feat/sprint-21-ask-which-amount`.
**Detected by:** User validation on v0.11.0.
**Related:** US-043, ADR-0055, ADR-0057.

## Problem

When the customer says they do not understand an amount on the bill, without naming which one, the assistant recites the whole invoice difference. It should ask which amount, then explain only that amount.

## Cause

A channel-provided account plus a billing word (`facture`, `montant`) routes straight to the full comparison. The clarify dialogue only runs for "problem / help" openers, and only on the RAG path.

## Expected

- Any invoice request that does not name an amount or a service asks which one. No figures are spoken.
- A still-vague reply asks once more, for the figure or the service name.
- A named amount or service is explained on its own.
- After the clarify budget (default 2), the turn is handed to an advisor instead of reciting the bill.
- "Pourquoi ma facture a augmenté ?" asks first. It does not recite the difference.
