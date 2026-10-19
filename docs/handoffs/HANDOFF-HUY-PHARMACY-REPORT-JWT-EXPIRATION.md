# Huy — yêu cầu JWT expiration nhất quán ở Pharmacy và Report

Ngày: 2026-10-19. Base: `cc3789a81340cca8aac564a305d7c629a0c1bac4`.
Owner production: **Huy**; Vinh chạy kiểm thử và review sau sửa.

Pharmacy `JwtAuthFilter` kiểm chữ ký, access type, role và UUID subject nhưng không yêu cầu
expiration có mặt. Parser chỉ reject expired token nếu claim tồn tại. Hai ca real-filter với
ADMIN và PHARMACIST, đúng signed UUID access credential, bỏ riêng `exp`, vẫn tạo authentication.

```powershell
mvn -pl backend/pharmacy-service '-Dtest=JwtAuthFilterTest#accessTokenWithoutExpiration_adminLeavesRequestUnauthenticated+accessTokenWithoutExpiration_pharmacistLeavesRequestUnauthenticated' test
```

Kết quả tái hiện: **2 tests, 2 failures, 0 error, 0 skip**. Cả hai đúng nguyên nhân:
filter tạo authentication dù chỉ thiếu `exp`. Baseline class sau khi restore fixture:
**8 tests, 0 failures, 0 errors, 0 skip**.

Report có cùng lỗ hổng ở một phạm vi URI khác. `JwtAuthFilter` chỉ yêu cầu expiration và
UUID dưới `/api/v1/reports/financial/` hoặc `/operations/`; route thật
`/api/v1/reports/outpatient/daily` thuộc profile MVP và bỏ qua guard đó. Regression dùng
ADMIN access token có chữ ký hợp lệ, UUID subject và chỉ thiếu `exp`; filter vẫn xác thực.
**1 test, 1 failure, 0 error, 0 skip**. Baseline class sau khi restore fixture:
**4 tests, 0 failures, 0 errors, 0 skip**. Legacy `/api/v1/reports/daily`, `/monthly` và
`/top-medicines` cũng nằm ngoài hai prefix guard theo source; chưa có test red riêng cho từng
route, nên cần bao phủ chúng khi sửa boundary.

```powershell
mvn -pl backend/report-service '-Dtest=JwtAuthFilterTest#accessTokenWithoutExpiration_isRejectedOnOutpatientReportPath' test
```

Regression patch gộp cho Pharmacy và Report: [huy-jwt-missing-exp-regression.patch](fixtures/huy-jwt-missing-exp-regression.patch).
Áp patch trên base, rồi chạy hai lệnh focused ở trên. Các log red/green và command/count đầy đủ
được lưu trong `%TEMP%\huy-jwt-missing-exp-pharmacy-evidence-20261019.txt` và các file log cùng
thư mục `huy-jwt-missing-exp-*-20261019.log`.

`JwtAuthFilter` Pharmacy hiện chỉ parse chữ ký; Report hiện chỉ áp expiration/UUID guard cho
hai prefix trên. Huy cần sửa cả hai filter để buộc `exp` tồn tại và còn hiệu lực trên mọi
human API route, gồm cả outpatient và legacy Report reads; giữ nguyên subject/role validation
hiện hành. Không đổi URL, DTO, envelope hoặc permission model.

Acceptance: signed token thiếu/expired `exp` bị từ chối trước mọi human controller/use case;
wrong-signature/wrong-type/malformed-subject cases vẫn kiểm đúng nguyên nhân; human access
credential do issuer thật phát vẫn qua. Thêm Report negative coverage cho outpatient và legacy
read routes, rồi chạy full Pharmacy/Report suites, Support co-host regression và
refund/paid-prescription acceptance trên fresh artifacts. Không đổi semantics `paymentConfirmed`,
không mở service token thành human access, không sửa chữ ký/type parser theo cách nới validation
để làm test xanh.

## Huy implementation — 2026-10-19

PR [#445](https://github.com/Dangvinh77/MediFlow/pull/445), base `5ccecf83`, code `5334aa8c`.
Hai human filters yêu cầu `exp` có mặt và strictly after current time, sau xác minh chữ ký
và trước tạo Authentication. Report áp guard trước mọi phân nhánh URI, bao gồm outpatient
daily/monthly và legacy daily/monthly/top-medicines. UUID rule riêng của các prefix Report
financial/operations, subject/role/type validation còn lại và service credential path giữ nguyên.
Không đổi DTO, API, boolean `paymentConfirmed`, rollout flags hoặc Notification source.

Red baseline: Pharmacy **11 tests, 2 failures**; Report **76 tests, 11 failures**. Regression
bao phủ ADMIN/PHARMACIST và ADMIN/MANAGER, valid/expired/missing expiry, signature/type/subject,
cùng HTTP negative tests chứng minh không gọi lifecycle/read use cases. Các sửa clock/publisher
ở #439 đã nằm trong base, không được làm lại.

Full module Docker-backed verification trên code `5334aa8c`:

```powershell
mvn -q -pl backend/pharmacy-service,backend/report-service -am test
```

Exit 0. Pharmacy **843 tests / 108 classes**, Report **1.407 tests / 137 classes**;
**0 failures, 0 errors, 0 skipped** cho cả hai. Các integration tests dùng PG16/RabbitMQ thật.
Count lấy từ Surefire XML của checkout riêng, không cộng lượt red hoặc rerun.
Support co-host regression cũng đạt **12/12 tests / 3 classes**, exit 0, zero failure/error/skip:

```powershell
mvn -q -pl backend/support-service -am '-Dtest=SupportSecurityTest,SupportOutpatientCompositionTest,SupportCompositionTest' '-Dsurefire.failIfNoSpecifiedTests=false' package
```

Handoff vẫn **OPEN cho Vinh regression/acceptance chung**; test filter/controller
không thay issuer/Gateway/package/browser acceptance. PR FE dispense được tách ở
[#446](https://github.com/Dangvinh77/MediFlow/pull/446).
