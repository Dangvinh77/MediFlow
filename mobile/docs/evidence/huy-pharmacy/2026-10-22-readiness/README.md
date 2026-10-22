# Mobile Pharmacy — task11/12 readiness, 2026-10-22

Candidate: working tree trên Huy/base `d5373058436463d5640a36423c66620103967b4c`.
[verification.json](verification.json) giữ source hashes và kết quả kiểm tra local.
Checkpoint [08/10](../2026-10-22-dispense/README.md) giữ nguyên làm lịch sử.

## Phạm vi thực hiện

Người dùng xác nhận giữ **cả phần nối app thành dependency**, cũng như phần mạng đã giữ trước.
Task11 không triển khai shared paths. Task12 thực hiện kiểm tra môi trường và phần UX regression
làm được trong feature; không có runtime app riêng, fake login, transport bypass hoặc public APIs mới.

Widget tests mới phát hiện lỗi danh mục khi xoay720×320 với text scale2: phần header và pagination
chiếm quá chiều cao, gây RenderFlex overflow. Sửa trong `pharmacy_page.dart`: các vùng điều khiển
cuộn được theo chiều cao khả dụng, giữ vùng danh sách thuốc. Không thay theme/framework/shared shell.
Thêm4 regression cases: rotation + keyboard search và back từ form chưa gửi, cả Sáng/Tối.
Quay lại không tạo thuốc, mở form lại không giữ draft đã bỏ. Không giảm font/đổi viewport để che lỗi.

## Môi trường kiểm được

| Thành phần | Kết quả |
|---|---|
| Flutter/Dart | 3.47.0 stable /3.13.0 |
| Web browsers | Chrome và Edge được Flutter nhận diện |
| Docker | Không kết nối được dockerDesktopLinuxEngine; pipe không tồn tại lúc kiểm |
| Gateway/mobile listeners | Không thấy8080,4300,4301; cũng không thấy3180/3182/8761 trong lần kiểm |
| Android | SDK36.0.0; thiếu cmdline-tools, license status unknown; không có device kết nối |
| Emulator | Có AVD Pixel_7, chưa chạy |
| Windows native | Thiếu Desktop development with C++, MSVC/CMake/Windows SDK |
| iOS | Chưa kiểm; máy Windows không có macOS/Xcode |

Nguồn: `flutter doctor -v`, `flutter devices`, `flutter emulators`, Docker API và local listeners.
Exit0 của doctor/devices không có nghĩa Android/Windows toolchain đạt; doctor báo hai nhóm vấn đề.
Không tự cài toolchain, chấp nhận license hoặc bật backend gates.

## Phân loại evidence

- Focused navigation tests: **4/4 PASS** sau khi sửa overflow.
- Full `tool/check.ps1`: **PASS exit0**, format104 files/0 changed, analyzer no issues,
  **288/288 tests PASS**, web release **PASS95,6s**, Wasm dry run PASS;
  warning CupertinoIcons có sẵn giữ nguyên. Kết quả và source hashes ở verification.json;
  raw log local `.git/mobile-pharmacy-readiness-check.log`.
- Widget rotation/keyboard/back dùng repository doubles: **LOCAL ONLY**.
- Root app DI/routes/mode/session integration: **DEPENDENCY** theo người dùng.
- Actual Gateway/issuer/clearance/stock-one-effect/browser/device/native acceptance: **NOT RUN**.

Tiến độ vẫn **8/12 DONE**;08/10 PARTIAL vì transport/adapters,11 DEPENDENCY,12 PARTIAL local.
Không thêm điểm hoàn thành bằng tests doubles hoặc build. Dependency giữ đúng
[một handoff chung](../../../../../docs/handoffs/HANDOFF-HUY-MVP.md), không tạo nhiều file handoff.
