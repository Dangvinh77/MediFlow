# Review 1 Final-System Language Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Revise the seven bilingual Review 1 reports so their requirements and CRS content describe the complete intended MediFlow system without internal code-version, compatibility, or feature-flag terminology.

**Architecture:** Preserve the existing three EN/VI report pairs and bilingual comparison file. Core requirements describe the final business behavior; the limitations sections describe incomplete Review 1 outcomes in stakeholder language, while service ownership, roles, data cycles, and source traceability remain consistent.

**Tech Stack:** Markdown, PowerShell validation, Git, GitHub CLI

---

### Task 1: Normalize the Standard English and Vietnamese reports

**Files:**
- Modify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md`
- Modify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/02-review-1-standard-vi.md`

- [ ] **Step 1: Inventory implementation-oriented terminology**

Run:

```powershell
rg -n 'V0|V1|V2|Core V1|compatib|default-off|feature[- ]gate|opt-in|planned context|target workflow' docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/02-review-1-standard-vi.md
```

Expected: every matching line is assigned either to final-system requirements or to a business-language Review 1 limitation.

- [ ] **Step 2: Rewrite core scope, actor functions, data cycles, and acceptance criteria**

Use these final-system rules in both languages:

- Clinical examinations require the authorized role, valid encounter state, and financial clearance or an audited emergency override.
- Laboratory tests are created by authorized clinical users and processed by authorized Laboratory staff after required clearance.
- Pharmacy owns prescription creation and requires valid payment evidence plus stock before dispensing.
- Billing covers charges, invoices, deposits, payments, receipts, refunds, settlement, and service clearance.
- Inpatient covers admission request, clearance, bed allocation, admission, treatment, transfer, medical discharge, financial settlement, and administrative close.
- Surgery covers request, readiness, clearance, scheduling, execution, recovery, outcome, and charge handoff.

Expected: paired tables keep identical row counts and equivalent facts.

- [ ] **Step 3: Rewrite limitations in stakeholder language**

State incomplete Review 1 outcomes without naming code generations, compatibility endpoints, feature switches, or internal rollout slices. Keep external integrations, production sizing, governance decisions, and incomplete end-to-end acceptance explicit.

- [ ] **Step 4: Validate Standard pair parity**

Run:

```powershell
$root='docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1'
$en=Get-Content -LiteralPath "$root/01-review-1-standard-en.md"
$vi=Get-Content -LiteralPath "$root/02-review-1-standard-vi.md"
@($en|Where-Object{$_ -match '^### '}).Count
@($vi|Where-Object{$_ -match '^### '}).Count
@($en|Where-Object{$_ -match '^\|[-:| ]+\|$'}).Count
@($vi|Where-Object{$_ -match '^\|[-:| ]+\|$'}).Count
```

Expected: heading and table-separator counts match between languages.

### Task 2: Normalize the Detailed English and Vietnamese reports

**Files:**
- Modify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/03-review-1-detailed-en.md`
- Modify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/04-review-1-detailed-vi.md`

- [ ] **Step 1: Rewrite functional requirements and actor CRS rows**

Apply the final-system rules from Task 1 to every `FR-*` and `CRS-*` row. Preserve all identifiers, one owning service per row, and actual write-role boundaries. Keep `DOCTOR` read-only for Laboratory result consumption; Laboratory writes and publication remain `ADMIN`/`LAB_TECH` operations.

- [ ] **Step 2: Rewrite detailed workflow narratives**

For each workflow, retain trigger, responsible authority, input, process, output, and failure/exception information. Describe the complete intended path without internal rollout labels. Move any Review 1 implementation gap into the limitations or acceptance-evidence section.

- [ ] **Step 3: Align traceability and acceptance evidence**

Keep source-grounded ownership and role evidence. Express incomplete acceptance as a business outcome still requiring end-to-end verification, rather than naming an internal implementation version.

- [ ] **Step 4: Validate Detailed pair parity and CRS identity**

Run:

```powershell
$root='docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1'
$en=Get-Content -Raw -LiteralPath "$root/03-review-1-detailed-en.md"
$vi=Get-Content -Raw -LiteralPath "$root/04-review-1-detailed-vi.md"
$idsEn=@([regex]::Matches($en,'CRS-[A-Z]+-\d+')|ForEach-Object Value|Sort-Object -Unique)
$idsVi=@([regex]::Matches($vi,'CRS-[A-Z]+-\d+')|ForEach-Object Value|Sort-Object -Unique)
Compare-Object $idsEn $idsVi
```

Expected: no output from `Compare-Object`; both languages retain the same 20 CRS identifiers.

### Task 3: Normalize the Presentation and Comparison editions

**Files:**
- Modify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/05-review-1-presentation-en.md`
- Modify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/06-review-1-presentation-vi.md`
- Inspect and modify only if necessary: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/07-standard-vs-detailed-comparison.md`

- [ ] **Step 1: Rewrite slide-ready requirements and talking points**

Keep exactly ten H2 sections and ten presenter cues per language. Summarize final-system actor functions and workflows without implementation-version vocabulary.

- [ ] **Step 2: Rewrite presentation limitations**

Use concise stakeholder language for incomplete end-to-end workflows, external integrations, production policy, and capacity certification.

- [ ] **Step 3: Verify the comparison file still describes the final reports accurately**

Update only statements that refer to internal implementation generations or that no longer match Standard versus Detailed content.

- [ ] **Step 4: Validate presentation structure**

Run:

```powershell
$root='docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1'
foreach($name in '05-review-1-presentation-en.md','06-review-1-presentation-vi.md') {
  $c=Get-Content -LiteralPath "$root/$name"
  "$name H2=$(@($c|Where-Object{$_ -match '^## '}).Count) cues=$(@($c|Where-Object{$_ -match '^\*(Presenter cue|Gợi ý trình bày):'}).Count)"
}
```

Expected: both files report `H2=10 cues=10`.

### Task 4: Validate, review, publish, merge, and clean up

**Files:**
- Verify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/*.md`

- [ ] **Step 1: Run the prohibited-terminology check**

Run:

```powershell
rg -n -i '\bV0\b|\bV1\b|\bV2\b|Core V1|compatibility|tương thích|default-off|mặc định tắt|feature[- ]gate|feature flag|opt-in|rollout slice' docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1
```

Expected: no matches.

- [ ] **Step 2: Run package validation**

Confirm seven Markdown files, paired heading/table parity, 20 matching Detailed CRS identifiers, ten presentation sections/cues per language, all nine roles, all ten services, no placeholders, and no forbidden HTML/code fences. Run `git diff --check` and expect no output.

- [ ] **Step 3: Commit the report revision**

Run:

```powershell
git add -- docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1
git commit -m "docs(review1): present final system requirements"
```

Expected: commit policy passes and only report Markdown files are committed.

- [ ] **Step 4: Obtain final independent review**

Review against the approved design, service ownership, role boundaries, final-system language rule, English/Vietnamese parity, and Word-friendly formatting. Expected verdict: `READY TO MERGE` with no critical or important findings.

- [ ] **Step 5: Push and create the pull request**

Run:

```powershell
git push -u origin codex/review-1-bilingual-reports
$body=@'
## Summary
- add Standard, Detailed, and Presentation Review 1 reports in English and Vietnamese
- describe complete MediFlow business requirements without internal implementation-version terminology
- include a bilingual Standard-versus-Detailed comparison and Word-friendly structures

## Validation
- verified seven Markdown deliverables and EN/VI structural parity
- verified 20 matching Detailed CRS identifiers and 10 presentation sections/cues per language
- prohibited-terminology scan and git diff check pass
'@
gh pr create --base master --head codex/review-1-bilingual-reports --title "docs(review1): add bilingual final-system report package" --body $body
```

Expected: GitHub returns a new pull-request URL.

- [ ] **Step 6: Verify checks and merge**

Run GitHub PR checks, merge only after required checks pass, and request remote branch deletion during merge.

- [ ] **Step 7: Return local workspace to master and clean the feature branch**

Run:

```powershell
git switch master
git pull --ff-only origin master
git branch -d codex/review-1-bilingual-reports
git ls-remote --heads origin codex/review-1-bilingual-reports
```

Expected: current branch is `master`, local branch is deleted, remote lookup is empty, and user-owned untracked artifacts remain untouched.
