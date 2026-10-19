# Review 2 supervisor revision

**Goal:** Apply the approved review: one backend boundary with explicit microservices, one use-case diagram per human role, a coherent V1 outpatient journey with visible errors and human responsibilities, and class diagrams grouped by service.

**Design:** Keep existing theme, technology/client/server icons, figure IDs where possible, and bilingual graph parity. Figures 04/05 become refresh/booking sequences; 06–09 become login/check-in/Lab/dispense; 37/38 add consultation outcome/prescribing. Figure 10 is Clinical; 11 contains separate Pharmacy/Billing namespaces; 39–41 add Core/Lab/Support class diagrams. Existing database authorities and production behavior remain unchanged. Human explanation/medicine handover are explicitly outside software persistence. DOCX/PDF edited by the user are not regenerated.

**Implementation:** Existing Mermaid renderer, Pillow verification, offline chooser and report publisher. Add a reproducible supervisor revision generator alongside the existing role generator.

- [x] Verify actor permissions, login/refresh responses, appointment preconditions, clearance gates, explicit dispense, and selected domain fields against live source.
- [x] Rewrite role DATA around user goals; keep login/refresh independent, remove screen-level steps and misleading sequential extends, document availability/ownership.
- [x] Author bilingual architecture, eight sequences and five class diagrams; each rejected command returns to the actor without success effects.
- [x] Rebuild standard reports from the revised figure sections, remove V0 saga from the main story, add evidence/limits and align DFD/algorithm prose.
- [x] Generate genuinely distinct Word orientations and readable panels without dropping interactions or namespace boundaries.
- [x] Render changed Mermaid sources to saved SVG and PNG; validate diagram semantics, bilingual message parity, hashes, borders, PNG/SVG replay and chooser, and visually inspect all new layouts.
- [x] Commit the documentation change on `codex/review2-supervisor-revision`; report paths and remaining source-vs-design limits.

**Verification commands** (from `word-compact/tools`): `python supervisor_revision.py`, `python role_usecases.py`, `node render.mjs --original <changed source names>`, `node render.mjs <changed source names and panels>`, `python publish.py`, `python verify_role_usecases.py`, `python verify_supervisor_revision.py`, `python verify.py`, `node inspect.mjs`. No application behavior changes are authorized by this documentation revision.

**Final evidence:** 342 Word Mermaid/SVG/PNG triples passed hash, XML, offline-asset, border and saved-SVG replay checks; 80 bilingual figures have distinct portrait/landscape geometry (single-table Patient ERDs excepted). All 246 chooser combinations passed. Eight role diagrams passed actor/ellipse/arrow checks in 48 complete variants. Sequence panels preserve message order and enclosing conditions; selected class members match live Java source. All four report link sets passed. Visual review caught and corrected nested architecture headings hidden by child rectangles. The DFD Patient boundary now matches the authentication/own-notification scope. No DOCX/PDF or production source changed.
