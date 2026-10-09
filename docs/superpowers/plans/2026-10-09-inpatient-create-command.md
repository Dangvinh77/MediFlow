# Inpatient Create Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tested Next.js command flow that lets ADMIN and DOCTOR create an Inpatient admission using the exact live backend request contract.

**Architecture:** Add a minimal shared Vitest/React Testing Library harness, then keep all domain behavior inside the Inpatient feature: pure form validation/mapping, a typed API method, a client form, and thin routes. The backend remains authoritative for identity, authorization and admission rules; the browser performs only contract-safe input validation.

**Tech Stack:** Next.js 16, React 19, TypeScript 5, Tailwind CSS 4, Vitest, React Testing Library, jsdom, pnpm.

---

## File map

- Modify `frontend/package.json` and `frontend/pnpm-lock.yaml`: test scripts and pinned test dependencies.
- Create `frontend/vitest.config.mts`: jsdom, React and TypeScript alias configuration.
- Create `frontend/src/test/setup.ts`: DOM matchers and deterministic cleanup.
- Create `frontend/src/test/harness.test.tsx`: harness smoke coverage.
- Modify `frontend/src/features/inpatient/types.ts`: exact `CreateAdmissionRequest` wire type.
- Modify `frontend/src/features/inpatient/api.ts`: typed create-admission call.
- Create `frontend/src/features/inpatient/api.test.ts`: request method/path/payload contract test.
- Create `frontend/src/features/inpatient/form.ts`: feature-local form model, validation and mapping.
- Create `frontend/src/features/inpatient/form.test.ts`: pure validation/mapping tests.
- Create `frontend/src/features/inpatient/components/AdmissionMutationError.tsx`: status-aware error presentation.
- Create `frontend/src/features/inpatient/components/CreateAdmissionForm.tsx`: interactive create form.
- Create `frontend/src/features/inpatient/components/CreateAdmissionForm.test.tsx`: client behavior tests.
- Create `frontend/src/features/inpatient/components/AdmissionCreateLink.tsx`: role-filtered create action.
- Create `frontend/src/features/inpatient/components/AdmissionCreateLink.test.tsx`: role matrix test.
- Modify `frontend/src/features/inpatient/components/AdmissionTable.tsx`: render the create action.
- Modify `frontend/src/features/inpatient/components/AdmissionDetail.tsx`: display creation notice.
- Create `frontend/src/app/(dashboard)/inpatient/new/page.tsx`: thin guarded route.
- Modify `frontend/src/app/(dashboard)/inpatient/[admissionId]/page.tsx`: forward the creation notice.
- Modify `frontend/docs/services/inpatient.md` and `frontend/docs/frontend-workboard.md`: record the delivered slice and next command.

### Task 1: Install and prove the frontend test harness

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/pnpm-lock.yaml`
- Create: `frontend/vitest.config.mts`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/src/test/harness.test.tsx`

- [ ] **Step 1: Install the official Next.js Vitest stack and DOM helpers**

Run:

```powershell
pnpm add -D vitest @vitejs/plugin-react jsdom @testing-library/react @testing-library/dom @testing-library/user-event @testing-library/jest-dom vite-tsconfig-paths
```

Expected: `package.json` and `pnpm-lock.yaml` change; install exits 0.

- [ ] **Step 2: Add deterministic test scripts**

Add to `frontend/package.json` scripts:

```json
"test": "vitest run",
"test:watch": "vitest"
```

- [ ] **Step 3: Add the Vitest configuration and setup**

Create `frontend/vitest.config.mts`:

```ts
import react from "@vitejs/plugin-react";
import tsconfigPaths from "vite-tsconfig-paths";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [tsconfigPaths(), react()],
  test: {
    environment: "jsdom",
    globals: true,
    restoreMocks: true,
    setupFiles: ["./src/test/setup.ts"],
  },
});
```

Create `frontend/src/test/setup.ts`:

```ts
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(() => cleanup());
```

- [ ] **Step 4: Write and run the harness smoke test**

Create `frontend/src/test/harness.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

describe("frontend test harness", () => {
  it("renders React in jsdom with DOM matchers", () => {
    render(<button type="button">Tạo đợt nội trú</button>);
    expect(screen.getByRole("button", { name: "Tạo đợt nội trú" })).toBeEnabled();
  });
});
```

Run: `pnpm test -- src/test/harness.test.tsx`

Expected: 1 test passes.

- [ ] **Step 5: Commit the harness**

```powershell
git add frontend/package.json frontend/pnpm-lock.yaml frontend/vitest.config.mts frontend/src/test/setup.ts frontend/src/test/harness.test.tsx
git commit -m "test(frontend): add vitest harness"
```

### Task 2: Build admission form validation with TDD

**Files:**
- Create: `frontend/src/features/inpatient/form.test.ts`
- Create: `frontend/src/features/inpatient/form.ts`
- Modify: `frontend/src/features/inpatient/types.ts`

- [ ] **Step 1: Add the exact request type**

Append to `types.ts`:

```ts
export interface CreateAdmissionRequest {
  maYeuCauNoiTru: string;
  maHoSoNguon: string;
  maBenhNhan: string;
  maKhoa: string;
  nguoiYeuCau: string;
  tomTatChanDoan: string;
  doUuTien: AdmissionPriority;
  capCuu: boolean;
  thoiGianYeuCau: string;
}
```

- [ ] **Step 2: Write failing validation and mapping tests**

Create `form.test.ts` with a valid form fixture and assertions that:

```ts
expect(validateAdmissionForm(validValues)).toEqual({});
expect(toCreateAdmissionRequest(validValues)).toEqual({
  maYeuCauNoiTru: REQUEST_ID,
  maHoSoNguon: RECORD_ID,
  maBenhNhan: PATIENT_ID,
  maKhoa: DEPARTMENT_ID,
  nguoiYeuCau: ACTOR_ID,
  tomTatChanDoan: "Viêm phổi",
  doUuTien: "URGENT",
  capCuu: false,
  thoiGianYeuCau: new Date("2026-10-09T10:30").toISOString(),
});
```

Add parameterized cases for the five UUID fields, empty and 4,001-character summaries, and an empty/invalid request datetime. Import the not-yet-created functions from `./form`.

- [ ] **Step 3: Run the focused test and verify RED**

Run: `pnpm test -- src/features/inpatient/form.test.ts`

Expected: FAIL because `./form` does not exist.

- [ ] **Step 4: Implement the minimal pure form module**

Create `form.ts` exporting:

```ts
export interface AdmissionFormValues {
  maYeuCauNoiTru: string;
  maHoSoNguon: string;
  maBenhNhan: string;
  maKhoa: string;
  nguoiYeuCau: string;
  tomTatChanDoan: string;
  doUuTien: AdmissionPriority;
  capCuu: boolean;
  thoiGianYeuCau: string;
}

export const EMPTY_ADMISSION_FORM: AdmissionFormValues = {
  maYeuCauNoiTru: "",
  maHoSoNguon: "",
  maBenhNhan: "",
  maKhoa: "",
  nguoiYeuCau: "",
  tomTatChanDoan: "",
  doUuTien: "ROUTINE",
  capCuu: false,
  thoiGianYeuCau: "",
};
```

Implement `validateAdmissionForm`, `toCreateAdmissionRequest`, and `mapAdmissionFieldErrors`. Use the shared `isUuid`, trim all text values, enforce summary length 1..4000, reject a datetime whose `Date#getTime()` is `NaN`, and map only known backend detail fields.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run: `pnpm test -- src/features/inpatient/form.test.ts`

Expected: all form tests pass.

- [ ] **Step 6: Commit the pure contract layer**

```powershell
git add frontend/src/features/inpatient/types.ts frontend/src/features/inpatient/form.ts frontend/src/features/inpatient/form.test.ts
git commit -m "feat(inpatient-ui): add admission form contract"
```

### Task 3: Add the create API method with a contract test

**Files:**
- Create: `frontend/src/features/inpatient/api.test.ts`
- Modify: `frontend/src/features/inpatient/api.ts`

- [ ] **Step 1: Write the failing API test**

Mock `@/lib/api`, call `inpatientApi.createAdmission(request)`, and assert:

```ts
expect(api.post).toHaveBeenCalledWith("/v1/inpatient/admissions", request);
```

- [ ] **Step 2: Verify RED**

Run: `pnpm test -- src/features/inpatient/api.test.ts`

Expected: FAIL because `createAdmission` is not defined.

- [ ] **Step 3: Implement the typed method**

Import `CreateAdmissionRequest` and add:

```ts
createAdmission: (body: CreateAdmissionRequest) =>
  api.post<AdmissionDTO>("/v1/inpatient/admissions", body),
```

- [ ] **Step 4: Verify GREEN and commit**

Run: `pnpm test -- src/features/inpatient/api.test.ts`

Expected: the exact API contract test passes.

```powershell
git add frontend/src/features/inpatient/api.ts frontend/src/features/inpatient/api.test.ts
git commit -m "feat(inpatient-ui): add admission create api"
```

### Task 4: Implement the create form through component tests

**Files:**
- Create: `frontend/src/features/inpatient/components/AdmissionMutationError.tsx`
- Create: `frontend/src/features/inpatient/components/CreateAdmissionForm.test.tsx`
- Create: `frontend/src/features/inpatient/components/CreateAdmissionForm.tsx`

- [ ] **Step 1: Write failing client behavior tests**

Mock `next/navigation` and `inpatientApi.createAdmission`. Cover these observable behaviors:

```ts
it("does not submit malformed identifiers", async () => {
  render(<CreateAdmissionForm />);
  await userEvent.type(screen.getByLabelText("Mã yêu cầu nội trú"), "bad-id");
  await userEvent.click(screen.getByRole("button", { name: "Tạo đợt nội trú" }));
  expect(await screen.findByText("Mã yêu cầu nội trú phải là UUID hợp lệ.")).toBeVisible();
  expect(inpatientApi.createAdmission).not.toHaveBeenCalled();
});

it("submits the exact payload and opens the created admission", async () => {
  vi.mocked(inpatientApi.createAdmission).mockResolvedValue(createdAdmission);
  render(<CreateAdmissionForm />);
  await fillValidForm();
  await userEvent.click(screen.getByRole("button", { name: "Tạo đợt nội trú" }));
  await waitFor(() => expect(replace).toHaveBeenCalledWith(
    `/inpatient/${createdAdmission.maDotNoiTru}?notice=created`,
  ));
});
```

Also cover `401` redirect, a `409` message with correlation ID, and retry visibility for a `503` but not a `400`.

- [ ] **Step 2: Verify RED**

Run: `pnpm test -- src/features/inpatient/components/CreateAdmissionForm.test.tsx`

Expected: FAIL because the component does not exist.

- [ ] **Step 3: Implement status-aware mutation errors**

Create `AdmissionMutationError.tsx` with headings for 400, 403, 404, 409 and 422, the backend message, optional correlation ID, and an optional retry button. Use feature-local Tailwind markup matching `LabMutationError` without importing the Lab feature.

- [ ] **Step 4: Implement the minimal form**

Create `CreateAdmissionForm.tsx` using `EMPTY_ADMISSION_FORM`, `validateAdmissionForm`, `mapAdmissionFieldErrors`, and `toCreateAdmissionRequest`.

Required behavior:

```ts
const created = await inpatientApi.createAdmission(toCreateAdmissionRequest(values));
router.replace(`/inpatient/${created.maDotNoiTru}?notice=created`);
```

Use labeled UUID inputs, a 4,000-character diagnosis textarea, priority select, emergency checkbox and `datetime-local` input. Disable controls while submitting, focus the first invalid field, redirect `401` to `/login`, and expose retry only when status is null or at least 500.

- [ ] **Step 5: Verify GREEN and refactor**

Run: `pnpm test -- src/features/inpatient/components/CreateAdmissionForm.test.tsx`

Expected: all component tests pass. Remove duplication without changing behavior, then rerun the test.

- [ ] **Step 6: Commit the create form**

```powershell
git add frontend/src/features/inpatient/components/AdmissionMutationError.tsx frontend/src/features/inpatient/components/CreateAdmissionForm.tsx frontend/src/features/inpatient/components/CreateAdmissionForm.test.tsx
git commit -m "feat(inpatient-ui): add admission create form"
```

### Task 5: Wire role-safe routes, actions and creation feedback

**Files:**
- Create: `frontend/src/features/inpatient/components/AdmissionCreateLink.tsx`
- Create: `frontend/src/features/inpatient/components/AdmissionCreateLink.test.tsx`
- Modify: `frontend/src/features/inpatient/components/AdmissionTable.tsx`
- Modify: `frontend/src/features/inpatient/components/AdmissionDetail.tsx`
- Create: `frontend/src/app/(dashboard)/inpatient/new/page.tsx`
- Modify: `frontend/src/app/(dashboard)/inpatient/[admissionId]/page.tsx`

- [ ] **Step 1: Write the failing role-matrix test**

Test `AdmissionCreateLink` with `ADMIN`, `DOCTOR`, `NURSE`, `CASHIER`, `MANAGER` and null. Assert only ADMIN and DOCTOR receive a link whose accessible name is `Tạo đợt nội trú` and whose href is `/inpatient/new`.

- [ ] **Step 2: Verify RED, implement the link, then verify GREEN**

Run: `pnpm test -- src/features/inpatient/components/AdmissionCreateLink.test.tsx`

Expected RED: module missing. Implement the small role-aware component, rerun, and expect all cases to pass.

- [ ] **Step 3: Add the guarded route**

Create `new/page.tsx`:

```tsx
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { CreateAdmissionForm } from "@/features/inpatient/components/CreateAdmissionForm";

export const metadata: Metadata = { title: "Tạo đợt nội trú | MediFlow" };

export default function CreateAdmissionPage() {
  return (
    <PageShell title="Tạo đợt nội trú" description="Tạo hồ sơ từ yêu cầu và hồ sơ Clinical đã được xác thực.">
      <RoleGate allowed={["ADMIN", "DOCTOR"]}>
        <CreateAdmissionForm />
      </RoleGate>
    </PageShell>
  );
}
```

Render `AdmissionCreateLink` beside the bed-list link in `AdmissionTable`.

- [ ] **Step 4: Add success feedback to the detail route**

Read `searchParams.notice` in the admission detail route, pass only `created`, and render a success status banner in `AdmissionDetail` when that value is present. Do not trust or display arbitrary query text.

- [ ] **Step 5: Run focused and full tests**

Run:

```powershell
pnpm test -- src/features/inpatient
pnpm test
```

Expected: all tests pass with zero failures.

- [ ] **Step 6: Commit route wiring**

```powershell
git add frontend/src/features/inpatient/components/AdmissionCreateLink.tsx frontend/src/features/inpatient/components/AdmissionCreateLink.test.tsx frontend/src/features/inpatient/components/AdmissionTable.tsx frontend/src/features/inpatient/components/AdmissionDetail.tsx 'frontend/src/app/(dashboard)/inpatient/new/page.tsx' 'frontend/src/app/(dashboard)/inpatient/[admissionId]/page.tsx'
git commit -m "feat(inpatient-ui): expose admission create route"
```

### Task 6: Update progress docs and run release verification

**Files:**
- Modify: `frontend/docs/services/inpatient.md`
- Modify: `frontend/docs/frontend-workboard.md`

- [ ] **Step 1: Update progress accurately**

Mark only admission creation as delivered. Keep bed assignment/transfer/release, admit, treatment,
medical discharge, close and cancellation as later bounded command slices. Keep finance and Surgery
composition blocked.

- [ ] **Step 2: Run full deterministic verification**

Run:

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm build
```

Expected: every command exits 0; test output contains zero failed tests; build completes successfully.

- [ ] **Step 3: Inspect scope and commit docs**

Run:

```powershell
git status --short
git diff --check
git diff HEAD~4 -- frontend docs/superpowers
```

Expected: only the planned frontend/test/docs files changed; no whitespace errors.

```powershell
git add frontend/docs/services/inpatient.md frontend/docs/frontend-workboard.md
git commit -m "docs(inpatient-ui): record admission create slice"
```

- [ ] **Step 4: Final review evidence**

Run `git status --short` and confirm the worktree is clean. Record the four verification commands,
test count, commit list and any environment warnings in the handoff.

