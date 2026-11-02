# Nghiệm thu đợt đầu cải thiện ngoại trú — 02/11/2026

Nhánh `codex/outpatient-ux-foundation`, nền `99b80114`. Phạm vi theo
[kế hoạch](../../superpowers/plans/2026-11-02-outpatient-ux-foundation.md).
Dữ liệu tổng hợp, giờ máy thực tại Việt Nam; không đổi đồng hồ để vượt giờ khám.

## Kiểm tra mã nguồn

| Kiểm tra | Kết quả |
|---|---|
| Vitest: Appointment components, Lab components, Lab-order workspace và ClinicalLabOrderForm | 21 tệp, 149 kiểm thử đạt |
| Chạy lại ReceptionWorkspace sau khi khóa thao tác đổi buổi khi kết quả tạo chưa rõ | 12/12 đạt |
| Chạy lại LabDetail + LabOrderWorkspace sau khi thêm nhãn ngày | 26/26 đạt |
| `pnpm typecheck` | Đạt |
| `pnpm lint` | Đạt, không cảnh báo sau chỉnh cleanup |
| `pnpm build`, frontend dev đã dừng trước khi build | Đạt |
| Rà soát độc lập theo yêu cầu rồi chất lượng mã | Đạt; lỗi đổi buổi khi retry chưa rõ kết quả đã được tái hiện và sửa |
| Quy tắc kiến trúc Report | Đạt; ngoại lệ gọi xác minh Core giới hạn đúng cấu hình và ba kiểu phụ thuộc, vẫn cấm Feign và phụ thuộc sai lớp |

Kiểm thử reception chứng minh: lưu thành công → tải lại số chỗ/danh sách; lỗi tải lại
không xóa phiếu hay tạo lại; kết quả đọc muộn không ghi đè khoa mới; retry chưa rõ kết
quả giữ nguyên mã chống trùng. Kiểm thử Lab chứng minh ngữ cảnh đúng người, nguồn
chỉ định không khớp bị chặn, đổi hồ sơ/phiên không giữ chỉ định cũ và chỉ đọc lại khi
xác minh hồ sơ thất bại.

Một kiểm thử cũ của lưới ca phụ thuộc ngày hiện tại đã được cố định ngày khởi đầu,
để thao tác đổi ngày luôn thực sự đổi giá trị; không đổi logic nghiệp vụ để làm test qua.

## Backend và UI thực tế

- `mvn -q -pl backend/report-service -am test`: 1.457 kiểm thử đạt, 0 lỗi, 0 bỏ qua;
  có các ca PostgreSQL/RabbitMQ thật. Lượt đầy đủ này chạy trước bản chỉnh nhỏ 4xx bên dưới.
- Sau đó đã tái hiện lỗi năm lần Core trả 403 làm mở circuit chung. Sửa để từ chối
  quyền 4xx thông thường không tính là mất kết nối; 429, timeout và lỗi máy chủ vẫn
  có bảo vệ circuit. Kiểm thử HTTP thật chứng minh năm 403 vẫn CLOSED và người hợp lệ
  tiếp theo được đọc; kiểm thử timeout mở circuit vẫn đạt.
- Bản nguồn cuối được chạy lại 23 kiểm thử: adapter HTTP (2), Report scope với JWT
  thật (7), kiến trúc (7), Support host security (7); 0 lỗi/0 bỏ qua. Đã package lại
  Report library và Support executable. Không chạy lại toàn reactor sau chỉnh 4xx;
  không chạy các profile nghiệm thu tùy chọn.
- Đã kiểm tra trực tiếp các XML kết quả của 23 ca cuối, không chỉ dựa lời báo hoàn tất.

## UI thật — phiên 10:44–10:58 giờ Việt Nam

Source ứng dụng `5e94fe13`, dữ liệu cách ly `snapshot-20261102-104443`;
`FOLLOW-UP SOURCE READY`, `CALENDAR READY` (1.104 buổi), `READY` đều đã xuất hiện.
Trình duyệt trong ứng dụng, không mở toàn màn hình; ảnh viewport mặc định và hai
ảnh toàn trang được giữ riêng. Không suy ra số đo hiệu năng từ thời gian điều khiển.

Một lượt mới liên tục cho **bệnh nhân cũ Nguyễn Văn An Demo**, không ghép các hồ sơ
lịch sử thành cùng một hành trình:

| Bước | Quan sát thực tế | Ảnh |
|---|---|---|
| Nurse tiếp nhận khám đợt mới, có mặt tại quầy | Bác sĩ Lê, khoa Nội, 02/11 07:00–17:00. Sau lưu, số chỗ 39 → 38 và An xuất hiện ngay trong danh sách; giữ phiếu thành công. | [Trước](screenshots/01-reception-before.png), [sau](screenshots/02-reception-after.png), [toàn trang sau](screenshots/03-reception-full.png) |
| Cashier thu khám | Đúng An; thu 50.000 VND mô phỏng, phiếu được xác nhận/số 2. | [Thu khám](screenshots/04-cashier-confirmed.png) |
| Doctor bắt đầu khám và tạo hồ sơ | Từ lịch cá nhân mở đúng phiếu, xác nhận bắt đầu, mở hồ sơ mới; ngữ cảnh chỉ định có An/ngày sinh/ngày khám/khoa/bác sĩ. Tạo CBC/LAB_CBC và gửi nguyên nguồn sang Lab. | [Ngữ cảnh chỉ định](screenshots/06-new-record-lab-order-context.png) |
| Lab chờ tài chính | Đúng An/CBC/ngày yêu cầu; hiển thị Ngoại trú; metadata hỗ trợ đóng mặc định, bàn giao thu ngân còn truy cập được. | [Phiếu mới](screenshots/07-new-lab-awaiting-payment.png) |
| Cashier thu CBC | Thu 36.000 VND mô phỏng theo báo giá, còn 0; không nhập số tiền tùy ý. | [Thu Lab](screenshots/08-lab-payment-confirmed.png) |
| LAB_TECH nhận và trả kết quả | Hàng chờ đã có đúng An/Sẵn sàng/xác nhận tài chính. Bắt đầu → nhập WBC 7.0 G/L, tham chiếu 4–10 → hoàn tất. Ngữ cảnh An vẫn có tại form và thanh cố định. | [Mở form](screenshots/09-lab-result-entry-context.png), [nhập](screenshots/10-lab-result-filled.png), [hoàn tất](screenshots/11-lab-completed.png) |
| Patient xem kết quả | `demo.patient` thấy chính CBC/WBC vừa trả và kết luận nghiệm thu 02/11. | [Kết quả của tôi](screenshots/12-patient-current-result.png) |
| Manager không có scope | Xem toàn viện và chọn khoa Nội đều nhận FORBIDDEN, không trả aggregate số 0. FE vẫn còn hướng dẫn “để trống khoa”, đã bàn giao Huy; không đánh dấu UI Report hoàn thiện. | [Toàn viện](screenshots/13-manager-hospital-report-denied.png), [khoa chưa được giao](screenshots/14-manager-unassigned-department-denied.png), [toàn trang](screenshots/15-manager-denied-full.png) |

Mã đối soát chỉ cho dữ liệu tổng hợp của phiên đã dọn:

- Phiếu: `609ce208-620d-4cbe-bd44-ccf818ba7963`.
- Hồ sơ mới: `60f66ab6-7fe4-41fc-9eb6-82f9c4f4d700`.
- Lab mới: `b9b76e0c-896b-4aa1-860e-e756c214c6c1`.

Lỗi môi trường tìm được: từ hồ sơ bấm chỉ định ban đầu trả [404](screenshots/05-stale-dev-route-404.png),
dù route có trong source và build/type manifest. Không đổi source/URL; dừng frontend,
chuyển riêng cache `.next/dev` sang thư mục `target` gốc dự án, mở lại frontend thì
cùng route trả trang chỉ định đúng. Backend và lượt bệnh nhân được giữ nguyên.
Đây là xử lý cache dev cũ, không tuyên bố đã sửa một lỗi định tuyến trong source.

Lỗi đọc lại sau tạo và retry chưa rõ kết quả được kiểm chứng bằng regression tests;
phiên browser này không giả lập đứt mạng hoặc tạo giao dịch trùng để minh họa.

## Dọn sau nghiệm thu

10:58: đăng xuất và đóng tab kiểm thử do agent mở; giữ nguyên các tab của người dùng.
Dừng đúng frontend của repository. Chạy `clean-docker.bat` thành công, dừng phiên
backend hiện tại, dọn cả container rehearsal cũ còn sót và image Postgres/RabbitMQ
liên quan. Script giữ volume và dự án khác, không global prune. Kiểm tra sau dọn:
không còn listener 3000/28080/28082/28084/28085/28086/28087/28089/28761 và danh sách
container rỗng. Cả 15 ảnh nghiệm thu đã được mở xác minh định dạng.

## Giới hạn

- Đây là đợt đầu MF-01/MF-02 và một phần MF-05/MF-08/MF-27, không đóng 30 mục review.
- FE Report/Pharmacy/Patient/Organization có [bàn giao theo owner](../../../frontend/docs/handoffs/HANDOFF-OUTPATIENT-PRODUCT-OWNER-UX.md).
- Chỉ định Lab còn hai bước và cần phục hồi bền vững khi rời trang trong đợt tiếp theo.
- Phiên này đã chạy cùng lượt bệnh nhân cũ đến trả/xem kết quả Lab trong giờ thực;
  chưa tạo bệnh nhân/cấp tài khoản/đổi mật khẩu, chưa kê đơn/thu thuốc/cấp thuốc,
  chưa hoàn tất hồ sơ và chưa nghiệm thu UI bổ nhiệm/miễn nhiệm. Không gọi đây là
  toàn hành trình bệnh nhân mới đến nhận thuốc; Admin/Pharmacist không được thử lại trong đợt này.
- Surgery/Inpatient tiếp tục deferred.
