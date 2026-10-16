# BUG-032 — Scenario billing phrases skip the clarify question

**Type:** Bug (conversation billing)
**Status:** ✅ Released in **v0.11.2** (2026-10-16) — merged into `feat/restart-from-scratch`.
**Detected by:** Smoke on v0.11.1 (eir-ai4cc-tst).
**Related:** BUG-031, US-043, ADR-0055.

## Problem

Some invoice requests that name neither an amount nor a service do not ask which one. A generic word of five letters or more is treated as a named service.

## Expected

- "je ne reconnais pas un prélèvement", "un service facturé en trop", "c'est quoi cette ligne", "my bill is higher than usual" and "a charge I don't recognise" ask first. They do not recite the bill and they do not say the amount is missing.
- "aucune idée" and "still no idea", after the two clarify questions, hand the turn to an advisor.
- A named amount or a real service word (bundle, roaming, mobile) is still explained on its own.
