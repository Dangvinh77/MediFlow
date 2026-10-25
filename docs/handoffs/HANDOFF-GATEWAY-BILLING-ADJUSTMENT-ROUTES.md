# Hoàng Anh — bổ sung Gateway routes hoàn tiền ngoại trú

## Điều phối hiện hành — 26/10/2026

- Registry ID: `H-04`; status: `NEEDS REVALIDATION`.
- Bàn giao hiện tại: Hoàng Anh/Gateway và Vinh/Billing → Vinh verification.
- [Registry và phần acceptance còn thiếu](README.md) là nguồn giao việc hiện tại.
- Các base/branch, status, owner và test counts trong checkpoint dưới giữ theo ngày;
  không là bằng chứng bug còn tồn tại hoặc task phải code lại trên HEAD.
- Nghiệp vụ mới dùng [WF-01–11](../plans/07-outpatient-workflow-implementation.md), không đóng từ evidence baseline.

## Hồ sơ phát hiện và checkpoint

**Refresh:** 2026-10-19, remote master `4c19dd4a`.
**Trạng thái:** CODE IMPLEMENTED tại `2805b07c` / PR #431; không giao lại code.
Vinh retest ngày 19/10: explicit routes 11/11 và full Gateway 1.449/1.449, không skips.
Phần runtime HTTP/Billing và financial race trong acceptance rộng hơn vẫn cần evidence
được chia sẻ trên cùng source lock trước khi xóa phiếu; policy tests không thay runtime E2E.
[Kết quả và giới hạn](../acceptance/backend-recheck-2026-10-19/README.md).
**Dev code:** Hoàng Anh (`TranHoangAnh94`).
**Toàn bộ kiểm thử/nghiệm thu:** Vinh, theo phân công người dùng ngày 2026-10-18;
xem [queue kiểm thử chung](HANDOFF-VINH-BACKEND-VERIFICATION.md).
Phiếu này thay yêu cầu cũ mở cả Surgery và dùng wildcard `/**`.

## Gap đã xác nhận

Hai controllers có role guards nhưng `RouteAuthorizationFilter` chưa cho qua;
`backend/gateway/src/test/resources/route-role-contracts.csv` đánh dấu cả hai `false`:

| Task | Method / exact path | Roles | Controller Billing |
|---|---|---|---|
| HA-CODE-01 | `GET /api/v1/billing/prescription-adjustments/{id}/refunds-due` | ADMIN, CASHIER | PrescriptionAdjustmentController |
| HA-CODE-02 | `POST /api/v1/billing/transactions/{id}/refunds` | ADMIN, CASHIER | LedgerRefundController |

Controllers nằm tại `backend/billing-service/src/main/java/com/mediflow/billing/web/`.
Default `docker-compose.yml` đã bật `MEDIFLOW_BILLING_LEDGER_ENABLED`,
`MEDIFLOW_BILLING_PRESCRIPTION_CHARGE_CONSUMER_ENABLED`, `MEDIFLOW_BILLING_REFUNDS_ENABLED`.
Đây là gap public outpatient hiện tại. HA-CODE-02 là phát hiện bổ sung trong lượt này:
command ghi nhận hoàn tiền thực tế cũng bị Gateway default deny.

## Hoàng Anh thực hiện code

- Thêm đúng hai exact method/path rules trong
  `backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java`,
  roles ADMIN/CASHIER theo controller. Không mở prefix hoặc nested paths bằng `/**`.
- Đổi đúng hai dòng tương ứng trong `route-role-contracts.csv` thành `true`, giữ policy
  các endpoints khác. Đây là fixture đi kèm code; Vinh nhận viết/chạy tests xác minh.
- Giữ JWT types, signed actor, correlation, query/body và downstream response/error forwarding.
  Không đổi Billing DTO/business semantics hoặc deployment flags để vượt gap.
- Bàn giao commit SHA/diff cho Vinh; không giao full regression/integration lại Hoàng Anh.

## Vinh kiểm thử và đóng

- ADMIN/CASHIER hợp lệ qua hai exact routes tới Billing thật; adjustment read trả đúng
  resource, refund command dùng original payment thật và live request DTO.
- Wrong role, SYSTEM/human-token mismatch, refresh token, thiếu token, sai method,
  thiếu ID, extra/nested segment đều bị từ chối theo existing policy.
- Giữ signed actor/correlation; refund retry/race/over-refund vẫn đúng business guards.
  Refund obligation pending không được coi là completed refund.
- RouteRoleContractTest, Gateway/Billing regression và actual Gateway/Billing slice đạt
  trên cùng revision; counts/skips/exit codes và evidence do Vinh ghi vào queue chung.

Frontend FE-BILLING-07 đọc adjustment phụ thuộc HA-CODE-01; frontend không thuộc phiếu code này.
Không mở Surgery cancellation, account deposit/settlement hoặc Report maintenance writes chỉ vì
CSV cũng có dòng `false`. Surgery/Inpatient/full V2 deferred, không là blocker ngoại trú.
Chỉ đóng phiếu sau khi cả code và nghiệm thu Vinh đạt.

## Tái hiện lịch sử trước fix — 18/10/2026

Vinh đã thêm test `backend/gateway/src/test/java/com/mediflow/gateway/GatewayRefundRoutesAcceptanceIT.java`.
Chạy explicit (tên IT không nằm trong default Surefire):

```powershell
mvn -B -ntp -pl backend/gateway -am '-Dtest=GatewayRefundRoutesAcceptanceIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

11 cases: 4 failures ở ADMIN/CASHIER của hai route trên, 7 negative controls pass; exit 1.
Test dùng actual RouteAuthorizationFilter với mock exchange/downstream chain để chứng minh
policy gap; chưa thay thế HTTP/JWT/Billing refund acceptance. Default Gateway suite 1.449/1.449
pass vì route-role CSV hiện ghi hai routes chưa publish. Sau sửa, Vinh chạy lại test này và
kiểm thử Gateway/Billing thật như acceptance phía trên.

[Evidence source/counts/failures](../acceptance/backend-2026-10-18/README.md).
