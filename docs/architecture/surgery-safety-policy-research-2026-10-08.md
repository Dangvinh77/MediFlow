# Surgery safety policy — research baseline, 2026-10-08

**Status:** RESEARCHED / IMPLEMENTATION DESIGN, not an approved hospital protocol.
**Scope:** Surgery checklist phases, team evidence and consent authority. No production activation.
**Requested by:** Huy; official medical/legal references replace unsupported assumptions, not real evidence.

## 1. Source-backed baseline

The WHO checklist separates checks before anaesthesia, before incision and before leaving the operating
room. Identity/procedure/site/consent, anaesthesia preparation and patient risks belong to the first;
team introductions, shared concerns, prophylaxis and necessary imaging to the second; performed
procedure, counts, specimen identification, equipment issues and recovery handover to the third.
At least nursing and anaesthesia personnel participate in the first; surgeon, anaesthesia and nursing
participate in the later phases. Some checks permit clinically justified non-applicability. This is
not a comprehensive procedure catalogue or a universal staffing headcount.
[WHO implementation manual, printed pp. 6–12](https://cdn.who.int/media/docs/default-source/patient-safety/9789241598590-eng.pdf).

Adaptation should be collaborative, actionable and tested in simulations and local practice, retaining
team communication. A database tick is not the verbal safety pause.
[WHO adaptation guide](https://cdn.who.int/media/docs/default-source/patient-safety/safe-surgery/checklist-adaptation.pdf?sfvrsn=dcbb632f_6).
An adapted checklist must not display the WHO emblem or imply WHO certification.
[WHO tools and resources](https://www.who.int/teams/integrated-health-services/quality-of-care-and-patient-safety/patient-safety-guidance-and-tools/safe-surgery/tool-and-resources).

For Vietnam, the consulted consolidated law is **26/VBHN-VPQH, 26 February 2026**, incorporating
the amendments named on its first page. Article 65 requires patient consent or a representative
covered by Article 8(2)(a–d), with Article 15 governing the specified capacity/minor cases.
Article 8 distinguishes representative categories and replacement evidence. Article 15 differentiates
prior lawful wishes, eligible representatives and decisions by the responsible professional/leader
when no eligible representative exists; the minor branch specifically references Article 8(2)(c,d).
Article 61(2) separately addresses urgent intervention before representative agreement. These
provisions do not justify treating every relative, volunteer, DOCTOR or ADMIN as a valid signer.
They do not establish a universal witness requirement in these cited articles.
[Official consolidated law, printed pp. 9–12, 37, 40](https://datafiles.chinhphu.vn/cpp/files/vbpq/2026/3/26-vbhn-vpqh.pdf).
This is a limited software-design reading, not a complete legal compliance determination.

The national legal database lists **50/2014/TT-BYT** as in force and identifies its procedure
classification/staffing subject. Its full text could not be retrieved in this run (403/502); do not
copy headcounts from search snippets or claim its annexes have been implemented.
[Official record](https://vbpl.vn/TW/Pages/ivbpq-thuoctinh.aspx?ItemID=66661).

## 2. Engineering decisions for MediFlow

These are Huy's proposed software boundaries, not additional WHO rules:

| Boundary | Candidate persisted evidence | What it must not imply |
|---|---|---|
| Planning / READY | Exact procedure-specific pre-op template, current source revisions, consent and eligibility | The intraoperative safety pauses have already happened |
| `BEFORE_ANAESTHESIA` | Case-scoped observed safety pause, participant references and item decisions | A planned appointment time is the actual induction time |
| `BEFORE_INCISION` | Separate pause referencing current team/procedure/site and prior phase | Generic `startedAt` proves both induction and incision |
| `BEFORE_OR_EXIT` | Counts, actual procedure and handover evidence linked to the performed case | Missing post-op evidence may be prefilled to enable READY |

The current `SurgeryChecklistTemplate`/snapshot is a **pre-op readiness** model; it has no phase
or actual anaesthesia/incision/OR-exit timestamps. Do not seed the whole WHO checklist into it.
`SurgeryChecklistEvidencePolicy.Rule` currently disallows mandatory + N/A. Keep that fail-closed
rule until a separate, audited applicability decision model exists; do not globally relax mandatory
items to make an imported checklist pass. No source gives every lab result or signature one fixed TTL.
Finite adapter observation age is a technical freshness bound, not clinical document expiry.

Keep `SurgeryTeamCompositionPolicy` procedure-specific and versioned. A safety-pause participant
set is not the staffing establishment, maximum team size or credential grant. Organization's exact
staff/role/procedure/interval capability remains required; login roles and job-title strings are not
proof. No universal `1 surgeon + 1 anaesthetist + 1 nurse` production team is configured here.

Keep recording account, patient/representative signer, witness and decision authority distinct.
Require authoritative case/patient/document/relationship references; client booleans are not proof.
The existing signer enum and consent record cannot represent every legal decision branch. Unsupported
branches remain explicit denials, not PATIENT/GUARDIAN aliases. Emergency authority must have a
separate audited workflow; the current V1 has no emergency or financial bypass.

## 3. Implementation work inside existing plan IDs

This is an acceptance refinement of S-05/S-06/S-07/X-01.4, **not additional global task counts**.

1. Add a Surgery-owned phase model and immutable procedure-policy revision; retain existing pre-op
   history and migrate additively. Unknown phases and legacy rows without phase evidence never become
   completed pauses through backfill. Pin the policy revision per case; changes invalidate affected
   pre-start readiness, not rewrite completed history.
2. Introduce applicability evidence separate from successful execution: exact item, policy revision,
   verified recorder, reason category and evidence reference. Only policy-allowed branches can be N/A;
   a failed required observation never converts to N/A. Store clinical narrative privately, not in
   reporting or notification payloads.
3. Define exact anaesthesia/incision/exit semantics before connecting phase gates to existing START
   and COMPLETE. Use actual observations, append-only phase receipts and idempotent commands. Never
   invent timestamps or emit completed Billing charges because a safety form was merely drafted.
4. Wire creation/pre-op authority against real source APIs and authenticated local attestations.
   Reauthorize replay and fence source revisions after lock waits. Missing source/provider, timeout,
   wrong patient/episode/order, stale revision and changed consent document all fail closed.
5. Extend legal authority evidence only after the hospital identifies its representation/decision
   registry and form policy. Version per consent type; do not require witnesses universally or silently
   remove an already configured witness/document requirement. Preserve sign/revoke audit and separate
   emergency workflow from the existing elective-only V1.
6. Verify phase order, wrong-phase recording, same-key replay/conflict, authorized N/A versus bypass,
   participant substitution, consent replacement/revoke, transactional rollback and concurrent START.
   Test provider outages with no state/outbox effects. Add shared producer/consumer acceptance only
   when wire actually changes; keep existing V1 fixtures unchanged meanwhile.

## 4. Activation evidence

The real authority source, procedure-to-template/team mapping, documented clinical approval and
staff training/simulation acceptance still need to be recorded before clinical use. Research URLs
are provenance, not an approval signature or source grant. Feature flags remain false; no positive
authority bean, clinical catalogue, guessed staff/patient identity or production seed is added.
Gateway stays outside Huy's writable scope.

Canonical technical context: [Surgery spec](../eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md),
[Surgery service rules](../ai/services/surgery.md),
[active authority handoff](../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md),
[Huy plan](../superpowers/plans/2026-09-25-huy-surgery-pharmacy-report.md).
