# 10 — Git Workflow

## Branches

- `master` — always buildable. Protected. No direct pushes.
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

## Git hooks (single-author policy)

- Hook scripts nằm trong repo tại `scripts/git-hooks/`, nhưng git **chỉ chạy** chúng khi config
  `core.hooksPath` trỏ tới đó — và setting này nằm trong `.git/config`, **local trên từng máy,
  không đi theo `git clone`**. Đây là lý do commit từ máy dev khác có thể "sót" dòng
  `Co-Authored-By: Claude` trong khi máy bạn thì không: Claude Code tự thêm trailer đó, và nếu
  máy đó chưa bật hook thì không gì chặn.
- `prepare-commit-msg` xoá mọi dòng `Co-Authored-By:`; `commit-msg` **từ chối** (exit 1) commit
  nào còn sót — agent (Claude, Codex, Copilot, ...) không bao giờ được ghi là đồng tác giả.

Bật hooks sau khi clone (1 lần):

```bash
git config core.hooksPath scripts/git-hooks   # tương đương: scripts\setup-hooks.bat / bash scripts/setup-hooks.sh
```

> `bootstrap.ps1` / `bootstrap.sh` làm việc này tự động. Dev đã có clone cũ → chạy lệnh trên.
> Chỉnh hook: sửa `scripts/git-hooks/*` rồi commit (scope `tooling`).

## Changelog harness

Mỗi commit tự động được ghi vào `.changelog/entries.jsonl` (text, 1 dòng/commit)
để dev mới clone về xem ngay lịch sử thay đổi mà không cần đọc `git log` hay
toàn bộ source. SQLite cache (`.changelog/cache.db`) được rebuild từ JSONL cho
query nhanh — file này gitignored.

Hook local là cơ chế ghi nhanh, không phải cơ chế thu thập duy nhất. Merge commit được tạo trực
tiếp trên GitHub không chạy hook của máy dev, vì vậy workflow trên `master` chạy
`node scripts/changelog.js --sync` để bổ sung các commit còn thiếu từ Git history. Sync giữ
commit thường và merge commit của thành viên, nhưng loại tài khoản `[bot]` và commit chỉ dùng
để tự cập nhật dashboard.

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
node scripts/changelog.js --sync              # bổ sung commit Git còn thiếu vào JSONL
```

### Commit activity dashboard

`README.md` chứa bảng thành viên và hai biểu đồ SVG được sinh từ toàn bộ lịch sử trong
`.changelog/entries.jsonl`. Chạy lệnh sau để kiểm thử và cập nhật dashboard ở local:

```bash
node --test scripts/changelog-sync.test.js scripts/commit-activity.test.js
node scripts/commit-activity.js
```

Mọi push lên `master` đều kích hoạt `.github/workflows/update-commit-activity.yml`. Workflow
đồng bộ commit Git còn thiếu trước khi sinh dashboard, rồi đưa changelog, README và hai SVG vào
nhánh cố định `automation/commit-activity-dashboard`. Nó mở hoặc làm mới một pull request duy
nhất và bật auto-merge nếu cấu hình repository cho phép. Lần chạy sau khi PR automation được merge
sẽ bỏ qua chính commit bot/dashboard đó và thoát khi không còn thay đổi, nên không tạo vòng lặp.

README gắn digest nội dung vào URL của từng SVG. Khi dữ liệu hoặc alias thay đổi, URL cũng đổi để
GitHub không hiển thị ảnh cache cũ. Dòng `Changelog updated through` là thời gian của commit hợp lệ
mới nhất đã được ghi trong changelog, không phải thời gian workflow chạy. Email chỉ được dùng nội bộ
để nhận diện thành viên và không bao giờ xuất hiện trong README hoặc biểu đồ.

Để workflow có thể mở PR bằng `GITHUB_TOKEN`, maintainer phải bật **Settings → Actions →
General → Workflow permissions → Allow GitHub Actions to create and approve pull requests**.
Nếu repository yêu cầu duyệt workflow hoặc review PR, maintainer vẫn phải thực hiện bước duyệt;
auto-merge chỉ chạy sau khi mọi rule bảo vệ nhánh đã đạt.

Khi một thành viên commit bằng nhiều email, khai báo một canonical contributor trong
`.changelog/contributor-aliases.json`. Mỗi email chỉ được thuộc một contributor; generator sẽ
dừng với lỗi rõ ràng nếu ID hoặc email bị trùng. Email không có trong registry vẫn được nhóm độc
lập theo email chuẩn hóa.

### Xử lý sự cố

```bash
# Rebuild cache sau khi conflict resolve
node scripts/changelog.js --dedup
node scripts/changelog.js --rebuild

# Init từ đầu (scan toàn bộ git log)
node scripts/changelog.js --init

# Bổ sung commit bị thiếu do merge trên GitHub hoặc máy chưa bật hook
node scripts/changelog.js --sync
```

### Lưu ý

- Yêu cầu `sqlite3` CLI trên PATH (Windows: winget install sqlite, macOS: brew install sqlite3, Linux: apt install sqlite3).
- Nếu không có sqlite3, vẫn đọc được entries từ JSONL nhưng query chậm hơn.
- `post-commit` hook ghi entries.jsonl + update cache. `post-merge` hook rebuild cache.
- Workflow `master` tự bù commit bị thiếu; hook local vẫn nên bật để changelog hữu ích ngay trước
  khi push.
