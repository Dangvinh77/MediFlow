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

## Changelog harness

Mỗi commit tự động được ghi vào `.changelog/entries.jsonl` (text, 1 dòng/commit)
để dev mới clone về xem ngay lịch sử thay đổi mà không cần đọc `git log` hay
toàn bộ source. SQLite cache (`.changelog/cache.db`) được rebuild từ JSONL cho
query nhanh — file này gitignored.

### Cách hoạt động với multi-branch

JSONL là text file, mỗi dòng 1 commit. Khi 2 branch cùng append dòng mới:

1. **Auto-merge:** Git merge recursive ghép nội dung ở cuối file → các dòng mới
   ở 2 branch được giữ nguyên, không conflict.
2. **Conflict:** Chỉ xảy ra nếu 2 branch cùng sửa *cùng dòng*. Khi đó conflict
   markers xuất hiện ở `.changelog/entries.jsonl`.
3. **Sau merge:** `post-merge` hook tự động chạy `--rebuild` để rebuild cache
   từ JSONL. Nếu có conflict, resolve conflict markers rồi chạy:

```bash
node scripts/changelog.js --dedup    # xoá entries trùng hash
node scripts/changelog.js --rebuild  # rebuild cache.db từ JSONL sạch
```

### Xem changelog

```bash
node scripts/changelog.js                    # TẤT CẢ commits (table view)
node scripts/changelog.js --limit 10         # giới hạn kết quả
node scripts/changelog.js --json             # output JSON (dùng cho tooling)
node scripts/changelog.js --summary          # thống kê (total, by type, by author)
node scripts/changelog.js --files            # xem chi tiết từng file + tác giả + số dòng
node scripts/changelog.js --scope feat       # lọc theo type hoặc service
node scripts/changelog.js --since 2026-07-01 # lọc theo thời gian
```

### Xử lý sự cố

```bash
# Rebuild cache sau khi conflict resolve
node scripts/changelog.js --dedup
node scripts/changelog.js --rebuild

# Init từ đầu (scan toàn bộ git log)
node scripts/changelog.js --init
```

### Lưu ý

- Yêu cầu `sqlite3` CLI trên PATH (Windows: winget install sqlite, macOS: brew install sqlite3, Linux: apt install sqlite3).
- Nếu không có sqlite3, vẫn đọc được entries từ JSONL nhưng query chậm hơn.
- `post-commit` hook ghi entries.jsonl + update cache. `post-merge` hook rebuild cache.
