# 10 — Git Workflow

## Branches

- `main` — always buildable. Protected. No direct pushes.
- Feature: `feat/<service>-<short-desc>` e.g. `feat/patient-crud`.
- Fix: `fix/<service>-<short-desc>`. Chore/docs: `chore/...`, `docs/...`.
- One service / one concern per branch where possible (monorepo, but keep PRs focused).

## Commits (Conventional Commits)

```
<type>(<scope>): <subject>

<body — why, not what>
```

- `type`: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`.
- `scope`: the service or area — `patient`, `billing`, `gateway`, `common`, `ai-rules`, `mcp`.
- Subject: imperative, ≤ 72 chars, English or Vietnamese (be consistent within the team).
- Example: `feat(pharmacy): dispense reduces stock and publishes prescription.filled`

## Pull requests

- PR description states: what changed, which design-doc section / business rules it implements, how it was tested.
- Checklist before requesting review:
  - [ ] Follows `docs/ai/04-microservice-blueprint.md` layout.
  - [ ] Business rules from the design doc implemented **and tested**.
  - [ ] Endpoints have role checks (`07`).
  - [ ] Events published/consumed match `06` + the service doc.
  - [ ] `mvn verify` green locally.
  - [ ] Re-indexed codebase memory if structure changed (see README > Codebase Memory).
- At least one human review. AI review (`/review-pr` command / `code-reviewer` agent) is encouraged but not a substitute.

## Keeping the framework in sync

- Changing a **coding rule** → edit `docs/ai/*` (never the tool entry files). One PR, scope `ai-rules`.
- Changing **tooling** (MCP, agents, hooks) → scope `mcp` / `tooling`, and update `README.md` setup steps if the change affects onboarding.

## After pulling

```bash
git pull
# rebuild if POMs changed
mvn -q -DskipTests install
# re-index codebase memory (Claude users) — see scripts/index-codebase.*
```

## Changelog database

Mỗi commit tự động được ghi vào `CHANGELOG.db` (SQLite, committed) để dev mới clone về
xem ngay lịch sử thay đổi mà không cần đọc `git log` hay toàn bộ source.

### Xem changelog

```bash
node scripts/changelog.js                    # 20 commits gần nhất (table view)
node scripts/changelog.js --json             # output JSON (dùng cho tooling)
node scripts/changelog.js --scope patient    # lọc theo service
node scripts/changelog.js --since 2026-07-01 # lọc theo thời gian
node scripts/changelog.js --limit 5          # giới hạn số dòng
```

### Schema

| Table | Mục đích |
|-------|----------|
| `changelog` | Mỗi row = một commit: hash, author, timestamp, type, scope, file count, insertions/deletions, summary |
| `file_changes` | Chi tiết từng file thay đổi trong commit: path, type (added/modified/deleted) |

### Query trực tiếp

```bash
sqlite3 CHANGELOG.db "SELECT hash, author, message FROM changelog ORDER BY id DESC LIMIT 5"
```

### Lưu ý

- Yêu cầu `sqlite3` CLI trên PATH (Windows: winget install sqlite, macOS: brew install sqlite3, Linux: apt install sqlite3).
- Nếu không có sqlite3, script fallback sang `.changelog/changelog.jsonl`.
- Sau bootstrap, chạy `node scripts/changelog.js --init` để init schema (tự động chạy trong bootstrap script).
- Các commit cũ có thể backfill bằng: `node scripts/changelog.js --init` rồi seed qua từng commit.
