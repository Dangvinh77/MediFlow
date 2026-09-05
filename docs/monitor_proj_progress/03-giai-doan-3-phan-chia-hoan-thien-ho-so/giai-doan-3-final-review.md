# GIAI ĐOẠN 3 - PHÂN CÔNG, RÀ SOÁT VÀ HOÀN THIỆN HỒ SƠ

**Dự án:** MediFlow

**Kỳ kế hoạch:** 24/08/2026 - 31/08/2026

**Ngày cập nhật:** 05/09/2026

**Phạm vi:** 8 microservice nghiệp vụ, API Gateway, cơ sở dữ liệu, hợp đồng API/event và giao diện Flutter tương ứng.

Tài liệu này chốt lại trách nhiệm của từng thành viên trong Giai đoạn 3, rà soát sự liên kết giữa yêu cầu và thiết kế, ghi nhận các nội dung đã chỉnh sửa, đồng thời tóm tắt ba file chính của bộ hồ sơ. Theo phân công mới, mỗi người chịu trách nhiệm trọn vẹn cho service được giao, bao gồm Backend, Database, API/event, kiểm thử và phần giao diện Flutter tương ứng. Hoàng Anh không phụ trách toàn bộ Flutter; Hoàng Anh chỉ chịu thêm API Gateway ngoài các service của mình.

---

## 1. Bảng phân công hoạt động chi tiết

### 1.1. Nguyên tắc phân công

- Mỗi service có một người chịu trách nhiệm chính để tránh bỏ sót hoặc giao thoa trách nhiệm.
- Owner của service thực hiện xuyên suốt từ yêu cầu nghiệp vụ, domain, API, database, event, kiểm thử đến màn hình Flutter tương ứng.
- Mỗi service sở hữu database riêng; không tạo khóa ngoại hoặc truy cập trực tiếp database của service khác.
- Flutter chỉ gọi hệ thống qua Gateway; không gọi trực tiếp cổng nội bộ của microservice.
- Các hợp đồng giao tiếp giữa hai service phải được cả hai owner đối chiếu trước khi tích hợp.
- Vinh giữ vai trò nhóm trưởng, điều phối tích hợp và kiểm duyệt cuối nhưng không thay thế trách nhiệm triển khai của các owner khác.

> Trong bảng phân công Giai đoạn 3, **Frontend** được hiểu là phần giao diện **Flutter** gắn với service của từng người. Web Next.js vẫn là một thành phần trong hồ sơ thiết kế Giai đoạn 2 nhưng không được dùng để mặc định giao toàn bộ frontend cho một thành viên.

### 1.2. Bảng ownership tổng hợp

| Thành viên | Service chịu trách nhiệm chính | Phạm vi bổ sung | Kết quả phải bàn giao |
|---|---|---|---|
| **Vinh** | Clinical Service, Lab Service | Nhóm trưởng; điều phối tích hợp; kiểm duyệt cuối | Backend, database, API/event, test, giao diện Flutter của Clinical và Lab; kết quả rà soát toàn gói |
| **Huy** | Pharmacy Service, Report Service | Chủ lực Backend | Backend, database, API/event, test, giao diện Flutter của Pharmacy và Report |
| **Hoàng Anh** | Organization Service, Patient Service | Chịu thêm API Gateway | Backend, database, API/event, test, giao diện Flutter của Organization và Patient; định tuyến, xác thực và hợp đồng vào hệ thống qua Gateway |
| **Lộc** | Billing Service, Notification Service | Hỗ trợ Backend và Frontend khi tích hợp | Backend, database, API/event, test, giao diện Flutter của Billing và Notification |

### 1.3. Bảng năng lực và phạm vi áp dụng

Bảng này mô tả năng lực theo vai trò đã được nhóm chốt, không phải bảng chấm điểm cá nhân. Các mức sử dụng gồm **Chủ trì**, **Thực hiện chính**, **Thực hiện trong service**, **Hỗ trợ** và **Phối hợp**.

| Thành viên | Điều phối | Backend | Database | Flutter | Tích hợp/API | Kiểm thử và tài liệu | Cách áp dụng trong Giai đoạn 3 |
|---|---|---|---|---|---|---|---|
| **Vinh** | Chủ trì | Thực hiện chính | Thực hiện chính | Thực hiện trong service | Chủ trì tích hợp | Chủ trì kiểm duyệt cuối | Chịu Clinical, Lab; xử lý các điểm nối nghiệp vụ và tổng hợp kết quả rà soát |
| **Huy** | Phối hợp | Thực hiện chính | Thực hiện trong service | Thực hiện trong service | Phối hợp | Thực hiện chính cho service | Tập trung các quy tắc khó của Pharmacy, Report, idempotency và read model |
| **Hoàng Anh** | Phối hợp | Thực hiện trong service | Thực hiện trong service | Thực hiện chính | Thực hiện chính với Gateway | Thực hiện chính cho service | Chịu Organization, Patient và lớp vào chung của hệ thống qua Gateway |
| **Lộc** | Phối hợp | Thực hiện trong service | Thực hiện trong service | Thực hiện trong service; hỗ trợ chung khi cần | Phối hợp | Thực hiện chính cho service | Chịu Billing, Notification; hỗ trợ kết nối Backend–Flutter tại các luồng liên quan |

### 1.4. Phân công chi tiết theo thành viên và service

#### 1.4.1. Vinh - Clinical Service và Lab Service

| Service | Lớp công việc | Nội dung thực hiện | Đầu ra cần bàn giao |
|---|---|---|---|
| Clinical | Backend/domain | Quản lý lịch hẹn, trạng thái lịch, hồ sơ bệnh án và chẩn đoán; kiểm tra quyền của bác sĩ, điều dưỡng và quản trị viên | Use case, domain model, port, adapter, exception và controller theo Clean Architecture |
| Clinical | Database | Quản lý `APPOINTMENT`, `MEDICAL_RECORD`, `DIAGNOSIS`; bảo đảm quan hệ nội bộ và UUID logic tới Patient/Organization | ERD đối chiếu, migration, constraint, index và dữ liệu kiểm thử |
| Clinical | API/event | Cung cấp API lịch hẹn và bệnh án; phát `appointment.created`, `appointment.status.changed`, `medicalrecord.created`, `diagnosis.added`; tiếp nhận kết quả liên quan theo hợp đồng đã chốt | DTO, validation, RBAC, publisher/consumer, correlation ID và idempotency |
| Clinical | Flutter | Màn hình danh sách/chi tiết lịch hẹn, thay đổi trạng thái, danh sách bệnh án, lập/cập nhật hồ sơ và thêm chẩn đoán | Feature Flutter Clinical hoàn chỉnh với loading, empty, success và error state |
| Clinical | Test/tài liệu | Kiểm thử chuyển trạng thái, quyền truy cập, validation bệnh nhân/bác sĩ/khoa và xử lý event trùng | Unit test, integration test, API evidence và cập nhật ma trận truy vết |
| Lab | Backend/domain | Tạo chỉ định xét nghiệm, tiếp nhận yêu cầu, cập nhật trạng thái và nhập kết quả | Use case, domain model, port, adapter và controller Lab |
| Lab | Database | Quản lý `LAB_TEST`, `LAB_RESULT`, `PROCESSED_EVENT`; bảo đảm kết quả gắn đúng yêu cầu và không xử lý event lặp | Migration, constraint, index và test dữ liệu |
| Lab | API/event | Cung cấp API tra cứu, tạo yêu cầu, thêm kết quả và đổi trạng thái; phát `lab.request.created`, `lab.result.created`; xử lý `medicalrecord.created`, `payment.completed` theo thiết kế | DTO, RBAC, event publisher/consumer, retry và deduplication |
| Lab | Flutter | Màn hình hàng đợi xét nghiệm, chi tiết yêu cầu, cập nhật trạng thái, nhập kết quả và xem lịch sử theo bệnh nhân | Feature Flutter Lab kết nối qua Gateway |
| Lab | Test/tài liệu | Kiểm thử trạng thái hợp lệ, cấu trúc kết quả, quyền `LAB_TECH`, event trùng và event đến muộn | Bộ test và bằng chứng đối chiếu SRS–Design–DB–GUI |

#### 1.4.2. Huy - Pharmacy Service và Report Service

| Service | Lớp công việc | Nội dung thực hiện | Đầu ra cần bàn giao |
|---|---|---|---|
| Pharmacy | Backend/domain | Quản lý thuốc, tồn kho, kê đơn, dòng đơn, giữ tồn và cấp phát thuốc | Aggregate, use case, port, adapter và controller Pharmacy |
| Pharmacy | Database | Quản lý `DRUG`, `PRESCRIPTION`, `PRESCRIPTION_LINE`, `DISPENSE_SLIP`, `STOCK_RESERVATION`, `PROCESSED_EVENT`; khóa dữ liệu khi trừ tồn và không cho tồn âm | Migration, ràng buộc dữ liệu, optimistic/pessimistic locking phù hợp và test concurrency |
| Pharmacy | API/event | Cung cấp API thuốc, tồn kho, đơn thuốc và cấp phát; nhận `payment.completed`; phát `prescription.created`, `prescription.filled`, `prescription.dispense.failed`, `stock.low` | API có RBAC, publisher/consumer idempotent và hợp đồng saga với Billing |
| Pharmacy | Flutter | Màn hình danh mục thuốc, tìm thuốc, điều chỉnh tồn, tạo/xem đơn và xác nhận cấp phát | Feature Flutter Pharmacy theo vai trò bác sĩ và dược sĩ |
| Pharmacy | Test/tài liệu | Kiểm thử không âm kho, không cấp trùng, đơn hợp lệ, giữ tồn hết hạn và hai nhánh saga | Test nghiệp vụ, test concurrency, test event và tài liệu API |
| Report | Backend/domain | Xây read model báo cáo lượt khám, doanh thu tháng và thống kê thuốc từ integration event | Consumer, use case truy vấn và controller Report |
| Report | Database | Quản lý `DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC`, `PROCESSED_EVENT`; cập nhật an toàn khi nhiều consumer chạy song song | Migration, unique constraint, `findOrCreate` an toàn concurrency và test tổng hợp |
| Report | API/event | Nhận event từ Clinical, Lab, Pharmacy, Billing và Organization; cung cấp API báo cáo ngày, tháng và thuốc dùng nhiều | Consumer idempotent, bộ lọc khoa/thời gian và RBAC `ADMIN`, `MANAGER` |
| Report | Flutter | Màn hình báo cáo ngày, doanh thu tháng và thuốc sử dụng; hỗ trợ lọc theo khoa, thời gian và phạm vi toàn viện | Feature Flutter Report với bảng/chỉ số và trạng thái tải dữ liệu |
| Report | Test/tài liệu | Kiểm thử event trùng, event đến muộn, tổng hợp theo khoa/toàn viện và giới hạn truy vấn | Bộ test read model và cập nhật tài liệu truy vết |

#### 1.4.3. Hoàng Anh - Organization Service, Patient Service và API Gateway

| Service | Lớp công việc | Nội dung thực hiện | Đầu ra cần bàn giao |
|---|---|---|---|
| Organization | Backend/domain | Quản lý khoa/phòng, nhân viên, tài khoản, vai trò, trạng thái tài khoản và xác minh thông tin đăng nhập | Use case, domain model, port, adapter và controller Organization |
| Organization | Database | Quản lý `DEPARTMENT`, `STAFF`, `ACCOUNT`; bảo đảm tên viết tắt, username, giấy phép và quan hệ nhân viên–khoa không bị trùng hoặc sai | Migration, constraint, index và test dữ liệu |
| Organization | API/event | Cung cấp API department, staff, account và verify credentials; phát `department.created`, `staff.created`, `staff.department.changed` | DTO, RBAC, event publisher và hợp đồng xác minh cho Gateway/Clinical |
| Organization | Flutter | Màn hình quản lý khoa, nhân viên và tài khoản; tìm kiếm, phân trang, chuyển khoa, kích hoạt/vô hiệu tài khoản | Feature Flutter Organization dành cho `ADMIN`, `MANAGER` theo quyền |
| Patient | Backend/domain | Tiếp nhận, tra cứu, cập nhật và xóa theo quyền hồ sơ hành chính bệnh nhân; chống trùng định danh | Use case, domain model, port, adapter và controller Patient |
| Patient | Database | Quản lý `PATIENT`; bảo đảm mã bệnh nhân và giấy tờ định danh duy nhất, trường bắt buộc và audit time | Migration, constraint, index và dữ liệu kiểm thử |
| Patient | API/event | Cung cấp API danh sách/chi tiết/tạo/cập nhật/xóa; phát `patient.created`, `patient.updated` | DTO, validation, RBAC và publisher event |
| Patient | Flutter | Màn hình tìm kiếm, danh sách, tạo, cập nhật và xem chi tiết bệnh nhân; giới hạn thao tác theo vai trò | Feature Flutter Patient kết nối qua Gateway |
| Gateway | Định tuyến | Duy trì chín route tới tám service, trong đó Clinical có hai tiền tố `/appointments` và `/records`; dùng service discovery | Cấu hình route không ghi cứng host và test định tuyến |
| Gateway | Xác thực và bảo vệ | Cung cấp login/refresh, phát và kiểm tra JWT, truyền role/identity, correlation ID, rate limit và phản hồi lỗi thống nhất | Filter, security configuration, token service và test xác thực |
| Gateway | Hỗ trợ Flutter | Chuẩn hóa base URL, login contract, refresh token và response envelope để các feature Flutter dùng chung qua `ApiClient` | Hợp đồng tích hợp chung; không gom phần giao diện service của thành viên khác vào Gateway |
| Organization/Patient/Gateway | Test/tài liệu | Kiểm thử phân quyền, tài khoản vô hiệu, thông tin trùng, token sai/hết hạn, route và correlation ID | Bộ test, API evidence và cập nhật tài liệu liên quan |

#### 1.4.4. Lộc - Billing Service và Notification Service

| Service | Lớp công việc | Nội dung thực hiện | Đầu ra cần bàn giao |
|---|---|---|---|
| Billing | Backend/domain | Quản lý khoản phí, hóa đơn, thanh toán và trạng thái saga thanh toán–cấp thuốc; thực hiện bù trừ khi cấp phát thất bại | Aggregate, state machine, use case, port, adapter và controller Billing |
| Billing | Database | Quản lý `FEE`, `INVOICE`, `PROCESSED_EVENT`; ngăn một nguồn phí bị ghi nhận hai lần và bảo đảm tổng tiền do server tính | Migration, constraint, index và test tính tiền |
| Billing | API/event | Cung cấp API hóa đơn, thanh toán và doanh thu; nhận event từ Clinical, Lab, Pharmacy; phát `invoice.created`, `payment.completed`, `payment.failed` | API có RBAC, consumer idempotent, publisher và correlation ID xuyên saga |
| Billing | Flutter | Màn hình danh sách/chi tiết hóa đơn, tạo hóa đơn, xác nhận thanh toán, xem trạng thái thanh toán và saga | Feature Flutter Billing dành cho `CASHIER`, `ADMIN` |
| Billing | Test/tài liệu | Kiểm thử tổng tiền, thanh toán lặp, trạng thái saga, bù trừ và event bị giao lại | Unit/integration test và bằng chứng luồng thành công/thất bại |
| Notification | Backend/domain | Nhận integration event, tạo nội dung thông báo, chọn kênh Email/SMS/In-app, lưu trạng thái và số lần thử | Use case, template, port, adapter và controller Notification |
| Notification | Database | Quản lý `NOTIFICATION`, `PROCESSED_EVENT`; ngăn thông báo trùng theo event và lưu lịch sử gửi | Migration, constraint, index và test deduplication |
| Notification | API/event | Nhận các event bệnh nhân, lịch hẹn, xét nghiệm, cấp thuốc và thanh toán; phát `notification.sent`; cung cấp API tra cứu/gửi thông báo | Consumer, retry/DLQ, kiểm tra quyền sở hữu bệnh nhân và publisher |
| Notification | Flutter | Màn hình danh sách và chi tiết thông báo của đúng bệnh nhân; trạng thái kênh gửi và lỗi thân thiện với người dùng | Feature Flutter Notification có kiểm tra dữ liệu theo JWT hiện tại |
| Notification | Test/tài liệu | Kiểm thử event trùng, retry, quyền sở hữu, template và trạng thái gửi cuối | Bộ test và tài liệu producer–consumer |

### 1.5. Ma trận trách nhiệm tích hợp

Ký hiệu: **R** - trực tiếp thực hiện; **A** - chịu trách nhiệm cuối; **C** - phối hợp/đối chiếu hợp đồng; **I** - được thông báo.

| Hạng mục tích hợp | R | A | C | I |
|---|---|---|---|---|
| Organization/Patient → Clinical validation | Hoàng Anh, Vinh | Vinh | Huy, Lộc | Cả nhóm |
| Clinical → Lab và kết quả Lab → Clinical | Vinh | Vinh | Huy | Hoàng Anh, Lộc |
| Pharmacy ↔ Billing saga | Huy, Lộc | Vinh | Hoàng Anh | Cả nhóm |
| Event nghiệp vụ → Notification | Lộc | Lộc | Vinh, Huy, Hoàng Anh | Cả nhóm |
| Event nghiệp vụ → Report read model | Huy | Huy | Vinh, Lộc, Hoàng Anh | Cả nhóm |
| Gateway → toàn bộ service | Hoàng Anh | Vinh | Huy, Lộc | Cả nhóm |
| API Gateway → các feature Flutter | Mỗi owner service | Vinh | Hoàng Anh về hợp đồng Gateway | Cả nhóm |
| Tổng hợp hồ sơ ba giai đoạn | Vinh | Vinh | Huy, Hoàng Anh, Lộc | Cả nhóm |

---

## 2. Rà soát tính đồng bộ SRS ↔ Design ↔ Database ↔ GUI

### 2.1. Nguyên tắc rà soát

Mỗi yêu cầu được đọc theo chuỗi: **vai trò/nghiệp vụ trong CRS → service trong thiết kế → bảng dữ liệu thuộc quyền sở hữu → API hoặc event → màn hình Flutter → kiểm thử chấp nhận**. Một hạng mục được xem là đồng bộ về thiết kế khi cùng thuật ngữ, cùng owner dữ liệu, không tạo liên kết database xuyên service và có đường đi rõ ràng từ thao tác người dùng đến kết quả nghiệp vụ.

### 2.2. Rà soát theo ba luồng nghiệp vụ lớn

| Luồng nghiệp vụ | Nội dung trong SRS/CRS | Thiết kế và database liên quan | Giao diện Flutter tương ứng | Owner phối hợp | Kết quả rà soát tài liệu |
|---|---|---|---|---|---|
| Quản trị, tiếp nhận và xếp lịch | Đăng nhập; quản lý khoa, nhân viên, tài khoản; tiếp nhận bệnh nhân; tạo và xem lịch | Gateway; Organization (`DEPARTMENT`, `STAFF`, `ACCOUNT`); Patient (`PATIENT`); Clinical (`APPOINTMENT`) | Login dùng chung; quản lý tổ chức; bệnh nhân; lịch hẹn | Hoàng Anh, Vinh | Vai trò, service và database đã có đường truy vết; cần triển khai các feature Flutter theo ownership mới |
| Khám bệnh, xét nghiệm và kê đơn | Lập bệnh án, chẩn đoán, chỉ định/nhập kết quả xét nghiệm, kê đơn | Clinical (`MEDICAL_RECORD`, `DIAGNOSIS`); Lab (`LAB_TEST`, `LAB_RESULT`); Pharmacy (`PRESCRIPTION`, `PRESCRIPTION_LINE`) | Hồ sơ khám, chẩn đoán, xét nghiệm và đơn thuốc | Vinh, Huy | Use Case, API, dữ liệu và event đã thống nhất ở mức tài liệu; cần kiểm thử hợp đồng giữa các service |
| Thanh toán, cấp thuốc, thông báo và báo cáo | Lập hóa đơn, thanh toán, cấp thuốc, bù trừ, xem thông báo và báo cáo | Billing (`FEE`, `INVOICE`); Pharmacy (`DISPENSE_SLIP`, `STOCK_RESERVATION`); Notification; Report | Hóa đơn/thanh toán, cấp phát, thông báo và báo cáo | Lộc, Huy | Saga và event topology đã được mô tả; `prescription.dispense.failed` phải được giữ nhất quán trong catalog và code |

### 2.3. Ma trận truy vết theo service

| Service | Yêu cầu/Use Case chính | Thiết kế dữ liệu | API hoặc event chính | GUI Flutter cần có | Owner |
|---|---|---|---|---|---|
| Organization | Quản lý khoa, nhân viên, tài khoản và vai trò | `DEPARTMENT`, `STAFF`, `ACCOUNT` | `/api/v1/org/**`; các event department/staff | Department, Staff, Account | Hoàng Anh |
| Patient | Tiếp nhận, tìm kiếm và cập nhật bệnh nhân | `PATIENT` | `/api/v1/patients/**`; `patient.created`, `patient.updated` | Patient List, Detail, Form | Hoàng Anh |
| Clinical | Lịch hẹn, bệnh án và chẩn đoán | `APPOINTMENT`, `MEDICAL_RECORD`, `DIAGNOSIS` | `/appointments/**`, `/records/**`; event lịch/bệnh án | Appointment, Medical Record, Diagnosis | Vinh |
| Lab | Chỉ định, trạng thái và kết quả xét nghiệm | `LAB_TEST`, `LAB_RESULT`, `PROCESSED_EVENT` | `/api/v1/lab/**`; event request/result | Lab Queue, Lab Detail, Result Form | Vinh |
| Pharmacy | Thuốc, tồn kho, đơn và cấp phát | Sáu bảng Pharmacy theo ERD | `/api/v1/pharmacy/**`; event prescription/stock | Drug, Inventory, Prescription, Dispense | Huy |
| Billing | Khoản phí, hóa đơn, thanh toán và saga | `FEE`, `INVOICE`, `PROCESSED_EVENT` | `/api/v1/billing/**`; event invoice/payment | Invoice, Payment, Saga Status | Lộc |
| Notification | Tạo, gửi và tra cứu thông báo | `NOTIFICATION`, `PROCESSED_EVENT` | `/api/v1/notifications/**`; `notification.sent` | Notification List, Detail | Lộc |
| Report | Báo cáo lượt khám, doanh thu và thuốc | Bốn bảng read model theo ERD | `/api/v1/reports/**`; consumer event tổng hợp | Daily, Monthly, Top Medicines | Huy |
| Gateway | Đăng nhập, refresh token, bảo vệ và định tuyến | Không sở hữu database nghiệp vụ | `/api/v1/auth/**` và chín route `lb://` | Auth shell và `ApiClient` dùng chung | Hoàng Anh |

### 2.4. Các điểm đồng bộ đã chốt

- Tám bounded context trong CRS khớp tám service và tám database trong thiết kế.
- Clinical là một service nhưng có hai nhóm URL: lịch hẹn và bệnh án.
- Mỗi service chỉ tạo khóa ngoại trong database của mình; định danh xuyên service là UUID logic hoặc được xác minh qua REST.
- Web/Mobile đều phải đi qua Gateway; trong phân công mới, mỗi owner chịu màn hình Flutter của chính service.
- Tên bảng và cột trong bộ thiết kế Giai đoạn 2 đã được chuẩn hóa bằng tiếng Anh; thuật ngữ service và event giữ thống nhất giữa ERD, DDL và backend spec.
- Billing và Pharmacy phối hợp bằng saga, không mở transaction phân tán.
- Notification và Report xử lý event theo cơ chế idempotent; event trùng không tạo thông báo kép hoặc cộng số liệu hai lần.

### 2.5. Khoảng cách cần được phản ánh trung thực

- Thư mục `mobile/` hiện mới có tài liệu hướng dẫn kiến trúc, chưa có project Flutter và feature theo bounded context. Vì vậy, các màn hình Flutter trong bảng phân công là đầu ra phải triển khai, chưa phải bằng chứng hoàn thành.
- Mức độ hiện thực Backend giữa các service chưa đồng đều; việc có đặc tả, ERD hoặc DDL chưa tự động chứng minh service đã đạt Definition of Done.
- Web Next.js hiện có khung đăng nhập, trang tổng quan và trang bệnh nhân, nhưng không thay thế phạm vi Flutter đã được nhóm phân công trong tài liệu này.
- Chỉ được tuyên bố đồng bộ hoàn toàn sau khi API contract, migration, event contract, RBAC và test của từng service được đối chiếu với màn hình Flutter thực tế.

---

## 3. Chỉnh sửa, bổ sung theo kết quả rà soát

### 3.1. Các nội dung đã chỉnh sửa trong bộ hồ sơ

| Nhóm chỉnh sửa | Nội dung đã thực hiện | Tài liệu chịu ảnh hưởng | Kết quả |
|---|---|---|---|
| CRS và biểu mẫu | Trình bày yêu cầu theo từng lớp người dùng; nhóm Use Case–Task–Process theo ba luồng lớn; chỉ liệt kê NFR ở mục riêng | Giai đoạn 1 | Cấu trúc yêu cầu dễ đọc và truy vết hơn |
| Chuẩn hóa database | Chuyển tên bảng/cột kỹ thuật còn tiếng Việt sang tiếng Anh, đồng bộ Organization, Patient, Notification và các service liên quan | Giai đoạn 2 và backend spec | ERD, DDL và tài liệu service dùng cùng thuật ngữ |
| ERD chi tiết | Bổ sung ERD–luồng cho đủ tám service và ERD tổng quan hệ thống | Giai đoạn 2 | Thể hiện cả dữ liệu sở hữu, UUID/REST logic và integration event |
| Hình dùng cho Word | Xuất SVG Word-safe và PNG 2400 px; khóa Arial trực tiếp trên từng phần tử chữ của các ERD–luồng | Giai đoạn 2 | Giảm lỗi mất màu và thay font khi chèn vào Word |
| Phân công service | Chốt lại Clinical/Lab cho Vinh; Pharmacy/Report cho Huy; Organization/Patient/Gateway cho Hoàng Anh; Billing/Notification cho Lộc | Giai đoạn 3 | Mỗi service có owner rõ ràng |
| Phân công Flutter | Bỏ cách hiểu Hoàng Anh chịu toàn bộ Mobile; mỗi owner làm giao diện Flutter của service mình | Giai đoạn 3 | Trách nhiệm Backend–Flutter được nối theo bounded context |
| Final Review | Bỏ bảng ký xác nhận và checklist đánh dấu; thay bằng phần tóm tắt nội dung chính của ba file giai đoạn | Giai đoạn 3 | Phù hợp mục đích tổng hợp hồ sơ |

### 3.2. Các bổ sung cần thực hiện trong quá trình code

| Ưu tiên | Bổ sung | Người chịu trách nhiệm | Điều kiện hoàn tất |
|---|---|---|---|
| P0 | Tạo project Flutter theo Clean Architecture và feature theo bounded context | Mỗi owner cho feature của mình; Hoàng Anh tạo phần core/Gateway dùng chung | Project chạy được; feature gọi API qua Gateway; không gọi thẳng service |
| P0 | Hoàn thiện migration và đối chiếu ERD cho từng database | Owner từng service | Migration chạy sạch trên database mới và khớp ERD |
| P0 | Hoàn thiện API, RBAC và validation theo backend spec | Owner từng service | Endpoint có DTO, validation, phân quyền và test tương ứng |
| P0 | Chốt event catalog, đặc biệt `prescription.dispense.failed` | Huy, Lộc; Vinh rà soát tích hợp | Publisher, consumer, routing key và payload cùng một hợp đồng |
| P1 | Kiểm thử saga Pharmacy–Billing ở cả nhánh thành công và bù trừ | Huy, Lộc | Không cấp trùng, không âm kho, trạng thái hóa đơn/saga truy vết được |
| P1 | Kiểm thử idempotency của Lab, Billing, Pharmacy, Notification và Report | Owner từng service | Event giao lại không làm thay đổi kết quả nghiệp vụ lần hai |
| P1 | Đối chiếu màn hình Flutter với role và quyền sở hữu dữ liệu | Mỗi owner; Vinh kiểm duyệt cuối | UI đúng vai trò, backend vẫn thực thi RBAC và ownership |
| P2 | Cập nhật lại ma trận truy vết sau mỗi thay đổi API/event | Owner thay đổi hợp đồng | SRS, Design, DB, API và Flutter không dùng tên hoặc kiểu dữ liệu lệch nhau |

---

## 4. Kiểm duyệt toàn bộ gói tài liệu thiết kế (Final Review)

Phần này chỉ tổng hợp nội dung chính của ba file giai đoạn để người đọc nắm nhanh cấu trúc toàn bộ hồ sơ. Đây không phải bảng ký xác nhận và không phải checklist đánh dấu đạt/chưa đạt.

| Giai đoạn và file chính | Nội dung chính |
|---|---|
| **Giai đoạn 1** - [giai-doan-1-crs-bieu-mau.md](../01-giai-doan-1-khoi-dong-dac-ta/giai-doan-1-crs-bieu-mau.md) | Xác định bối cảnh và vấn đề của hệ thống quản lý bệnh viện; đề xuất giải pháp MediFlow; mô tả kiến trúc ở mức khởi động; đặc tả CRS theo từng lớp người dùng; trình bày Use Case, Task và Process theo ba luồng nghiệp vụ lớn; liệt kê các yêu cầu phi chức năng cho hệ thống phân tán. |
| **Giai đoạn 2** - [giai-doan-2-design.md](../02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/giai-doan-2-design.md) | Quy định bảng màu, kiểu chữ và bố cục Web/Mobile; thiết kế tám database, quan hệ đối tượng, ERD, DDL; mô tả kiến trúc microservices, REST/event, saga; trình bày luồng nghiệp vụ tổng quát và các sequence flow chi tiết; cung cấp sơ đồ SVG/PNG có thể chèn vào Word. |
| **Giai đoạn 3** - `giai-doan-3-final-review.md` | Chốt owner cho tám service và Gateway; phân công chi tiết Backend, Database, API/event, test và Flutter; mô tả năng lực và phạm vi áp dụng của bốn thành viên; rà soát chuỗi SRS–Design–Database–GUI; ghi nhận nội dung đã chỉnh sửa và các bổ sung cần thực hiện khi code. |

Ba file tạo thành một chuỗi liên tục: Giai đoạn 1 xác định **hệ thống cần làm gì**, Giai đoạn 2 xác định **hệ thống được thiết kế như thế nào**, và Giai đoạn 3 xác định **ai chịu trách nhiệm hiện thực, đối chiếu và hoàn thiện từng phần**.

---

**Tài liệu tham chiếu:**

- [Backend implementation specs](../../eproject_general_plan/backend-spec/README.md)
- [Flutter Mobile Blueprint](../../ai/14-flutter.md)
- [API conventions](../../ai/05-api-conventions.md)
- [Event/RabbitMQ conventions](../../ai/06-events-rabbitmq.md)
