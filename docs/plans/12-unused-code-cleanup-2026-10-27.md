# Dọn mã không còn được dùng — 27/10/2026

Baseline: `7f03530d` (PR #544 đã merge). Phạm vi là xóa mã không có nơi gọi trong
luồng đang chạy, không thay hợp đồng HTTP/event, migration hay activation gates.

## Những phần đã bỏ và bằng chứng

| Phần | Lý do |
|---|---|
| Clinical `ProcessedEventPort`, adapter, JPA repository/entity | Không có production caller. Chỉ test persistence cũ dùng port/adapter; consumer hiện dùng `ConsumerInboxPersistenceAdapter` qua transaction boundary. |
| Lab `ProcessedEventPort` và adapter | Không có production caller. Giữ repository/entity vì `LabClearancePersistenceAdapter` vẫn dùng chúng. |
| Lab `ReactToClinicalUseCase.autoCreateFromRecord` | Chỉ còn một test gọi với lab type trống. Consumer thực tế dùng `ReactToMedicalRecordUseCase` receipt-only; chỉ định mới đi qua luồng Lab order rõ nguồn. Không còn subscriber gọi đường tự tạo test từ record này. |
| Clinical `hasActiveRegistrations` | Không có caller ở production/test. Domain `ConsultationSession` đã kiểm tra occupied counts khi đổi cấu hình/hủy; không sửa các guard đang chạy. |
| Frontend `/loading-test` | Trang demo loader độc lập, không có navigation/import nghiệp vụ. Giữ nguyên package loader và các loading states đang dùng. |
| Frontend `harness.test.tsx` | Chỉ render một button literal để thử jsdom; các test component thực đã dùng cùng setup và DOM matchers. |

Xóa 4 backend test chỉ kiểm tra các đường vừa bỏ: 1 Clinical claim, 2 Lab claim,
1 Lab auto-create với type trống. Giữ kiểm tra chống trùng clearance Lab bằng cách
gọi trực tiếp repository đang dùng trong production. Không xóa test do nó đang fail.

`SupportStoreIsolationTest` vẫn giữ nguyên kiểm tra pool, schema và lịch sử migration;
cập nhật danh sách Notification thành V1–V6 để khớp migration V6 đã có ở baseline.
Đây là test expectation cũ gây failure trong CI của PR #544.

## Những phần được giữ

- Surgery/Inpatient source, test, route, integration contracts và các cổng mở rộng liên quan.
- Tất cả SQL/migration và dữ liệu lịch sử. Bảng `processed_event` vẫn được consumer inbox
  Clinical/Lab đọc để từ chối legacy receipt không có raw evidence; không drop hay backfill bảng.
- Inbox byte identity, dedupe, transaction/rollback, upgrade, finance/security/ownership tests.
- Standalone Report/Notification launchers và recovery tools dùng cho rollback/cutover.
- Frontend contract fixtures chỉ được test import; đây là test data đang dùng, không phải dead code.

Khảo sát dùng codebase-memory trên worktree rồi kiểm chứng tên symbol/import/literal trực tiếp.
Graph không chứng minh callback/framework hook không dùng; các entry points và bean đang được
inject không bị xóa chỉ vì thiếu edge. Rà import TypeScript không tìm thấy module production
mồ côi ngoài các contract fixtures được test dùng. Đây là đợt dọn có bằng chứng, không tuyên bố
toàn repository đã hết dead code.

## Kiểm chứng

Trên nhánh `codex/remove-unused-code`:

- `mvn -q -pl backend/clinical-service,backend/lab-service -am clean test`: exit 0;
  Clinical 435 test / 76 suites, Lab 271 test / 41 suites, không failure/error/skip.
  Clean build tránh giữ lại `.class` của source đã xóa. Container PostgreSQL/RabbitMQ chạy thật.
- `mvn -q -pl backend/support-service -am '-Dtest=SupportStoreIsolationTest'
  '-Dsurefire.failIfNoSpecifiedTests=false' package`: exit 0; 5 test, không failure/error/skip.
  Đây là targeted store-isolation test, không phải toàn bộ packaged Support acceptance.
- `node node_modules/vitest/vitest.mjs run --maxWorkers=2` từ frontend: 119 file,
  1.097 test đạt, exit 0 (giảm đúng 1 test harness).
- `node node_modules/next/dist/bin/next typegen`, TypeScript `--noEmit`, ESLint và
  Next production build: exit 0. Tạo lại route types sau khi xóa trang demo để loại
  reference `.next/types` cũ. Build không còn route `/loading-test`.
- `git diff --check`: đạt. Diff không sửa SQL/migration, Surgery/Inpatient hay các
  integration ports trong Billing/Notification/Report.
- Review độc lập: không blocker/should-fix; đã sửa README Lab còn nhắc port/adapter đã xóa.

Không chạy lại toàn bộ monorepo, packaged Gateway/browser hay các acceptance suites
Surgery/Inpatient trong lượt cleanup; giữ nguyên source/test của các phần này.
