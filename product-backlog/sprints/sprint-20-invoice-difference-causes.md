# Sprint 20 — Invoice difference causes

## Sprint Objective

Make the spoken invoice explanation use the cause catalogue Product confirmed on
2026-10-15 (ADR-0057): fifteen named causes, detected from the two extracted
invoices, with `UNEXPLAINED` still the fail-closed residual (BR-003).

## Scope

### In scope

- **TASK-BE-073** — replace the coarse cause codes (TASK-BE-067) with the
  ADR-0057 catalogue. A new TV or mobile is an add-on, not a plan change. A
  same-label subscription price rise is a tariff increase. The first partial
  month of a newly added service belongs to that add-on. FR and EN wording for
  every voiced cause. Domain tests, including the existing eir B2C samples.
  The cause code already carried on the billing explanation uses the new code.

### Out of scope

- Siebel history or a CRM billing-cycle event. `CHANGE_CYCLE` stays in the
  catalogue and is not attributed from invoice lines.
- Guessing late-payment or device-installment labels. Those causes do not fire
  until a confirmed eir label list exists.
- `SPEED_UPGRADE` on the current eir samples (no speed token on those bills).
- A same-label price drop, an in-place option price change, and a discount
  whose amount changes on the same line. Those stay `UNEXPLAINED`.
- Pilot deploy of this sprint.

## Tickets

| Ticket | Title | Status |
|--------|-------|--------|
| TASK-BE-073 | Attribute invoice differences to the ADR-0057 cause catalogue | ✅ Done — merged and released in v0.11.0. Adversarial review 95/100 (Pass). QA GO. |

## Status

✅ Done (closed 2026-10-15) — merged into `feat/restart-from-scratch` and released as **v0.11.0**.
