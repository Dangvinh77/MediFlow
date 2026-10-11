# Review 1 Bilingual Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create seven source-aligned, Word-friendly Review 1 Markdown reports covering Problem Definition, Customer Requirement Specification, actor data cycles, and client/server requirements in English and Vietnamese.

**Architecture:** Use one factual content model and publish it at three levels of detail: Standard, Detailed, and Presentation. Author the English file first in each pair, translate it structurally into Vietnamese while preserving requirement identifiers and source-defined names, then generate a bilingual comparison from the completed Standard and Detailed editions.

**Tech Stack:** Markdown, PowerShell validation, Git, source evidence from Java 21/Spring Boot 3.3.5, Next.js 16.2.11/React 19.2.4, PostgreSQL 16, RabbitMQ 3 Management Alpine, Docker Compose, and the partial Flutter package baseline.

---

## File Map

Create all report files under:

`docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/`

| File | Responsibility |
|---|---|
| `01-review-1-standard-en.md` | Formal English Review 1 submission, approximately 15–20 Word pages. |
| `02-review-1-standard-vi.md` | Vietnamese structural equivalent of the Standard English report. |
| `03-review-1-detailed-en.md` | Expanded English report with actor-specific cycles, requirement IDs, exceptions, and technical profiles. |
| `04-review-1-detailed-vi.md` | Vietnamese structural equivalent of the Detailed English report. |
| `05-review-1-presentation-en.md` | Concise English presentation source, approximately 8–12 Word pages. |
| `06-review-1-presentation-vi.md` | Vietnamese structural equivalent of the Presentation English report. |
| `07-standard-vs-detailed-comparison.md` | Bilingual comparison and usage recommendation for Standard versus Detailed. |

## Evidence Files

Use these files as the primary evidence during authoring:

- `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/README.md`
- `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/bao-cao-giai-trinh.md`
- `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/giai-doan-1-crs-bieu-mau.md`
- `backend/organization-service/src/main/java/com/mediflow/organization/domain/model/Role.java`
- `pom.xml`
- `docker-compose.yml`
- `frontend/package.json`
- `mobile/packages/mediflow_loader/pubspec.yaml`
- `docs/ai/01-architecture.md`
- `docs/ai/05-api-conventions.md`
- `docs/ai/06-events-rabbitmq.md`
- `docs/ai/07-security-rbac.md`
- `docs/ai/16-care-finance-integration-contracts.md`
- `docs/ai/services/*.md`

Source-alignment decisions that every report must preserve:

- Ten backend business contexts are implemented and tested.
- Nine source-defined roles include eight human-facing roles and the internal `SYSTEM` role.
- The web client is implemented; the `mobile/` tree currently contains a reusable Flutter loader package rather than a complete runnable MediFlow mobile application. Mobile access must therefore be described as partial or future-facing, not complete.
- Docker Compose provides a demonstration topology, not certified production sizing.
- Hardware quantities are planning recommendations and must not be presented as measured capacity guarantees.

### Task 1: Create the Standard English report

**Files:**
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md`

- [ ] **Step 1: Create document control and report purpose**

Add an H1 title and a two-column metadata table containing project, report, edition, language, review date, source baseline, and status. Follow it with a purpose paragraph stating that the report covers both Part A and Part B and is intended for formal Review 1 submission.

- [ ] **Step 2: Write Part A — Problem Definition**

Use these exact H2 sections:

```markdown
## Part A — Problem Definition
### A1. Project Introduction and Context
### A2. Problem Identification
### A3. Project Objectives
### A4. Project Scope
### A5. Users and Major Functions
### A6. Proposed Solution
### A7. Current Limitations and Unimplemented Capabilities
```

Cover fragmented hospital data, delayed handoffs, authorization, financial clearance, traceability, ten context boundaries, included and excluded scope, and all nine roles. Use one compact actor/function table. Clearly identify incomplete mobile delivery, unconfirmed production SLOs and retention policies, and external integrations that are not implemented.

- [ ] **Step 3: Write Part B — Customer Requirement Specification**

Use these exact H2 sections:

```markdown
## Part B — Customer Requirement Specification
### B1. Customer and Stakeholder Requirements
### B2. Data-Processing Cycle by User Type
### B3. Cross-Department Data Flows
### B4. Client Hardware Requirements
### B5. Client Software Requirements
### B6. Server Hardware Requirements
### B7. Server and Infrastructure Software Requirements
### B8. Security, Reliability, and Operational Requirements
### B9. Acceptance Checklist
### B10. Glossary
```

Use the shared cycle table contract:

```markdown
| Workflow | Actor | Input information | Processing | Output information | Owning service |
|---|---|---|---|---|---|
```

Include at least one row for each human-facing role and representative rows for the internal System actor. Cover all ten required cross-department workflows from the approved design.

- [ ] **Step 4: Add Word-friendly client and server requirement tables**

Create separate tables for client hardware, client software, development/demo server hardware, minimum small-deployment hardware, recommended scalable hardware, and server software. State Java 21, Spring Boot 3.3.5, Next.js 16.2.11, React 19.2.4, PostgreSQL 16, RabbitMQ 3 Management Alpine, Docker Compose, and modern browsers where relevant. Label CPU, RAM, storage, bandwidth, TLS, backup, and monitoring values as planning recommendations.

- [ ] **Step 5: Validate the Standard English report**

Run:

```powershell
$file = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md'
rg -n '^## Part A|^## Part B|^### A[1-7]\.|^### B(?:[1-9]|10)\.' $file
rg -n 'ADMIN|DOCTOR|NURSE|PHARMACIST|CASHIER|LAB_TECH|MANAGER|PATIENT|SYSTEM' $file
rg -n 'Organization|Patient|Clinical|Lab|Pharmacy|Billing|Notification|Report|Inpatient|Surgery' $file
```

Expected: both parts, all required numbered sections, all nine roles, and all ten services are present.

- [ ] **Step 6: Commit the Standard English report**

```powershell
git add -- 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md'
git commit -m "docs(review1): add standard English report"
```

### Task 2: Create the Standard Vietnamese report

**Files:**
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/02-review-1-standard-vi.md`

- [ ] **Step 1: Translate the Standard report structurally**

Use the same section order and tables as the English report. Translate narrative, headings, column names, requirements, limitations, and acceptance statements into natural Vietnamese. Retain source-defined role names, service names, versions, API terms, IDs, and product names in English where translation would reduce traceability.

- [ ] **Step 2: Use the Vietnamese cycle table contract**

```markdown
| Quy trình | Đối tượng | Thông tin đầu vào | Xử lý | Thông tin đầu ra | Dịch vụ sở hữu |
|---|---|---|---|---|---|
```

Ensure that every English data-cycle row has one Vietnamese counterpart with the same actor and owning service.

- [ ] **Step 3: Validate structural parity**

Run:

```powershell
$en = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md'
$vi = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/02-review-1-standard-vi.md'
$enH2 = (Select-String -LiteralPath $en -Pattern '^## ').Count
$viH2 = (Select-String -LiteralPath $vi -Pattern '^## ').Count
$enH3 = (Select-String -LiteralPath $en -Pattern '^### ').Count
$viH3 = (Select-String -LiteralPath $vi -Pattern '^### ').Count
if ($enH2 -ne $viH2 -or $enH3 -ne $viH3) { throw 'Standard report heading trees differ' }
```

Expected: English and Vietnamese H2/H3 counts match.

- [ ] **Step 4: Commit the Standard Vietnamese report**

```powershell
git add -- 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/02-review-1-standard-vi.md'
git commit -m "docs(review1): add standard Vietnamese report"
```

### Task 3: Create the Detailed English report

**Files:**
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/03-review-1-detailed-en.md`

- [ ] **Step 1: Expand Part A with traceable definitions**

Reuse the Standard Part A facts, then add stakeholder interests, measurable project goals, explicit in-scope/out-of-scope boundaries, one subsection per actor, a solution-component table, and a limitations table grouped as Not finalized, External dependency not integrated, and Demonstration constraint.

- [ ] **Step 2: Add actor-specific CRS subsections**

Under `### B2. Data-Processing Cycle by User Type`, create these H4 subsections:

```markdown
#### B2.1 Administrator
#### B2.2 Doctor
#### B2.3 Nurse
#### B2.4 Laboratory Technician
#### B2.5 Pharmacist
#### B2.6 Cashier
#### B2.7 Manager
#### B2.8 Patient
#### B2.9 Internal System Actor
```

Each subsection must include responsibilities, permitted functions, restrictions, normal-flow data-cycle rows, and one important alternative or failure outcome. Use stable IDs such as `CRS-ADM-01`, `CRS-DOC-01`, `CRS-NUR-01`, `CRS-LAB-01`, `CRS-PHA-01`, `CRS-CAS-01`, `CRS-MAN-01`, `CRS-PAT-01`, and `CRS-SYS-01`.

- [ ] **Step 3: Expand the ten cross-department workflows**

For each workflow, provide:

```markdown
#### Workflow title
- **Trigger:** business event or user request.
- **Primary authority:** service that owns the decision.
- **Input:** business information received.
- **Process:** ordered synchronous and asynchronous steps.
- **Output:** records, decisions, events, or user information produced.
- **Failure or exception:** controlled outcome and owning service.
```

Do not imply shared transactions or cross-service table access.

- [ ] **Step 4: Expand non-functional and deployment profiles**

Add requirement IDs for security, privacy, reliability, idempotency, retry/DLQ behavior, observability, backup, performance planning, maintainability, and usability. Separate the infrastructure tables into Development/Demonstration, Minimum Small Deployment, and Recommended Scalable Deployment. Explain that the latter two are planning profiles pending measured workload and stakeholder SLO approval.

- [ ] **Step 5: Add traceability and acceptance evidence**

Add a matrix with columns Requirement ID, Actor or workflow, Owning service, Source evidence area, and Acceptance method. Include the 3,612-test baseline as supporting evidence, not customer acceptance proof.

- [ ] **Step 6: Validate Detailed English coverage**

Run:

```powershell
$file = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/03-review-1-detailed-en.md'
rg -n '^#### B2\.[1-9] ' $file
rg -n 'CRS-ADM-|CRS-DOC-|CRS-NUR-|CRS-LAB-|CRS-PHA-|CRS-CAS-|CRS-MAN-|CRS-PAT-|CRS-SYS-' $file
rg -n 'patient registration|clinical encounter|laboratory|dispens|refund|admission|surgery|notification|report' $file
```

Expected: all nine actor subsections, all requirement-ID families, and all required workflow categories are present.

- [ ] **Step 7: Commit the Detailed English report**

```powershell
git add -- 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/03-review-1-detailed-en.md'
git commit -m "docs(review1): add detailed English report"
```

### Task 4: Create the Detailed Vietnamese report

**Files:**
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/04-review-1-detailed-vi.md`

- [ ] **Step 1: Translate every Detailed section and table**

Preserve the English section order, all `CRS-*` identifiers, actor order, service ownership, version numbers, and row count. Translate explanations and business information naturally rather than word-for-word.

- [ ] **Step 2: Verify requirement-ID equality**

Run:

```powershell
$en = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/03-review-1-detailed-en.md'
$vi = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/04-review-1-detailed-vi.md'
$enIds = [regex]::Matches((Get-Content -LiteralPath $en -Raw), 'CRS-[A-Z]+-\d+') | ForEach-Object Value | Sort-Object -Unique
$viIds = [regex]::Matches((Get-Content -LiteralPath $vi -Raw), 'CRS-[A-Z]+-\d+') | ForEach-Object Value | Sort-Object -Unique
if (Compare-Object $enIds $viIds) { throw 'Detailed report requirement IDs differ' }
```

Expected: no comparison output and exit code `0`.

- [ ] **Step 3: Verify heading and table parity**

Compare H2/H3/H4 counts and the number of data-cycle table headers between both files. Expected: equal counts at each level.

- [ ] **Step 4: Commit the Detailed Vietnamese report**

```powershell
git add -- 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/04-review-1-detailed-vi.md'
git commit -m "docs(review1): add detailed Vietnamese report"
```

### Task 5: Create the Presentation English report

**Files:**
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/05-review-1-presentation-en.md`

- [ ] **Step 1: Create a 10-section slide-ready narrative**

Use these sections:

```markdown
## 1. MediFlow in One Minute
## 2. Hospital Context
## 3. Problems to Solve
## 4. Scope and Actors
## 5. Proposed Solution
## 6. Part B — How Data Moves
## 7. Representative End-to-End Workflows
## 8. Client and Server Requirements
## 9. Current Limitations
## 10. Review 1 Conclusion
```

Keep each section focused enough to become one or two slides. Use no more than two tables per section.

- [ ] **Step 2: Add presenter cues**

End each section with a short italicized `Presenter cue:` line containing the one point that must be spoken. Do not create a full speech transcript.

- [ ] **Step 3: Validate presentation coverage**

Run:

```powershell
$file = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/05-review-1-presentation-en.md'
if ((Select-String -LiteralPath $file -Pattern '^## ').Count -ne 10) { throw 'Presentation must contain 10 main sections' }
if ((Select-String -LiteralPath $file -Pattern '^\*Presenter cue:').Count -ne 10) { throw 'Each section needs one presenter cue' }
```

Expected: exit code `0`.

- [ ] **Step 4: Commit the Presentation English report**

```powershell
git add -- 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/05-review-1-presentation-en.md'
git commit -m "docs(review1): add English presentation report"
```

### Task 6: Create the Presentation Vietnamese report

**Files:**
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/06-review-1-presentation-vi.md`

- [ ] **Step 1: Translate the ten presentation sections**

Preserve the section order, factual content, tables, role and service references, limitations, and one presenter cue per section. Translate `Presenter cue:` as `Gợi ý trình bày:`.

- [ ] **Step 2: Verify presentation parity**

Run:

```powershell
$en = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/05-review-1-presentation-en.md'
$vi = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/06-review-1-presentation-vi.md'
if ((Select-String -LiteralPath $en -Pattern '^## ').Count -ne (Select-String -LiteralPath $vi -Pattern '^## ').Count) { throw 'Presentation heading counts differ' }
if ((Select-String -LiteralPath $vi -Pattern '^\*Gợi ý trình bày:').Count -ne 10) { throw 'Vietnamese presenter cues are incomplete' }
```

Expected: exit code `0`.

- [ ] **Step 3: Commit the Presentation Vietnamese report**

```powershell
git add -- 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/06-review-1-presentation-vi.md'
git commit -m "docs(review1): add Vietnamese presentation report"
```

### Task 7: Create the bilingual Standard-versus-Detailed comparison

**Files:**
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/07-standard-vs-detailed-comparison.md`

- [ ] **Step 1: Write the bilingual decision summary**

State in English and Vietnamese that Standard is the recommended formal submission and Detailed is the evidence-rich reference or appendix source.

- [ ] **Step 2: Add the comparison matrix**

Use these rows:

```markdown
| Criterion / Tiêu chí | Standard edition / Bản tiêu chuẩn | Detailed edition / Bản chi tiết |
|---|---|---|
| Primary audience / Đối tượng chính | Review 1 evaluator and project panel / Giảng viên và hội đồng Review 1 | Evaluator, development team, and later reviewers / Giảng viên, nhóm phát triển và người rà soát sau này |
| Purpose / Mục đích | Formal submission with complete essential content / Báo cáo nộp chính thức với nội dung thiết yếu đầy đủ | Evidence-rich reference and traceability source / Tài liệu tham chiếu giàu bằng chứng và nguồn truy vết |
| Target length / Độ dài mục tiêu | 15–20 Word pages / 15–20 trang Word | 25–35 Word pages / 25–35 trang Word |
| Actor coverage / Mức độ bao phủ đối tượng | Consolidated functions for all nine roles / Tổng hợp chức năng của chín vai trò | Separate responsibilities, restrictions, and cycles for every role / Tách riêng trách nhiệm, giới hạn và chu trình của từng vai trò |
| Workflow depth / Chiều sâu quy trình | Essential normal flows / Các luồng chính thiết yếu | Normal flows, alternatives, failures, ownership, and acceptance / Luồng chính, thay thế, lỗi, quyền sở hữu và tiêu chí chấp nhận |
| Requirement traceability / Truy vết yêu cầu | Section-level traceability / Truy vết theo phần | Stable `CRS-*` IDs and evidence matrix / Mã `CRS-*` ổn định và ma trận bằng chứng |
| Hardware and software detail / Chi tiết phần cứng, phần mềm | Concise client and server profiles / Hồ sơ client và server cô đọng | Development, minimum, and recommended profiles with assumptions / Hồ sơ phát triển, tối thiểu và khuyến nghị kèm giả định |
| Advantages / Ưu điểm | Easier to read, submit, and present / Dễ đọc, nộp và trình bày | Stronger evidence, exception coverage, and future reuse / Bằng chứng mạnh hơn, bao phủ ngoại lệ và dễ tái sử dụng |
| Limitations / Hạn chế | Less room for exceptions and detailed traceability / Ít không gian cho ngoại lệ và truy vết chi tiết | Longer review time and higher maintenance effort / Thời gian đọc và công sức bảo trì cao hơn |
| Recommended use / Cách sử dụng khuyến nghị | Main Review 1 submission / Báo cáo chính cho Review 1 | Appendix source, evaluator questions, and team reference / Nguồn phụ lục, trả lời câu hỏi và tài liệu nhóm |
```

- [ ] **Step 3: Add a section-by-section mapping**

List every Standard section and identify the additional Detailed content. Finish with a bilingual recommendation for submission, evaluator questions, team reference, and presentation preparation.

- [ ] **Step 4: Validate the comparison file**

Run:

```powershell
$file = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/07-standard-vs-detailed-comparison.md'
rg -n 'Standard edition|Bản tiêu chuẩn|Detailed edition|Bản chi tiết|Recommended use|Cách sử dụng khuyến nghị' $file
rg -n '\.\.\.|TODO|TBD' $file
```

Expected: the first command finds all bilingual labels; the second command produces no output.

- [ ] **Step 5: Commit the comparison file**

```powershell
git add -- 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/07-standard-vs-detailed-comparison.md'
git commit -m "docs(review1): compare standard and detailed editions"
```

### Task 8: Run package-level validation and final editorial pass

**Files:**
- Modify only if validation finds defects: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/*.md`

- [ ] **Step 1: Verify the seven-file inventory**

Run:

```powershell
$root = 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1'
$files = Get-ChildItem -LiteralPath $root -Filter '*.md' -File
if ($files.Count -ne 7) { throw "Expected 7 reports, found $($files.Count)" }
$files | Sort-Object Name | Select-Object Name,Length
```

Expected: exactly seven non-empty Markdown files.

- [ ] **Step 2: Scan for unresolved placeholders and broken formatting**

Run:

```powershell
rg -n 'TODO|TBD|PLACEHOLDER|\.\.\.' $root
git diff --check
```

Expected: placeholder search produces no report content matches and `git diff --check` exits `0`.

- [ ] **Step 3: Verify role, service, and technical baseline coverage**

Run one search across all reports for the nine roles, ten services, Java 21, Spring Boot 3.3.5, Next.js 16.2.11, PostgreSQL 16, RabbitMQ, Docker Compose, client hardware, client software, server hardware, and server software. Expected: each required concept appears in the Standard and Detailed pairs; the Presentation pair may use a consolidated technology table.

- [ ] **Step 4: Verify paired structures and requirement IDs**

Compare heading counts for each English/Vietnamese pair. Compare `CRS-*` identifier sets for the Detailed pair. Expected: equal paired counts and identical identifier sets.

- [ ] **Step 5: Perform the Word-readiness review**

Check every table for no more than six columns, paragraphs for focused length, heading levels for H1–H4 only, and absence of Mermaid, HTML layout, nested tables, or GitHub-only blocks.

Run:

```powershell
rg -n '```mermaid|<table|<div|<details|<summary' $root
```

Expected: no output.

- [ ] **Step 6: Confirm Git scope**

Run:

```powershell
git status --short
git diff --name-only master...HEAD
```

Expected: implementation changes are limited to the seven Review 1 files plus the approved design and plan documents; no DOCX, PDF, application source, or canonical `docs/ai` file is changed.

- [ ] **Step 7: Commit editorial fixes if needed**

If validation required report edits, run:

```powershell
git add -- 'docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1'
git commit -m "docs(review1): finalize bilingual report package"
```

If no report changed after validation, do not create an empty commit.
