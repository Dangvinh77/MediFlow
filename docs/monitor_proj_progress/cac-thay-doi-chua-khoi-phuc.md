# Báo cáo khôi phục thay đổi từ stash

Ngày đối chiếu: 2026-09-04  
Nguồn giữ dữ liệu: `stash@{0}` — `codex: preserve local changes before merge to master`  
Trạng thái: **đã khôi phục toàn bộ vào workspace chính**; danh sách dưới đây được giữ làm biên bản đối chiếu.

## Tóm tắt

- 8 file mới đã được khôi phục vào workspace hiện tại.
- Phần chỉnh sửa của 2 file đang tồn tại đã được hợp nhất với nội dung mới trên `master`.
- 3 báo cáo Word Giai đoạn 1–3 đã được khôi phục và push, nên không nằm trong danh sách bị thiếu.

## 1. Các file mới đã được khôi phục

### Tài liệu Giai đoạn 1

1. `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/giai-doan-1-crs-bieu-mau.md` — 32,628 byte.
2. `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/kien-truc-tong-quan-mediflow.svg` — 15,369 byte.
3. `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/kien-truc-tong-quan-mediflow-word-safe.svg` — 17,193 byte.
4. `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/kien-truc-tong-quan-mediflow-word-safe.png` — 354,920 byte.
5. `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/luong-rest-event-va-saga.svg` — 12,270 byte.
6. `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/luong-rest-event-va-saga-word-safe.svg` — 13,917 byte.
7. `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/assets/luong-rest-event-va-saga-word-safe.png` — 367,350 byte.

### Tài sản thử nghiệm Giai đoạn 2

8. `docs/monitor_proj_progress/02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/assets/diagrams/png/06-erd-pharmacy-font-test.png` — 255,236 byte.

## 2. Các chỉnh sửa đã được áp dụng

### `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/README.md`

Phiên bản trong stash có 16 dòng thêm và 4 dòng xóa so với trạng thái trước khi stash. Nội dung chính:

- Thêm liên kết đến `giai-doan-1-crs-bieu-mau.md`.
- Thêm mục sơ đồ kiến trúc SVG.
- Thêm liên kết đến hai sơ đồ kiến trúc và luồng REST/Event/Saga.
- Thêm hướng dẫn sử dụng bản Word-safe SVG hoặc PNG 2× trong Microsoft Word.
- Cập nhật thứ tự tài liệu cần nộp cho giáo viên.

### `.changelog/entries.jsonl`

Stash từng chứa 15 dòng changelog chưa có trong file hiện tại:

1. `9ba94ca` — `docs: Update monitor_proj_process`
2. `ac51227` — `Merge origin/master into master`
3. `d79a419` — `docs: define english database naming design`
4. `2a77f7e` — `docs: plan english database naming update`
5. `7538e01` — `docs: normalize stage two database names`
6. `dc3f98b` — `docs: translate remaining database diagrams`
7. `67fb11c` — `docs: align organization and patient schemas in english`
8. `f84647c` — `docs: align notification schema in english`
9. `ddc0101` — `docs: regenerate english database diagrams`
10. `2220180` — `docs: include complete stage two diagram set`
11. `f2abd3f` — `chore: normalize documentation whitespace`
12. `cb22c8d` — `docs: design detailed service erd set`
13. `1e38c36` — `docs: plan detailed service erd expansion`
14. `6ddd891` — `chore: normalize detailed erd spec whitespace`
15. `a1d84c9` — `fix(docs): normalize detailed ERD flow fonts`

Các dòng này đã được bổ sung theo `hash` bằng changelog harness. File hiện tại sau đó được deduplicate và rebuild, nên không ghi đè lịch sử mới trên `master`.

## 3. Các file Word đã được khôi phục

Ba file sau đã có trong `master` tại commit `e275a63` và đã được push lên `origin/master`:

- `docs/monitor_proj_progress/BÁO CÁO GIAI ĐOẠN 1.docx`
- `docs/monitor_proj_progress/BÁO CÁO GIAI ĐOẠN 2.docx`
- `docs/monitor_proj_progress/BÁO CÁO GIAI ĐOẠN 3.docx`

Blob của ba file trên trong `master` trùng với blob được lưu trong stash.

## 4. Lưu ý về file tạm Word

Workspace có thể xuất hiện file bắt đầu bằng `~$` khi tài liệu đang mở trong Microsoft Word. Đây là file khóa tạm, không phải báo cáo gốc và không nên commit.

## 5. Cách khôi phục đã thực hiện

- Đã khôi phục riêng 8 file mới từ parent chứa file untracked của stash: `stash@{0}^3`.
- Đã hợp nhất thủ công thay đổi README để giữ cả nội dung mới trên `master`.
- Đã bổ sung các commit còn thiếu, deduplicate và rebuild changelog; kết quả có 96 entry duy nhất.
- Chỉ xóa `stash@{0}` sau khi commit, push và xác minh toàn bộ nội dung trên remote.
