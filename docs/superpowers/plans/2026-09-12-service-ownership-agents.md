# Service Ownership AGENTS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add concise agent ownership boundaries for all backend service modules without changing GitHub review rules.

**Architecture:** The root `AGENTS.md` owns the global developer-to-module map and cross-service policy. Each service-level `AGENTS.md` repeats only the effective owner, local scope, handoff rule, subagent inheritance, and verification command.

**Tech Stack:** Markdown, Git, Maven

---

### Task 1: Add ownership rules

**Files:**
- Modify: `AGENTS.md`
- Create: `backend/clinical-service/AGENTS.md`
- Create: `backend/lab-service/AGENTS.md`
- Create: `backend/pharmacy-service/AGENTS.md`
- Create: `backend/report-service/AGENTS.md`
- Create: `backend/organization-service/AGENTS.md`
- Create: `backend/patient-service/AGENTS.md`
- Create: `backend/gateway/AGENTS.md`
- Create: `backend/billing-service/AGENTS.md`
- Create: `backend/notification-service/AGENTS.md`

- [ ] **Step 1: Add the root ownership map and global boundary**

Add the approved owner mapping, allow read-only contract investigation, require handoff documentation for missing external contracts, require explicit task-scoped overrides for shared or foreign modules, and make subagents inherit the same boundary.

- [ ] **Step 2: Add concise nested service rules**

Each nested file must state:

```markdown
Root `AGENTS.md` rules continue to apply.
Owner: <name and Git identity>
Writable scope: backend/<module>/**
Task types: IMPLEMENT inside this scope; HANDOFF under docs/ for another owner's required change.
Other services are read-only unless the user explicitly grants a task-scoped override.
Subagents inherit this boundary and cannot bypass it.
Verify: mvn -q -pl backend/<module> -am test
```

Use this mapping: Vinh/Harori → Clinical, Lab; Huy/LQHuy0210 → Pharmacy, Report; Hoàng Anh/TranHoangAnh94 → Organization, Patient, Gateway; Lộc/locgit-89 → Billing, Notification.

- [ ] **Step 3: Validate ownership coverage**

Run:

```powershell
$files = Get-ChildItem backend -Filter AGENTS.md -Recurse
if ($files.Count -ne 9) { throw "Expected 9 backend ownership files" }
rg -n "Owner:|Writable scope:|HANDOFF|Subagents inherit" backend/*/AGENTS.md
git diff --check
```

Expected: nine files, every required rule appears, and `git diff --check` reports no errors.

- [ ] **Step 4: Commit and open the PR**

```bash
git add AGENTS.md backend/*/AGENTS.md docs/superpowers
git commit -m "docs(ownership): scope agents by service owner"
```

Run no Maven suite because the change contains agent documentation only. Create a PR, wait for the existing CI gate, merge after success, and delete the feature branch.
