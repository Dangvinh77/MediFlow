# Hoàng Anh — JWT thiếu expiration và các kiểm tra Core cần bổ sung

Ngày: 2026-10-19. Base: `05c7690c3a37b602e336fac4902ced7f3a1f7d89`.
Owner production: **Hoàng Anh**, Gateway và Core. Vinh giữ việc kiểm thử/review.
Không sửa production hai module trong PR audit của Vinh.

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
