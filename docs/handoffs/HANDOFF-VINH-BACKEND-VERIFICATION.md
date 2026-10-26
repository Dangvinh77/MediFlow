# Vinh — kiểm thử toàn bộ backend MVP

## Điều phối hiện hành — 26/10/2026

- Registry ID: `H-09`; status: `NEEDS REVALIDATION`.
- Bàn giao hiện tại: Tất cả backend owner → Vinh verification.
- [Registry và phần acceptance còn thiếu](README.md) là nguồn giao việc hiện tại.
- Các base/branch, status, owner và test counts trong checkpoint dưới giữ theo ngày;
  không là bằng chứng bug còn tồn tại hoặc task phải code lại trên HEAD.
- Nghiệp vụ mới dùng [WF-01–11](../plans/07-outpatient-workflow-implementation.md), không đóng từ evidence baseline.

## Hồ sơ phát hiện và checkpoint

**Phân công người dùng:** 2026-10-18. **Owner kiểm thử:** Vinh (`Harori` / `Dangvinh77`).
**Trạng thái:** ĐÃ CHẠY regression và selected acceptance; CHƯA ĐÓNG vì hai Gateway routes, flaky test Report và Pharmacy V1 opt-in timeout do thiếu publisher config.
[Kết quả thực chạy](../acceptance/backend-2026-10-18/README.md) · [Bảng giao việc cuối](history/2026-10-18-backend-assignments.md).
**Baseline rà soát:** master `025ef39e` đã gồm PR #427. Khóa final acceptance revision sau
hai fixes Gateway của Hoàng Anh; baseline này chưa có fixes đó.

## Ranh giới

Vinh viết/chạy/đối soát toàn bộ kiểm thử backend, tổng hợp nghiệm thu. Dev phụ trách module
nhận sửa production khi có bug tái hiện được, rồi trả commit để Vinh retest.
Không giao lại full regression/integration cho Huy/Lộc/Hoàng Anh. Phân công này bao gồm test
changes cần thiết trong modules được kiểm thử; không mở quyền sửa production Core/Gateway/
Pharmacy/Report/Support/shared sang Vinh. CI/root build/Compose/runtime wiring cần task riêng.

## Queue Vinh

| ID | Việc thực hiện | Điều kiện nghiệm thu |
|---|---|---|
| V-TEST-01 | Khóa final source/fixtures, build fresh artifacts, inventory profiles/flags | SHA/dirty diff, tool/image versions, migrations/checksums; không dùng JAR cũ |
| V-TEST-02 | Full Core, Gateway, Clinical, Lab, Pharmacy, Billing, Report, Notification, Support và shared dependencies | Counts/failures/errors/skips/exit từng module; selected tests không thay full Core/Pharmacy |
| V-TEST-03 | Nhận HA-CODE-01/02, viết/chạy route/token/role/forwarding tests và actual refund slice | Exact routes pass tới Billing; wrong role/method/nested path deny; giữ signed actor/correlation, retry/race/over-refund guards |
| V-TEST-04 | Packaged/distributed EXAM, LAB_TEST, PRESCRIPTION cùng revision | Check-in/pay/exam/Lab pay/result/Clinical attachment; Rx pay/dispense/Report/in-app; replay/terminal compensation đúng |
| V-TEST-05 | Recovery, sáu deployments và cutover acceptance theo runbook | Crash sau commit trước ACK, outbox/lease/quarantine/HELD, broker restart, Support isolation/rollback giữ accepted writes |
| V-TEST-06 | Đối chiếu T01–T19/M6-01–18, CI và tổng hợp evidence/blockers | PASS/FAIL/BLOCKED/NOT RUN từng scenario, bug/task giao đúng dev; không suy global done từ counts cũ |

V-TEST-01/02 và tests độc lập có thể bắt đầu trước fixes Gateway. V-TEST-03/final closure
cần commit HA-CODE-01/02. Refund dùng actual original payment và live DTO, không giả IDs
hoặc ghi SQL hộ business authority.

## Lệnh/profiles hiện có

Đọc module AGENTS/README và prerequisites trước khi chạy. Build trước packaged acceptance;
không compile/package cùng module đang test. Các lệnh dưới là tham chiếu; exact commands và exit đã chạy nằm trong evidence. Dùng quote các `-D...` khi gọi Maven qua PowerShell:

```powershell
mvn -q test
mvn -q -DskipTests package
mvn -q -pl backend/billing-service -am -Poutpatient-publication-acceptance verify
mvn -q -pl backend/clinical-service -am -Ppackaged-acceptance verify
mvn -q -pl backend/clinical-service -am -Pprocess-recovery verify
mvn -q -pl backend/lab-service -am -Ppackaged-slice '-Dlab.acceptance.billing.jar=<absolute Billing jar>' verify
mvn -q -pl backend/lab-service -am -Pprocess-recovery verify
mvn -q -pl backend/pharmacy-service -am -Pdistributed-acceptance verify
mvn -q -pl backend/support-service -am -Psupport-acceptance verify
mvn -q -pl backend/support-service -am -Psix-service-acceptance verify
pwsh -NoProfile -File backend/support-service/operations/Invoke-HistoricalSupportRehearsal.ps1 -SourcePgVolume source_mediflow-pgdata -SourceRabbitVolume source_mediflow-rabbitdata
```

Kiểm cả Surefire/Failsafe, selected IT và skips; exit 0 mà IT không được chọn không là pass.
Reactor giữ compatibility tests của deferred modules; chạy chúng không kích hoạt Inpatient/
Surgery hoặc mở backlog feature. Historical-support dùng clone/cold copies theo runbook,
không test trực tiếp live stores/volumes. Thiếu prerequisites ghi BLOCKED, tiếp tục phần độc lập.

## Evidence và báo cáo

Evidence nền: [bundle Vinh/Lộc 18/10](../../backend/billing-service/acceptance-evidence/2026-10-18/README.md),
[Huy integrated/persisted-volume rehearsal](../plans/huy-mvp-cutover-runbook.md),
[matrix T01–T19](../plans/06-acceptance-runbook.md).
Counts 3349 của PR #427 và Huy 48/48 là lịch sử theo revision/phạm vi tương ứng;
không cộng trùng reruns hoặc gọi thành full-suite final acceptance.

Lưu commands/exit codes, full/selected counts, SHA/flags/profiles, raw event hashes,
test IDs, expected/actual và report paths trong bundle bền vững có manifest. Không chỉ trỏ
`target/`, không lưu secrets/token/PII. Giữ DLQ/HELD evidence, không purge/release/nới guards.

| Task/scenario | Source SHA | Kết quả | Command/counts/exit | Evidence/hash hoặc bug/dev code |
|---|---|---|---|---|
| V-TEST-01 | 025ef39e + test acceptance Gateway | PASS build | Fresh reactor install, exit 0, không chạy tests | Tool/image/test hash trong evidence |
| V-TEST-02 | 025ef39e | FAIL | 6854 tests; 1 failure Report; 0 errors/skips | [V-TEST-BUG-01](history/2026-10-19-test-harness-findings.md), Vinh sửa test |
| V-TEST-03 | 025ef39e + test Gateway | BLOCKED | 11 filter acceptance cases; 4 failures, 7 pass | HA-CODE-01/02 Hoàng Anh code; Vinh retest token/HTTP/actual refund |
| V-TEST-04/05 | 025ef39e | Selected slices đã chạy | Lab đủ property pass; historical clone rehearsal pass; Pharmacy V1 opt-in 1 error cần V-TEST-BUG-02 | [Evidence](../acceptance/backend-2026-10-18/README.md); không suy full matrix pass |
| V-TEST-06 | Chưa có Gateway fix revision | OPEN | Full final regression, CI/FE/global signoff chưa hoàn tất | Vinh nhận commit fixes và tổng hợp cuối |

Nếu có lỗi, thêm source path, bước tái hiện, expected/actual và dev code vào [registry](README.md).
Vinh nhận lại/retest; kết quả local không tự cấp production activation hoặc ký coverage môi trường khác.
