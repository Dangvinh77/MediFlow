# 17 — Dọn Pull Request, nhánh và worktree thủ công

Tài liệu này dùng sau khi một Pull Request (PR) đã có đủ check xanh. Mục tiêu là merge PR, xóa
nhánh remote, gỡ worktree và xóa nhánh local mà không dùng lệnh xóa cưỡng bức.

## 1. Khai báo đúng mục tiêu

Mở PowerShell tại checkout chính của repository rồi thay bốn giá trị dưới đây:

```powershell
$repoPath = "E:\DEV\Coding_Resource\Project\e_PROJECT\Semester4\Source"
$prNumber = 358
$branchName = "codex/inpatient-placement-ci-fix"
$worktreePath = "C:\Users\VIP\.codex\worktrees\inpatient-placement-authority\Source"

Set-Location -LiteralPath $repoPath
```

Không dùng `$HOME`, `~`, thư mục gốc ổ đĩa hoặc đường dẫn được ghép động làm `$worktreePath`.

## 2. Kiểm tra trước khi merge

```powershell
gh pr view $prNumber --json url,state,isDraft,mergeable,headRefName,baseRefName,statusCheckRollup
gh pr checks $prNumber
git worktree list
git -C $worktreePath status --short --branch
```

Chỉ tiếp tục khi:

- PR đang `OPEN`, không phải draft và `MERGEABLE`.
- Tất cả required checks đều `pass`.
- `headRefName` đúng bằng `$branchName`.
- `$worktreePath` xuất hiện trong `git worktree list`.
- Worktree không có code chưa commit. File do hook sinh ra phải được xem riêng; không dùng `--force`
  để bỏ qua vì có thể xóa nhầm công việc thật.

## 3. Merge và xóa nhánh remote

```powershell
gh pr merge $prNumber --merge --delete-branch
gh pr view $prNumber --json state,mergedAt,mergeCommit,url
git fetch origin --prune
```

Nếu repository yêu cầu squash commit, thay `--merge` bằng `--squash`. Không merge khi check đang
pending hoặc failed.

Nếu PR đã merge nhưng nhánh remote vẫn còn:

```powershell
git ls-remote --heads origin $branchName
git push origin --delete $branchName
```

## 4. Gỡ worktree và nhánh local

Kiểm tra lại đường dẫn tuyệt đối trước khi gỡ:

```powershell
$resolvedRepo = (Resolve-Path -LiteralPath $repoPath).Path
$resolvedWorktree = (Resolve-Path -LiteralPath $worktreePath).Path

$resolvedRepo
$resolvedWorktree
git worktree list
git -C $resolvedWorktree status --short --branch
```

Nếu hai đường dẫn đúng và worktree không còn thay đổi cần giữ:

```powershell
Set-Location -LiteralPath $resolvedRepo
git worktree remove $resolvedWorktree
git branch -d $branchName
git worktree prune
```

`git branch -d` sẽ từ chối xóa nếu Git chưa xác nhận nhánh đã merge. Dừng lại để kiểm tra nguyên
nhân; không đổi sang `git branch -D` hoặc `git worktree remove --force` theo phản xạ.

## 5. Đồng bộ checkout chính và xác nhận đã sạch

```powershell
Set-Location -LiteralPath $resolvedRepo
git switch master
git pull --ff-only origin master

git status --short --branch
git branch --list $branchName
git ls-remote --heads origin $branchName
git worktree list
```

Kết quả mong đợi:

- PR có trạng thái `MERGED`.
- Không còn nhánh local hoặc remote mang `$branchName`.
- Không còn `$worktreePath` trong `git worktree list`.
- Checkout `master` đã đồng bộ và không có thay đổi ngoài ý muốn.

## Khi worktree thuộc Codex

Worktree do Codex quản lý nên được **Archive worktree** trong giao diện Codex sau khi PR merge.
Thao tác này giữ snapshot có thể khôi phục rồi mới gỡ checkout. Nếu dọn hoàn toàn bằng PowerShell,
hãy làm đúng các kiểm tra ở trên và chỉ gỡ đúng đường dẫn tuyệt đối đã xác nhận.
