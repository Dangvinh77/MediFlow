# Hoàng Anh — JWT thiếu expiration và các kiểm tra Core cần bổ sung

Ngày: 2026-10-19. Base: `05c7690c3a37b602e336fac4902ced7f3a1f7d89`.
Owner production: **Hoàng Anh**, Gateway và Core. Vinh giữ việc kiểm thử/review.
Không sửa production hai module trong PR audit của Vinh.

## Bàn giao bản sửa của Hoàng Anh — 2026-10-19

Nhánh: `codex/ha-jwt-expiration-verifier-caller`. HA-AUDIT-01/02 đã có source fix;
chưa đóng nghiệm thu, chờ Vinh xác nhận trên commit/artifact của PR.

- Gateway, Organization và Patient yêu cầu access token có `exp` còn hiệu lực.
  Giữ kiểm tra type/subject/role/identity và các quy tắc TTL của service token hiện có.
- Riêng `/api/v1/org/accounts/verify` yêu cầu service token `role=SYSTEM`, signed
  `sub=gateway`, kiểm tại Gateway và method security của Core. Header `X-Service-Id`
  không cấp quyền. Không thêm claim scope mới hoặc khóa các lookup khác về Gateway.
- Regression fixtures từ handoff đã được đưa vào suite. Bổ sung thiếu/hết hạn trên
  cả hai Core chains, positive access/service, Gateway đúng caller và Pharmacy sai
  caller kể cả khi giả header Gateway; verify sai token type không gọi được use case.
  Các positive fixtures cũ của Core/Patient được bổ sung `iat`/`exp`.
- HA-AUDIT-03 vẫn chỉ là nghi vấn: Vinh tái hiện bằng concurrent PostgreSQL test;
  nếu xác nhận, Hoàng Anh xử lý production trong thay đổi riêng. Invariant nghiệm thu:
  không còn nhân viên active thuộc khoa inactive.
- Vinh chạy full Gateway/Core với Docker/PostgreSQL/RabbitMQ, kiểm lại hai refund
  routes và acceptance trên artifact mới. Không giao viết lại refund production.

Commit, kết quả kiểm thử và trạng thái nghiệm thu được ghi trong PR. Các patch tái hiện
bên dưới là evidence trên base cũ; không áp lại lên nhánh đã chứa regression tests.

Kiểm thử local trên source của nhánh này:

| Lệnh | Kết quả |
|---|---|
| `mvn -q -pl backend/gateway,backend/core-service -am test` | Gateway: 1.451 tests pass. Core lần đầu: 181 tests, 7 failures do positive fixtures Patient thiếu exp, 3 errors do Docker không chạy. Đã sửa fixtures và chạy lại bên dưới. |
| `mvn -q -pl backend/core-service -am test '-Dtest=!OrganizationServiceIntegrationTest,!SurgeryAuthorityIntegrationTest,!PatientServiceIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'` | 178 tests, 0 failures/errors/skips. Ba integration suites được loại rõ qua selector, chưa được nghiệm thu. |
| `mvn -q -pl backend/gateway -am test '-Dtest=GatewayRefundRoutesAcceptanceIT' '-Dsurefire.failIfNoSpecifiedTests=false'` | 11 tests, 0 failures/errors/skips; dùng acceptance sẵn có, không sửa refund routes. |
| `mvn -q -pl backend/gateway,backend/core-service -am package -DskipTests` | Thành công; tạo lại hai executable JAR trong target. Không thay thế full integration/packaged acceptance của Vinh. |

Self-review theo skill `review-pr`: không phát hiện blocker trong diff; đủ điều kiện mở PR.
Chưa đủ điều kiện kết luận nghiệm thu/merge trước khi Vinh chạy các gate còn thiếu trên PR head.

### Cập nhật sau khi bật Docker — 2026-10-19

Đã chạy lại `mvn -q -pl backend/gateway,backend/core-service -am test` trên commit
`cea553d1d853e396cbcf052acb0d16ed9a02c511`, Docker Engine 28.5.1, PostgreSQL
`postgres:16-alpine` và RabbitMQ `rabbitmq:3.13-management-alpine` qua Testcontainers.
Lệnh kết thúc exit code 0:

- Gateway: **1.451 tests, 0 failures, 0 errors, 0 skips**.
- Core: **187 tests, 0 failures, 0 errors, 0 skips**.
- Trong Core, `OrganizationServiceIntegrationTest`: 2 pass,
  `SurgeryAuthorityIntegrationTest`: 6 pass, `PatientServiceIntegrationTest`: 1 pass.
  Ba integration suites trước đó bị chặn bởi Docker nay đã chạy thành công.

Đây là evidence local của Hoàng Anh, không thay cho xác nhận độc lập của Vinh.
Vẫn chờ Vinh nghiệm thu artifact mới/refund routes và tái hiện riêng HA-AUDIT-03;
không coi full suite hiện có là bằng chứng đã kiểm concurrent race khoa/nhân viên.

## HA-AUDIT-01 — đã tái hiện: signed access token không có exp vẫn được chấp nhận

Gateway `JwtAuthenticationFilter` chỉ dựa vào parser kiểm expiration nếu claim có mặt,
không yêu cầu `exp`. Core Organization và Patient có cùng thiếu sót. Token issuer hiện hành
luôn phát access token có hạn dùng; token không hạn không phải wire format được chủ đích hỗ trợ.

Regression patch: [gateway-core-missing-exp-regression.patch](fixtures/gateway-core-missing-exp-regression.patch).
Áp patch trên base, chạy:

```powershell
git apply docs/handoffs/fixtures/gateway-core-missing-exp-regression.patch
mvn -pl backend/gateway '-Dtest=GatewayWebTest#humanAccessTokenWithoutExpiration_returns401Envelope' test
mvn -pl backend/core-service '-Dtest=JwtAuthFilterTest#humanAccessTokenWithoutExpiration_leavesRequestUnauthenticated' test
```

Kết quả thực tế: mỗi lệnh **1 test, 1 failure, 0 error, 0 skip**. Gateway expected 401,
actual 503 từ downstream không có service chạy — request đã vượt qua authentication.
Core expected null authentication, actual authenticated `ROLE_ADMIN`.
Không mô tả 503 này là backend nghiệp vụ thành công; nó chứng minh boundary đã cho qua token.
Patch được lưu riêng để không đưa ca cố ý đỏ vào suite của PR sửa module khác.

Acceptance: yêu cầu expiration có mặt và còn hiệu lực ở Gateway, Organization và Patient;
giữ đúng loại token, subject/role, service TTL và caller scopes. Thêm negative fixture cho
Patient cùng các positive access/service tokens hợp lệ. Chạy full Gateway/Core và refund
route acceptance; API/JSON/route hiện hành không đổi.

## HA-AUDIT-02 — đã tái hiện: scope của credential verification

`backend/core-service/requests/organization.md` quy định Gateway là caller của
`POST /api/v1/org/accounts/verify`; `OrganizationAuthClient` phát service token `sub=gateway`.
Gateway/Core hiện cấp quyền generic `SYSTEM_SERVICE` cho service subject bất kỳ và controller
chỉ kiểm authority đó. Token hợp lệ của Pharmacy có thể thỏa điều kiện này dù không phải Gateway.
Regression qua Core security chain thật đã xác nhận đúng signed token của Pharmacy (service,
SYSTEM, iat/exp 60 giây) gọi được controller và credential use case: **1 test, 1 failure,
0 error, 0 skip**, expected 403, actual 200; response chứa account ADMIN do mocked use case trả.
Không có truy vấn mật khẩu/account production trong kiểm thử.

```powershell
git apply docs/handoffs/fixtures/core-verifier-caller-regression.patch
mvn -pl backend/core-service '-Dtest=CoreCompositionSmokeTest#pharmacyServiceTokenCannotVerifyAccountCredentials' test
```

Patch [core-verifier-caller-regression.patch](fixtures/core-verifier-caller-regression.patch)
được kiểm `git apply --check`; source test tạm đã restore sau tái hiện.

Owner cần khóa rõ caller/scope cho riêng route verify, thêm test wrong-subject/wrong-scope,
giữ các internal lookup khác đúng caller contracts. Không đổi mọi Core internal route thành
Gateway-only. Nếu hiện trạng là chủ đích, phải cập nhật contract và threat boundary trước đóng.

## HA-AUDIT-03 — static race candidate: deactivate department và create staff

`UpdateDepartmentService` kiểm không có active staff rồi save inactive; `CreateStaffService`
đọc department active rồi insert staff. Các reads hiện không khóa/version aggregate; FK chỉ
bảo đảm department tồn tại. Có interleaving để cả hai check đều qua rồi tạo active staff ở
department inactive, trái invariant Organization.

**Chưa tái hiện bằng PostgreSQL concurrency test**; không tính là lỗi runtime đã xác nhận.
Owner bổ sung barrier test với DB thật cho hai giao dịch và giải quyết nếu test đỏ. Acceptance:
ít nhất một giao dịch bị từ chối/serialize, không có active staff thuộc department inactive;
sequential guards và API errors hiện hành vẫn đúng.

Quy tắc đối chiếu: [security/tests](../ai/README.md),
[identity/authority contracts](../plans/03-contracts.md),
[business invariants](../plans/02-business-flows.md).
Chỉ đóng từng mục sau khi có source fix (hoặc quyết định contract có căn cứ) và evidence tương ứng.
