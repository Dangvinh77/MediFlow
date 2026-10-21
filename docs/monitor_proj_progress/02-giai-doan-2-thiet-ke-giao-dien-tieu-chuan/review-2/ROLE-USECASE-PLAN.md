# Review 2 — Use cases by user role

**Goal:** One detailed, bilingual use-case diagram for each of the eight human roles, following the supervisor's actor/main-task/subtask presentation.

**Design:** Keep overview figures 04–05 and add figures 29–36. Use one stick-figure actor per detailed diagram, solid arrows to primary tasks, dashed `«include»` arrows to required supporting behavior, and dashed `«extend»` arrows from conditional extensions to the base task. An extension may include its own supporting tasks. Retain the Sky/Teal theme and offline actor asset. Human actors exclude SYSTEM. Authentication is a precondition for staff diagrams; Patient also illustrates login/refresh.

**Scope:** Existing outpatient source only, checked against both Gateway route authorization and downstream controllers. Patient appointment/lab self-service is not currently authorized. Feature-gated care/finance operations are marked in prose and labels. No production source, historical DOCX/PDF, or existing architecture changes.

## Execution

- [x] Record role/task/source evidence and author 16 Mermaid sources, with the same IDs and relationships in English/Vietnamese.
- [x] Add independent A4 portrait/landscape sources and optional readable detail panels; retain overview numbering.
- [x] Integrate figures into both standard reports, both Word report options, and the offline chooser; remove fixed 28-figure assumptions from publishing/verification.
- [x] Render through the established isolated saved-SVG pipeline; verify image borders, source/output hashes, PNG/SVG pixel replay, bilingual graph equivalence, and distinct orientations.
- [x] Inspect every new role layout visually, correct crowded labels/arrows, and report output locations and limitations.

**Validation:** Run selected `render.mjs` for modified/new diagrams, `python publish.py`, `python verify.py`, and `node inspect.mjs`. Review contact sheets and full-resolution dense samples. No application tests are necessary for documentation-only changes.

**Verified results:** 268 Word Mermaid/SVG/PNG sets passed file, border, hash and saved-SVG replay checks; 70 orientation pairs have independent layouts (two single-table ERD exceptions). Eight roles and 48 full variants passed bilingual/relationship/ellipse checks. The chooser passed 216 checks. All new full diagrams and 50 detail panels were visually inspected. Historical DOCX/PDF files were not regenerated.
