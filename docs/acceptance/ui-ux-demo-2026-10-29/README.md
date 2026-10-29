# Kiểm thử UI ngoại trú và khắc phục UX

Phiên click UI: 29/10/2026, khoảng 18:09–19:22 giờ Việt Nam. Báo cáo chốt ngày 30/10/2026.
Đối chiếu `scripts/demo_25_10/DEMO_UI_TUNG_BUOC.md`, dữ liệu tổng hợp trên stack demo cách ly.
Frontend chạy chế độ phát triển, viewport khoảng 674×668. Không thay đồng hồ, không sửa DB để vượt điều kiện nghiệp vụ.

## Kết luận phạm vi

**Chưa nghiệm thu một bệnh nhân mới đi trọn luồng từ đăng ký tới nhận thuốc.** Phiên chạy ngoài giờ khám nên lịch seed là ngày hôm sau; `historyReady=false`. Các bước hạ nguồn được click trên một ca mô phỏng đang khám có sẵn, tách biệt với hai phiếu mới tạo.

| Bước trong hướng dẫn | Thao tác và kết quả UI | Đánh giá |
|---|---|---|
| 2A: hồ sơ mới | Nurse tạo Demo UI Nguyễn Lan 29; thử BHYT sai, sửa đúng; cấp tài khoản và chuyển đăng ký với bệnh nhân chọn sẵn | Đạt |
| 3–4: bệnh nhân mới | Đăng ký khám mới PREBOOKED ngày 30/10, thu 50.000 VND, có số thứ tự; bác sĩ thấy phiếu xác nhận/chưa đến | Đạt phần đăng ký/thu |
| 2B: bệnh nhân cũ khám đợt mới | Tìm Nguyễn Văn An Demo, dùng hồ sơ cũ, không cấp lại tài khoản; đăng ký khám mới, thu 50.000 VND | Đạt |
| 2C: tái khám | Tìm Trần Thị Bình Demo; không có lượt nguồn hợp lệ, UI chặn đăng ký và yêu cầu chọn lượt hoàn tất cùng khoa | Đạt chặn sai; chưa thử đường thành công |
| 5: bắt đầu lượt mới | Phiếu ngày mai chưa tới giờ, chưa ghi nhận đến; không có thao tác bắt đầu khám | Chưa thử trong giờ |
| 5–8: Lab trên ca có sẵn | Bác sĩ lập CBC/LAB_CBC, thu ngân thu 36.000 VND; kỹ thuật viên bắt đầu, nhập WBC 7.0 G/L (4–10), lưu hoàn tất; bác sĩ mở xem kết quả | Đạt trên ca riêng |
| 8–9: thuốc trên cùng ca có sẵn | Kê 2 thuốc A, hoàn tất hồ sơ, thu 20.000 VND, dược sĩ xuất thuốc; đơn hoàn tất, không còn nút xuất lại; UI tồn từ 291 xuống 289 | Đạt trên ca riêng |
| 10: tài khoản mới | Đăng nhập mật khẩu ban đầu vào màn hình bắt buộc đổi mật khẩu; truy cập thẳng trang kết quả vẫn bị chuyển lại | Đạt chuyển hướng; chưa đổi mật khẩu |
| 10: cổng bệnh nhân cũ | demo.patient thấy thông báo đăng ký/xác nhận và phiếu của mình; kết quả khám/Lab của tài khoản này còn rỗng | Đạt phần đã có dữ liệu; chưa xác nhận trả kết quả trên portal |
| Thu lại sau sửa | Tạo CBC riêng cho Demo CL06_IN_EXAM_WITH_DIAGNOSIS; dán mã bàn giao, tìm/chọn tên; thu 36.000 VND; tự đọc lại PAID, còn phải thu 0, giữ mã giao dịch | Đạt |

Việc đổi mật khẩu phải do người dùng tự nhập và gửi theo quy định công cụ điều khiển UI. Không nhập mật khẩu mới thay người dùng.
Không cộng các khoản của những bệnh nhân khác nhau thành tổng 106.000 VND của một lượt khám.

## Vấn đề đã sửa

| Vấn đề quan sát | Điều chỉnh | Kiểm chứng |
|---|---|---|
| BHYT sai hiện hộp lỗi trình duyệt tiếng Anh, thiếu ví dụ | Dùng bộ kiểm tra form tiếng Việt hiện có; ghi rõ mẫu `01-12345678-9`, cho phép để trống | Click gửi sai và ảnh trước/sau; test hồi quy |
| Chưa tìm lịch đã báo không có lịch | Phân biệt chưa tải, đang tải, tải rỗng; bỏ lịch cũ trong lúc tải lại | Click UI sau sửa; test tải chậm/thất bại |
| Khó bàn giao mã phiếu, thông báo phiếu cũ còn dưới bệnh nhân mới | Ô mã chỉ đọc có thể chọn sao chép, liên kết phiếu; xóa kết quả khi đổi bệnh nhân/khoa/buổi/loại lượt | Click tạo phiếu bệnh nhân cũ và test hồi quy |
| Đã thu phí khám nhưng phiếu vẫn ở danh sách chờ | Loại phiếu có thanh toán hoàn tất khỏi hàng chờ, kể cả khi projection trả dữ liệu cũ | Thu trực tiếp, danh sách rỗng; test dữ liệu cũ |
| Đã thu Lab/thuốc nhưng số dư cũ vẫn hiện | Sau kết quả COMPLETED, đọc lại đúng yêu cầu từ Billing; giữ biên nhận nếu đọc lỗi và ngăn thu lại từ dữ liệu cũ | CBC thứ hai hiện PAID/0; test đọc lỗi, không gửi POST lần hai |
| Dán UUID bệnh nhân rồi tìm không được | UUID không có scope được resolve thành lựa chọn có tên; vẫn bắt buộc bấm chọn; scoped picker giữ tìm theo scope | Click tìm/chọn và thanh toán; test không vượt scope/không chọn ID không tồn tại |
| Bác sĩ khó quay từ hồ sơ tới kết quả đúng lượt | Liên kết xét nghiệm của lượt khám mở danh sách đã lọc mã đợt; có cả khi hồ sơ đã hoàn tất | Click hồ sơ hoàn tất → danh sách lọc → kết quả; test bộ lọc đầu tiên |
| Thiếu mã bàn giao Lab; bảng kết quả tràn ngang | Khối bàn giao 3 mã thật; bỏ chiều rộng tối thiểu bảng, sửa hướng dẫn kỹ thuật thành hướng dẫn nghiệp vụ | Click mở bàn giao; ảnh WBC đủ 4 cột ở viewport thử |

Ảnh chọn lọc chỉ chứa dữ liệu mô phỏng; các ảnh thô và nhật ký cục bộ không đưa vào PR.

- [BHYT trước sửa](screenshots/02-bhyt-invalid-before.jpg), [sau sửa](screenshots/16-bhyt-validation-after.jpg).
- [Tiếp nhận trước tải — trước](screenshots/04-reception-not-searched-before.jpg), [sau](screenshots/17-reception-initial-after.jpg).
- [Hàng chờ sau thu](screenshots/20-exam-queue-cleared-after-payment.jpg).
- [Bắt buộc đổi mật khẩu](screenshots/21-new-patient-forced-password-change.jpg).
- [Kết quả Lab vừa viewport](screenshots/26-lab-results-responsive-after.jpg).
- [Thanh toán PAID, còn 0](screenshots/27-lab-payment-zero-balance-after.jpg).
- [Đơn đã cấp thuốc](screenshots/14-prescription-dispensed.jpg).

## Đo thời gian trong trình duyệt

Đọc Performance API trong phiên dev, không phải kiểm thử tải hay Core Web Vitals:

| Mẫu | Thời gian |
|---|---|
| Tài liệu trang Billing: load / DOMContentLoaded | 392 / 309 ms |
| Tra danh bạ / tra khoản phí / lấy báo giá | 33 / 39 / 34 ms |
| Gửi thanh toán Lab / đọc lại số dư | 260 / 32 ms |
| Tài liệu trong phiên hồ sơ → Lab: load / DOMContentLoaded | 527 / 342 ms |
| 11 yêu cầu API quan sát trong phiên hồ sơ → Lab | 61–168 ms |

Không suy ra p95, khả năng chịu tải hay hiệu năng production từ các mẫu này. Console trang Billing cuối phiên không có lỗi trong mẫu thu được.

## Kiểm tra mã nguồn

- 183 test đạt, 20 file: ReferenceInput; PatientForm/logic; ReceptionWorkspace; các component Billing và Lab; MedicalRecordDetail.
- TypeScript và ESLint chạy lại trước commit. File route types trong `.next/dev/types` bị hỏng do cache dev; dừng frontend, chuyển cache hỏng ra khỏi đường dẫn types và tạo lại route types. Không sửa source để né lỗi compiler.
- Có cảnh báo React `act(...)` từ test InvoiceDetail/InvoiceForm hiện hữu, không có test thất bại.
- Không sửa backend hoặc thay điều kiện giờ khám/tài chính/đổi mật khẩu.

## Còn cần nghiệm thu

1. Khởi động lại bằng hai BAT trong 07:00–16:00; xác nhận seed báo `FOLLOW-UP SOURCE READY`.
2. Thử cả ba điểm bắt đầu: bệnh nhân mới, bệnh nhân cũ khám đợt mới, tái khám có lượt nguồn; đi trọn một ca đến nhận thuốc, xác nhận đúng tổng các khoản của cùng ca.
3. Người dùng tự hoàn tất đổi mật khẩu tài khoản mới; kiểm portal của chính ca đã có kết quả và thông báo trả kết quả/tái khám.
4. Bàn giao Lab/thuốc vẫn cần sao chép mã; chưa có hàng chờ thu tự động xuyên quầy. Khối cấp tài khoản sau tạo hồ sơ vẫn phụ thuộc `notice=created`; đây là hạn chế UX còn lại, chưa thay đổi trong đợt sửa này.
5. Cần kiểm thêm màn hình rộng/mobile và dữ liệu nhiều chỉ số/tên dài; ảnh WBC một dòng không chứng minh mọi bảng đều responsive.

## Kết thúc demo

Ngày 30/10: dừng đúng tiến trình frontend của repository; chạy `clean-docker.bat`. Lần đầu gặp race container vừa được runner tự gỡ (`No such container`); lần chạy lại exit 0. Gỡ thêm đúng hai image nền postgres:16-alpine và rabbitmq:3.13-alpine của demo vì lần lỗi đầu mất danh sách ứng viên. Xác minh không còn container và không còn cổng frontend/8 ứng dụng demo lắng nghe. Giữ volume và image MCP của công cụ; không chạy global prune.
