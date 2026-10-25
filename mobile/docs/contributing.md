# Bổ sung module mobile

1. Đọc AGENTS gốc/mobile/feature, docs/ai/14-flutter.md và chạy app theo README trước khi sửa.
2. Chọn một luồng nhỏ; khóa path, role, query, DTO, serializer và envelope từ backend thật. Không suy ra ID, giá tiền hoặc trạng thái. Thiếu producer contract thì tạo handoff và đăng ký docs/handoffs/README.md.
3. domain/: model thuần Dart, repository interface và use case. Không import Flutter, Dio, SecureStorage hoặc feature khác.
4. data/: DTO đọc wire và adapter gọi ApiClient chung qua Gateway. Tham khảo appointment/lab. Kiểm tra field bắt buộc, không đổi response sai thành danh sách rỗng; PageDecoder đọc Spring page.
5. presentation/: provider theo interface domain, state/controller/page; không tạo concrete adapter. Có loading/error/empty/retry, chặn submit đôi, bỏ qua response cũ sau điều hướng.
6. Nhờ Vinh bind adapter tại app/dependencies.dart, nối page tại app/router.dart, cập nhật quyền tại app/modules.dart. Quyền phản ánh backend.
7. Test contract/rule và tình huống lỗi, chạy tool/check.ps1, kiểm tra API thật với đúng role; ghi rõ kết quả và điều kiện môi trường.

## Làm song song

Mỗi dev sửa feature sở hữu và test có tên context riêng. app/, core/, auth, pubspec/lock và platform hosts do Vinh điều phối. Không import feature khác, truy cập database hoặc service port. Composition root app/dependencies.dart bind domain interface sang data adapter. core/providers.dart nối phiên với Authorization. Không thêm HTTP client riêng hoặc log token; không tự retry lệnh thanh toán/tạo đơn.

## Từng bước cho Lộc

1. Đọc [registry bàn giao hiện hành](../../docs/handoffs/README.md) và [hợp đồng hệ thống](../../docs/plans/03-contracts.md); hoàn thành tiêu chí backend liên quan trước khi bật luồng phụ thuộc. Checkpoint Billing/Notification cũ nằm trong history, không giao lại ownership backend.
2. Làm danh sách hóa đơn chỉ đọc trong billing/: khóa DTO/role thật, repository, use case, page và test phân trang/lỗi; bàn giao Vinh nối route.
3. Làm chi tiết: phân biệt thiếu quyền, không tồn tại, chưa sẵn sàng và đã thanh toán. Không biến lỗi API thành “chưa thanh toán”.
4. Chỉ thêm thanh toán sau khi khóa hợp đồng idempotency/trạng thái backend. Chặn nhấn đôi, không suy luận thành công từ việc đã gửi request.
5. Làm notification riêng, không nhúng billing adapter hay truy vấn DB vào notification.
6. Mỗi bước bàn giao: file sửa, API/role, test đã chạy, cách thử và dependency còn chờ. Màn hình hiển thị chưa đồng nghĩa luồng hoàn tất.

## Chờ owner nào

| Hợp đồng thiếu | Owner |
|---|---|
| Bệnh nhân/nhân sự/khoa, auth, Gateway | Hoàng Anh |
| Lịch hẹn/hồ sơ khám/Lab | Vinh |
| Thuốc/đơn thuốc/báo cáo | Huy |
| Invoice discovery/publication/thanh toán/notification (backend) | Vinh |

Lịch hẹn/Lab đọc được từ endpoint hiện có. Luồng thiếu hợp đồng giữ gate hiện tại và theo handoff, không thêm dữ liệu giả.
