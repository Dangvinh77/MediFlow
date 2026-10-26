# Kiểm thử ngoại trú V2 — 26/10/2026

Tài liệu này hướng dẫn kiểm thử mã nguồn của PR triển khai `OPD-SCHED-2026-10-26`
revision 2. Nghiệp vụ chuẩn nằm tại [02](../../docs/plans/02-business-flows.md), wire tại
[03 phụ lục A](../../docs/plans/03-contracts.md#opd-scheduling-v2). Hướng dẫn 25/10 là
baseline cũ; SQL 25/10 cung cấp dữ liệu tổng hợp và lịch sử, không cung cấp sẵn buổi khám V2.
Mỗi lần tổng duyệt phải ghi commit, ngày chạy, vai trò, các UUID và kết quả thực tế.
Kiểm thử tự động đạt không thay cho nghiệm thu đầy đủ trên UI.

Kết quả đã chạy và phần chưa nghiệm thu được ghi riêng tại
[bằng chứng 26/10](../../docs/acceptance/outpatient-2026-10-26/README.md).

## 1. Kiến trúc và tiền trong demo

![Luồng giữ suất và thanh toán](../../docs/plans/diagrams/outpatient-rehearsal-v2.svg)

[Mermaid source](../../docs/plans/diagrams/outpatient-rehearsal-v2.mmd).

Browser gọi `/api/*` cùng origin của Next.js; Next chuyển tới Gateway. Postman gọi
Gateway trực tiếp. Gateway kiểm JWT và quyền; service kiểm lại quyền và dữ liệu hiện hành.
Clinical sở hữu ca khám, sức chứa, phiếu, hồ sơ; Core sở hữu tài khoản/nhân sự/khoa/phòng.
Billing sở hữu báo giá, tiền thực thu, clearance và hoàn tiền. Mỗi context dùng DB riêng.
HTTP nội bộ dùng service credential ngắn hạn; không dùng token người dùng để gọi lease.
RabbitMQ và outbox đồng bộ sự kiện sau commit; không giả định event đến ngay hoặc chỉ một lần.

| Thành phần | Cổng ngoài mặc định | Cổng trong mạng Docker / mục đích |
|---|---:|---|
| Frontend | 3000 | 3000; UI và proxy cùng origin |
| Gateway | 8080 | 8080; cửa vào API của client |
| Eureka | 8761 | 8761; discovery của service |
| Core | 8089 | 8089; Organization + Patient, store riêng |
| Clinical | 8082 | 8082; đăng ký, lịch, hồ sơ |
| Lab | 8084 | 8084; thực hiện xét nghiệm |
| Pharmacy | 8085 | 8085; thuốc/kho/cấp phát |
| Billing | 8086 | 8086; báo giá/thu/hoàn |
| Support | 8087 | 8087; Notification + Report, store riêng |
| PostgreSQL | 5432 | 5432; JDBC theo DB của từng context |
| RabbitMQ | 5672; quản trị 15672 | 5672; AMQP |

Các cổng service phục vụ vận hành/debug; Postman và UI dùng Gateway. Runner cách ly bên dưới
dùng dải 28xxx thay cho các cổng trên. Không khởi động thêm Organization/Patient riêng khi dùng Core;
không khởi động Notification/Report riêng khi dùng Support.

**BHYT mô phỏng:** mặc định mọi bệnh nhân đã đóng đủ và thẻ còn hạn. Không tra cứu BHXH,
không quyết toán bảo hiểm. Policy `DEMO-BHYT-2026-10-25` do dự án tự quy định:

| `sourceType` / `priceCode` | Giá | Hỗ trợ | Người bệnh trả |
|---|---:|---:|---:|
| EXAM / OUTPATIENT_EXAM | 100.000 | 50% | 50.000 |
| EXAM / OUTPATIENT_FOLLOW_UP | 80.000 | 50% | 40.000 |
| LAB_TEST / LAB_CBC | 120.000 | 70% | 36.000 |
| LAB_TEST / LAB_GLUCOSE | 40.000 | 50% | 20.000 |
| LAB_TEST / LAB, mục không được cấu hình, thuốc | Theo catalogue | 0% | Toàn bộ giá |

Tỷ lệ phải khớp cả `sourceType` và `priceCode`; tên hiển thị không quyết định tỷ lệ.
Tính hỗ trợ từng dòng, làm tròn VND bằng `HALF_UP`; tổng tiền là tổng các dòng.
Quote giữ `catalogueVersion`, `policyVersion` và snapshot. Không sửa receipt cũ khi đổi config.
Policy mặc định trên không có mục hỗ trợ 100%; kiểm thử nhánh 0 đồng cần dataset/config riêng
có item 100% được ghi rõ, không giả tạo giao dịch thu 0 đồng. Hoàn tiền tối đa bằng tiền
người bệnh đã trả trừ các lần hoàn trước; phần hỗ trợ không phải tiền mặt/doanh thu thu ngân.

## 2. Khởi động và dữ liệu

Yêu cầu Java 21, Maven, Docker Compose, pnpm và Python 3. Chạy lệnh từ repo root.
Các khối `bash` dùng Terminal Git Bash/Linux/macOS; lệnh Maven/Docker/pnpm cũng chạy được
trong PowerShell. Build JAR tạo file trong `target/`, không commit các file build vào Git.

### 2.1. Tổng duyệt bằng JAR trên hạ tầng cách ly

```bash
mvn -q -pl backend/core-service,backend/clinical-service,backend/lab-service,backend/pharmacy-service,backend/billing-service,backend/support-service,backend/gateway,backend/eureka-server -am -DskipTests package
python scripts/demo_25_10/rehearse_snapshot.py --scheduling --keep
```

Runner tạo PostgreSQL/RabbitMQ riêng, restore tám context từ SQL demo, chạy Flyway mới
khi từng JAR khởi động, thêm phòng `CONSULTATION` bằng `seed_outpatient_scheduling.sql`,
kiểm health và ghi `evidence/active-rehearsal.local.json`.
Không dùng DB/volume đang có. Chỉ tiếp tục khi in `READY`. Gateway là `http://127.0.0.1:28080`.
Tạo file có đường dẫn `stopFile` trong manifest để dừng đúng JVM/container của runner.
Đây là kiểm tra khởi động, chưa tự chạy toàn bộ nghiệp vụ bên dưới.

Sau `READY`, có thể chạy kiểm thử API tạo dữ liệu mới trên chính stack cách ly:

```bash
python scripts/demo_25_10/accept_scheduling.py --gateway http://127.0.0.1:28080
```

Chạy full trước 17:00 giờ Việt Nam, nên bắt đầu trước 16:00 để đủ thời gian.
Ngoài giờ, dùng `--booking-only` trên stack mới để kiểm đăng ký/thanh toán cho ngày mai;
chế độ này **không** bắt đầu khám, kiểm Lab hoặc lời hẹn tái khám và evidence ghi phạm vi riêng.

Runner dùng tài khoản demo tổng hợp và tạo buổi khám/phiếu/thu tiền/chỉ định thật trong DB demo;
không chạy vào DB đang dùng bảo vệ. Nó kiểm khám mới, quote và replay, Lab thanh toán/trả kết quả,
hoàn tất hồ sơ và PATIENT đọc lời hẹn; kết quả ghi `evidence/scheduling.local.json` không chứa token.
Chỉ chạy một lần trên stack mới vì buổi được tạo có kiểm overlap. Bộ này không thay ma trận lỗi/zero/refund/UI.

Khởi động UI trong terminal khác:

```bash
cd frontend
export GATEWAY_URL=http://127.0.0.1:28080
export MEDIFLOW_SCHEDULING_UI_ENABLED=true
export MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED=true
export MEDIFLOW_ZERO_PAYABLE_UI_ENABLED=true
export MEDIFLOW_OUTPATIENT_UI_ENABLED=true
export MEDIFLOW_PHARMACY_PRESCRIPTION_MODE=outpatient
export MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=true
pnpm install --frozen-lockfile
pnpm dev
```

### 2.2. Chạy hoàn toàn bằng Docker

Restore SQL theo phần 4 của [hướng dẫn 25/10](MediFlow_Demo_4_Dev_Chi_Tiet_2026-10-25.md)
trước khi chạy app. Chỉ restore vào database trống; không import đè DB có dữ liệu.
`scripts/init-databases.sql` tạo tên DB, không tạo tài khoản/bệnh nhân demo.
Fresh volume tự chạy init; volume cũ không chạy lại init khi restart. SQL demo và Flyway
có nhiệm vụ khác nhau: SQL phục hồi baseline, Flyway bổ sung schema mới lúc app boot.

```bash
docker compose -f docker-compose.yml -f compose.demo.local.yml -f compose.outpatient.local.yml config --quiet
docker compose -f docker-compose.yml -f compose.demo.local.yml -f compose.outpatient.local.yml up -d postgres rabbitmq
docker compose -f docker-compose.yml -f compose.demo.local.yml -f compose.outpatient.local.yml ps
# Sau khi restore SQL demo, thêm phòng khám V2 (chạy lại an toàn):
docker compose -f docker-compose.yml -f compose.demo.local.yml -f compose.outpatient.local.yml cp scripts/demo_25_10/seed_outpatient_scheduling.sql postgres:/tmp/scheduling.sql
docker compose -f docker-compose.yml -f compose.demo.local.yml -f compose.outpatient.local.yml exec -T postgres psql -X -v ON_ERROR_STOP=1 -U postgres -d mediflow_organization -f /tmp/scheduling.sql
# Sau khi xác nhận DB/broker healthy:
docker compose -f docker-compose.yml -f compose.demo.local.yml -f compose.outpatient.local.yml up -d --build eureka-server gateway core-service clinical-service lab-service pharmacy-service billing-service support-service frontend
docker compose -f docker-compose.yml -f compose.demo.local.yml -f compose.outpatient.local.yml ps
```

Luôn giữ đủ ba `-f` khi quản lý stack này. Overlay mới bật scheduling/quote/zero và thay
catalogue demo; các gate trong cấu hình mặc định của ứng dụng vẫn tắt. Dừng giữ dữ liệu:

```bash
docker compose -f docker-compose.yml -f compose.demo.local.yml -f compose.outpatient.local.yml down
```

Không thêm `-v` khi muốn giữ dữ liệu đã tổng duyệt. Không dùng lệnh xóa Docker toàn máy.

## 3. Chuẩn bị Postman và tài khoản

Tạo environment có `gateway`, token riêng theo role, `departmentId`, `doctorId`, `roomId`,
`patientId`, `sessionId`, `sessionRevision`, `registrationId`, `reservationId`,
`reservationRevision`, `paymentRequestId`, `quoteId`, `paymentOperationId`, `recordId`, `testId`.
Lấy UUID thật từ response của môi trường; không dùng khóa `staff-03`/`patient-01` trong seed làm UUID.

Đăng nhập `POST {{gateway}}/api/v1/auth/login`, JSON
`{"username":"demo.admin","password":"MediFlowDemo@2026"}`. Đây là credential demo tổng hợp;
snapshot đã đổi mật khẩu/khóa tài khoản vẫn giữ thay đổi đó. Lưu `accessToken` ở cấp gốc response vào biến role
(Auth trả DTO riêng; API nghiệp vụ dùng envelope `data`).
Mọi request bảo vệ có `Authorization: Bearer {{tokenRole}}`; command V2 có
`Content-Type: application/json`, `X-Correlation-Id` và `Idempotency-Key` ASCII không dấu, tối đa 120 ký tự.
Mỗi intent mới dùng key mới; retry cùng intent giữ nguyên key, body và tài khoản.

| Tài khoản | Role | Luồng cần kiểm |
|---|---|---|
| demo.admin | ADMIN | Danh bạ, quản lý scope, tạo buổi, kiểm quyền |
| demo.manager | MANAGER | Quản lý buổi trong khoa được cấp scope |
| demo.nurse | NURSE | Tiếp nhận, giữ suất, arrival, tái khám |
| demo.cashier | CASHIER | Quote, thu/0 đồng, theo dõi operation, hoàn |
| demo.doctor | DOCTOR | Lịch ngày/tuần, khám đúng phân công, hẹn tái khám |
| demo.doctor2 | DOCTOR | Kiểm tra không khám thay người/khoa trái phép |
| demo.lab | LAB_TECH | Bắt đầu xét nghiệm, trả kết quả |
| demo.pharmacist | PHARMACIST | Thuốc/kho/cấp phát sau clearance |
| demo.patient | PATIENT | Phiếu và thông báo của chính mình, lời hẹn |
| demo.patient2 | PATIENT | Kiểm tra không đọc chéo dữ liệu |

## 4. Khám mới: từ lập lịch đến hoàn tất

1. ADMIN đọc danh bạ Core để chọn bác sĩ, khoa, phòng đang hoạt động và cùng khoa.
   Không chọn bác sĩ chỉ vì token cũ còn ghi DOCTOR; backend kiểm authority hiện hành.
2. Nếu dùng MANAGER, ADMIN đọc/cập nhật
   `/api/v2/org/accounts/{accountId}/schedule-management-scope` theo DTO A.2.
   MANAGER ngoài scope phải bị từ chối. ADMIN có thể quản lý buổi trực tiếp.
3. ADMIN/MANAGER `POST /api/v2/consultation-sessions` với
   `{doctorId,departmentId,roomId,sessionDate,startTime,endTime,breaks,capacity}`.
   Ngày là ngày demo, giờ `HH:mm`, múi giờ nghiệp vụ `Asia/Ho_Chi_Minh`.
   Ví dụ giờ 07:00–11:30, `breaks: []`, `capacity: 10`. Lưu id và revision từ response.
   Tạo ca trùng bác sĩ/phòng phải lỗi; không gán UUID phòng/khoa từ môi trường khác.
4. NURSE tìm buổi bằng
   `GET /api/v2/consultation-sessions?departmentId=...&fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD&page=0&size=20`.
   Chọn buổi OPEN còn suất. Gửi `POST /api/v2/outpatient-registrations`:
   `{patientId,sessionId,expectedSessionRevision,receptionMode:"AT_COUNTER",visitType:"NEW_VISIT"}`.
   Không gửi các liên kết tái khám. Lưu `registrationId`, reservation và các revision mới.
5. Kỳ vọng đang chờ thanh toán, giữ suất 15 phút, chưa có thông báo xác nhận đã thu.
   `AT_COUNTER` đã có mặt; `PREBOOKED` cần NURSE gọi `PUT /api/v2/outpatient-registrations/{registrationId}/arrival`
   với `{expectedRevision}` khi bệnh nhân đến. Không gọi check-in V1 cho phiếu V2.
6. CASHIER `GET /api/v2/billing/payment-requests/by-registration?registrationId=...`.
   Khi projection chưa đến, UI hiển thị chờ; không tạo invoice thủ công thay thế.
   Lưu payment request, reservation revision hiện hành và số tiền cần trả.
7. CASHIER `GET /api/v2/billing/payment-requests/{id}/quote`. Kiểm gross 100.000,
   support 50.000, patientPayable 50.000 với policy trên; lưu `quoteId` và `expiresAt`.
8. CASHIER `POST /api/v2/billing/payment-requests/{id}/payments` body
   `{reservationId,expectedReservationRevision,quoteId,amount:"50000.00",currency:"VND",paymentMethod:"CASH"}`.
   Thu đúng toàn bộ nghĩa vụ; không tự trừ BHYT lần nữa. `BANK_TRANSFER` là phương thức ghi nhận,
   không là tích hợp cổng thanh toán/ngân hàng thật.
9. Nếu 202, lưu `paymentOperationId`, gọi `GET /api/v2/billing/payment-operations/{id}`
   cho đến kết quả cuối. Không phát sinh key mới khi chưa biết đã commit hay chưa.
   Khi COMPLETED, positive payment có transaction và clearance; Clinical xác nhận phiếu sau receipt.
10. DOCTOR đúng phân công `GET /api/v2/consultation-sessions/mine?date=YYYY-MM-DD`
    hoặc `?weekStart=YYYY-MM-DD`; mở hàng chờ của ca.
    Gọi `PUT /api/v1/appointments/{registrationId}/start-exam`, body `{}`.
    Chỉ được bắt đầu khi confirmed, đã đến và clearance còn hợp lệ.
11. Dùng `recordId` thật của lượt để ghi nhận khám/chẩn đoán/chỉ định theo UI hồ sơ.
    Nếu có Lab, làm phần 5 trước. Cuối lượt `PUT /api/v1/records/{recordId}/complete`
    với `disposition` hợp lệ theo form hiện hành và `dispositionNote` khi cần.
    Không hoàn tất khi chỉ định bắt buộc chưa kết thúc. Tái đọc kết quả cùng record không thu EXAM lần hai.

## 5. Xét nghiệm và thuốc

1. DOCTOR tạo chỉ định từ record đang mở. Với CBC chọn `priceCode=LAB_CBC`; glucose chọn
   `LAB_GLUCOSE`. Lab tạo test liên kết source order và episode thật, Billing nhận event tạo request.
2. CASHIER tra request bằng màn hình thanh toán theo nguồn, đọc quote V2 cùng request.
   Không gửi client price. Với CBC phải trả 36.000 theo policy demo.
3. LAB payable dương: `POST /api/v2/billing/payment-requests/{id}/lab-payments`,
   `{quoteId,amount:"36000.00",currency:"VND",paymentMethod:"CASH"}`, key/correlation riêng.
   Đây là local payment, không acquire reservation EXAM. Phải đúng toàn bộ `patientPayable`.
4. LAB_TECH chỉ bắt đầu sau clearance; nhập kết quả/hoàn tất bằng UI Lab. Thử bắt đầu trước thu,
   trả kết quả sai test và thao tác lặp để kiểm guard. Nếu hủy phải có lý do và đi qua luồng hủy hợp lệ.
5. DOCTOR mở lại đúng record để đọc kết quả rồi hoàn tất. Không tạo registration/EXAM mới để đọc
   kết quả của lượt đang mở. Mất Lab dependency phải báo chờ/lỗi, không giả định đã hoàn tất.
6. Đơn thuốc giữ luồng outpatient hiện hành: DOCTOR kê thuốc theo catalogue, CASHIER thu đúng request,
   PHARMACIST cấp phát sau clearance và kiểm tồn. Thuốc không có item hỗ trợ nên mặc định 0%.
   Không thay stock guards, không giả định kê đơn đồng nghĩa cấp phát.

## 6. Tái khám và lời hẹn

1. DOCTOR hoàn tất lượt nguồn rồi tạo lời hẹn
   `POST /api/v2/records/{recordId}/follow-up-recommendations`,
   `{recommendedFrom,recommendedThrough,reason,preparationInstructions}`.
   Chỉ bác sĩ của record V2 completed, hiện còn authority phù hợp, được tạo.
   Lời hẹn bất biến; retry cùng key không tạo lời hẹn thứ hai.
2. PATIENT mở `/appointments/my-registration`, thấy lời hẹn trong phiếu đã hoàn tất của mình.
   Tài khoản khác không được đọc chéo. Lời hẹn chưa là ca khám được đặt và chưa thu tiền.
3. NURSE chọn bệnh nhân, lấy nguồn từ
   `GET /api/v2/outpatient-registrations/follow-up-sources?patientId=...&page=0&size=20`.
   Chỉ dùng lượt V2 completed cùng bệnh nhân và khoa. Không copy diagnosis/đơn/kết quả thành chỉ định mới.
4. Chọn buổi mới còn suất và tạo registration `visitType:"FOLLOW_UP"`,
   `previousRegistrationId` là lượt nguồn; có lời hẹn thì gửi `followUpRecommendationId` thật.
   Không có lời hẹn thì gửi `reason` giải thích. Nếu đổi bác sĩ, bắt buộc
   `replacementReason` và `patientAgreedToDoctorReplacement:true`; UI phải cho bệnh nhân xác nhận.
5. Thu EXAM quote tái khám riêng: gross 80.000, support 40.000, trả 40.000. Đây là lượt mới,
   không sửa receipt/lịch đã kết thúc. Sau thanh toán và có mặt, bác sĩ được phân công mới bắt đầu khám.

## 7. Nghĩa vụ 0 đồng và các lỗi cần thử

Với dataset kiểm thử có hỗ trợ 100%, EXAM dùng
`POST /api/v2/billing/payment-requests/{id}/zero-payable-eligibility`,
body `{reservationId,expectedReservationRevision,quoteId}`. LAB dùng
`POST /api/v2/billing/payment-requests/{id}/lab-zero-payable-eligibility`, body `{quoteId}`.
Không gửi số tiền 0 vào endpoint thu tiền. Phải có clearance thật và basis `ZERO_LIABILITY`;
không có payment transaction/allocation/cash event giả. Report không tăng cash bởi nhánh này.

| Ca lỗi | Kết quả cần quan sát |
|---|---|
| Hai người giữ suất cuối | Chỉ một registration thành công; không vượt capacity |
| Revision cũ | Conflict; tải lại snapshot trước intent mới |
| Retry cùng key/body/actor | Cùng kết quả, không thu/giữ suất/thông báo hai lần |
| Cùng key đổi body hoặc actor | Conflict; không tạo giao dịch thay thế |
| Quote hết hạn/sai quote/sai số tiền | Bị từ chối trước thu; GET không tự renew |
| EXAM chưa acquire lease mà gọi thu V1 | Không được bypass registration V2 |
| LAB quote-bound gọi thu V1 | Bị từ chối; dùng command V2 có quoteId |
| Timeout sau gửi thu | Giữ key và tra operation; không giải phóng PAYMENT_LOCKED bằng TTL đơn lẻ |
| Billing/Clinical mất kết nối | Trạng thái chưa rõ được worker đối soát bằng cùng operation và fence |
| Bác sĩ khác/đã khóa/đổi khoa | Không được khám thay bằng token hoặc phân công cũ |
| PATIENT truyền patientId của người khác | Không có quyền đọc; mine lấy identity từ principal |
| Lab bắt buộc còn pending | Không hoàn tất record; không giả định lỗi network là đã xong |
| Refund lặp/vượt tiền thu | Bị chặn; không hoàn phần hỗ trợ |

**Giới hạn xử lý hết hạn:** chưa có command renew quote trong slice này. Sau hết hold EXAM
chưa thu, xử lý hết hạn/hủy đúng state rồi tạo đăng ký mới nếu còn suất. LAB hết quote cần xử lý
theo luồng hủy/tạo chỉ định mới hợp lệ, giữ audit; không sửa DB, snapshot hoặc nút thu để lách expiry.
Nếu operation đang PREPARING, phải đối soát trước mọi thao tác hủy/tạo mới.

## 8. Trình tự test UI và ghi bằng chứng

Mở browser profile riêng cho từng role hoặc đăng xuất/đăng nhập để không dùng token lẫn nhau.
ADMIN/MANAGER: `/appointments/schedule`; NURSE: `/appointments/reception`;
CASHIER: `/billing/scheduled-payments` cho EXAM và màn thanh toán theo nguồn cho LAB;
DOCTOR: `/appointments/my-calendar`, hồ sơ; PATIENT: `/appointments/my-registration` và thông báo.
Kiểm phần menu đúng role, rồi thử URL/API trực tiếp bằng role sai để kiểm backend.

Mỗi ca ghi: commit, cấu hình, role, bước, expected, actual, HTTP status/error code,
correlation, registration/request/operation/record/test UUID và ảnh UI đã che token.
Report phải phân biệt kỳ đã được chấp nhận coverage với kỳ chưa đủ event; không tạo coverage giả
chỉ để dashboard có số. Notification xác nhận chỉ sau phiếu được xác nhận, replay không gửi lặp.
Không kết luận đủ bảo vệ chỉ từ health hoặc build; hoàn tất ma trận tại
[06](../../docs/plans/06-acceptance-runbook.md) và [10](../../docs/plans/10-scheduling-contract-acceptance.md).

Kiểm tự động từ repo root:

```bash
python scripts/contract-checks/check-outpatient-scheduling.py
mvn -q -pl backend/core-service,backend/clinical-service,backend/lab-service,backend/billing-service,backend/gateway,backend/notification-service -am test
cd frontend
pnpm typecheck
pnpm lint
pnpm test --maxWorkers=1 --no-file-parallelism --testTimeout=15000 --hookTimeout=30000
pnpm build
```

Nếu dùng Node mới có xung đột native Web Storage/jsdom, dùng Node 24 LTS theo runner client
trong hướng dẫn 25/10. Không tắt assertion để biến lỗi thành kết quả đạt.
