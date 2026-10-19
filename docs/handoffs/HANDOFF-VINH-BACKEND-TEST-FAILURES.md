# Vinh — lỗi kiểm thử mới sau regression 18/10/2026

## Recheck 19/10 — phân biệt nguồn chung và sửa local

Remote master `4c19dd4a` và tag `v0.9.0-backend-feature-complete` chưa chứa hai sửa
test bên dưới. Vinh đã áp dụng bản sửa test ở branch `codex/backend-loc-verification`;
commands, source lock và kết quả sau sửa ở [bundle 19/10](../acceptance/backend-recheck-2026-10-19/README.md).
Full Report sau sửa 1.331/1.331 và hai targeted reruns 8/8 mỗi lượt; Pharmacy opt-in
1/1 pass, default 2 pass/1 explicit skip. Tất cả các lượt sau sửa exit 0.
Hai fix đã được kiểm chứng local, chưa có trên remote. Full reactor và actual refund E2E
không chạy lại ngày 19/10; không ký các acceptance rộng hơn từ những lượt chọn này.
Giữ phiếu cho đến khi fix được chia sẻ và acceptance rộng hơn đã đủ bằng chứng; không xóa
failure lịch sử hoặc coi một targeted rerun xanh là toàn backend đã nghiệm thu.

Gateway code đã sửa tại `2805b07c` / PR #431. Recheck ngày 19/10: explicit refund
11/11 và full Gateway 1.449/1.449 pass. Ghi chú bốn positive cases đỏ phía dưới là lịch sử.

## Báo cáo lỗi lịch sử trước fix

**Owner sửa test và retest:** Vinh. **Baseline:** master `025ef39e358756a28043d9809fad165096528fbf`.
Đây là phiếu lỗi test; chưa có bằng chứng yêu cầu Huy sửa production Report.

## V-TEST-BUG-01 — Report so sánh hai đồng hồ gây flaky (P2, OPEN)

- Source: `backend/report-service/src/test/java/com/mediflow/report/infrastructure/persistence/ReportTelemetryPostgresTest.java`,
  method `emptySnapshot_hasZeroLocalBacklogButDoesNotPublishCoverage`, dòng 53.
- Tái hiện: `mvn -B -ntp test`. Full reactor dừng ở Report; một failure trong 1.331 tests Report.
  Các module sau Report đã được chạy riêng để có đủ regression.
- Expected: snapshot rỗng không backlog/coverage, timestamp hợp lệ.
- Actual: `sampledAt=2026-10-18T07:36:47.586162Z` lớn hơn host bound
  `2026-10-18T07:36:47.586105700Z` khoảng 56,3 microsecond.
- `ReportTelemetryPersistenceAdapter` lấy `statement_timestamp()` từ PostgreSQL;
  test so sánh trực tiếp với `Instant.now()` của JVM Windows. Hai đồng hồ không có bảo đảm cùng offset.
  `ReportTelemetryCollector` đã dùng local receipt clock cho freshness.
- Chạy lại riêng `mvn -B -ntp -pl backend/report-service -Dtest=ReportTelemetryPostgresTest test`
  hai lần, mỗi lần 8/8 pass. Kết quả này không xóa failure của full regression.

**Cách sửa test:** chặn thời gian trước/sau capture bằng chính đồng hồ DB (ví dụ
`clock_timestamp()` trên DB test), giữ assertions backlog/coverage/freshness có ý nghĩa.
Không thêm sleep hoặc đổi timestamp production chỉ để assertion xanh.

**Acceptance:** test sửa bắt được sampledAt sai, ổn định qua nhiều lượt; full Report suite và
full backend regression trên revision nhận Gateway fixes đạt; lưu counts/exit/reports mới.
Nếu xuất hiện sai nghiệp vụ độc lập với clock assertion, tạo bug production riêng cho Huy.

## V-TEST-BUG-02 — Pharmacy opt-in không cấu hình Billing publisher (P2, OPEN)

**Owner:** Vinh sửa test harness và retest. Không giao production Billing cho Lộc hoặc
production Pharmacy cho Huy chỉ từ timeout này.

- Test `PharmacyOutpatientRuntimeAcceptanceIT.actualV1IssuerGrantSurvivesPharmacyJvmRestartAndDuplicatesNeverAutoDispense`.
- Tái hiện với `distributed-acceptance`, `-Dit.test=PharmacyOutpatientRuntimeAcceptanceIT#actualV1IssuerGrantSurvivesPharmacyJvmRestartAndDuplicatesNeverAutoDispense`,
  `-Dmediflow.acceptance.billing-v1-published=true`, và hai flags failIfNoSpecifiedTests=false
  như exact command trong evidence. 1 test, 0 failures, **1 error**, 0 skips; exit 1.
- Actual: chờ `invoice.created` tại dòng 133 qua `captured` dòng 185 timeout 35 giây.
  Không tới bước trả tiền/restart/duplicate assertions.
- Source harness Billing container dòng 38–42 chỉ bật ledger, prescription consumer và
  clearance lookup. System property opt-in chỉ mở điều kiện JUnit; không truyền ba publication
  settings vào container. `application.yml` Billing dòng 49–52 mặc định publication=false,
  purposes rỗng và acceptance-reference rỗng. Không có container override cho chúng trong helper.
- Đây là mismatch cấu hình test đã xác nhận từ source; cơ chế HELD phù hợp contract hiện tại.
  Không có DB row/log payload đã giữ để khẳng định trạng thái từng event trong lượt timeout.
  Six-service actual EXAM/Rx đã pass với publication enabled/purposes/reference được cấu hình.

**Cách xử lý:** khi explicit opt-in sau producer acceptance, cấu hình Billing container isolated
với publication enabled, purpose PRESCRIPTION và acceptance reference test-only trước startup.
Giữ default opt-out/HELD, không sửa application defaults hoặc release historical rows.
Giữ failure ở timeout nếu event thực sự không xuất hiện; không nới timeout để che thiếu cấu hình.

**Acceptance:** rerun opt-in bắt đủ invoice/payment/grant, Pharmacy restart hai lần, duplicate
không auto-dispense/stock effect. Rerun default profile giữ hai controls pass và explicit skip
được ghi đúng. Nếu vẫn lỗi khi đã cấu hình đúng thì mở bug production riêng kèm bytes/log/DB
owned-context evidence đã lọc cho đúng owner. Hiện chưa ký actual issuer-chain ca này.

## Điều kiện chạy được phát hiện, không phải lỗi production

- Lab `packaged-slice` cần truyền `-Dlab.acceptance.billing.jar=<absolute packaged Billing JAR>`.
  Lượt đầu thiếu property thất bại trước business flow. Vinh giữ command đủ tham số trong evidence.
- Pharmacy mặc định bỏ qua `actualV1IssuerGrantSurvivesPharmacyJvmRestartAndDuplicatesNeverAutoDispense`
  vì thiếu `mediflow.acceptance.billing-v1-published=true`. Chỉ opt-in disposable acceptance
  sau producer acceptance; không release HELD rows hoặc đổi cờ môi trường thật.
- Gateway có phiếu riêng [HA-CODE-01/02](HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md).
  Bốn positive cases hiện đỏ, bảy negative controls pass. Vinh không sửa production Gateway.

[Evidence đầy đủ](../acceptance/backend-2026-10-18/README.md) ·
[Bảng giao việc cuối](BACKEND-FINAL-ASSIGNMENTS-2026-10-18.md)
