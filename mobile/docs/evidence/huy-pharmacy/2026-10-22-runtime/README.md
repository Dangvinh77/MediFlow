# Mobile runtime — 2026-10-22

Người dùng báo đã mở Docker và yêu cầu chạy. Đã start PostgreSQL/RabbitMQ qua Compose,
đóng gói lại Eureka/Gateway/Core/Pharmacy từ working source bằng Maven package -DskipTests,
rồi chạy Java21 local. Không chạy lại backend tests trong bước đóng gói này.
Không khởi động toàn bộ sáu business deployments hoặc bật gates V1/V2.

| Thành phần | Kiểm tra thực tế |
|---|---|
| PostgreSQL/RabbitMQ | Container healthy |
| Eureka8761 | Actuator UP |
| Gateway8080 | Actuator UP, profile outpatient-mvp |
| Core8089 | Actuator UP |
| Pharmacy8085 | Actuator UP, đã đăng ký Eureka |
| Mobile4300 | HTML200; browser hiển thị màn hình đăng nhập |
| Proxy /api/v1/pharmacy/drugs | Không token trả401; chưa chứng minh request hợp lệ đến Pharmacy |

Pharmacy database hiện có chứa bảng nhưng thiếu flyway_schema_history nên boot ban đầu bị chặn.
Không baseline/repair/delete dữ liệu đó. Tạo database riêng `mediflow_pharmacy_mobile_20261022`,
chạy Pharmacy với datasource override bằng launch argument; Flyway áp dụng20 migrations đếnv20.
Database mới chưa có dữ liệu thuốc/đơn, không phải môi trường đã nghiệm thu trước đây.

[runtime.json](runtime.json) ghi health/status, [ảnh login](login.png) ghi browser smoke thật.
Raw logs/PIDs local ở `.git/mobile-runtime-*`, proxy giữ ở phiên terminal task hiện hành.
Mobile dùng build candidate288 tests/104 formatted files từ checkpoint readiness; không build
lại Flutter hoặc thay app/core/shared network trong lượt chạy này.

**Giới hạn:** mới kiểm startup/login screen và unauthenticated401. Chưa đăng nhập bằng tài khoản
nghiệp vụ thật, chưa root-bind Pharmacy (task11 giữ dependency), chưa real writer/dispenser adapters.
Không coi runtime UP là acceptance Rx→payment→dispense hoặc bằng chứng stock/clearance/one-effect.
Task completion vẫn8/12. Shared network/root integration giữ theo lựa chọn người dùng.
