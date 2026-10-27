# Một lượt nghiệm thu trực tiếp cho Vinh, Huy và Hoàng Anh

Candidate: PR [#547](https://github.com/Dangvinh77/MediFlow/pull/547), code `d496b49e`.
Kết quả tự động và giới hạn: [biên bản bằng chứng](../acceptance/demo-readiness-2026-10-27/README.md).
Tài liệu này gom lượt thử; không thay các tiêu chí [06](06-acceptance-runbook.md),
[ONB trong 07](07-outpatient-workflow-implementation.md) và [SC-01–20](10-scheduling-contract-acceptance.md).
Trạng thái lúc bàn giao: **chưa thực hiện lượt thử chung, chưa duyệt demo**.

## Chuẩn bị chung

1. Cả ba dùng cùng candidate commit, ghi SHA, ngày giờ Asia/Ho_Chi_Minh và flags thực tế.
   Dùng database/broker riêng, dữ liệu giả lập; không restore đè môi trường đang có giao dịch.
2. Theo [hướng dẫn V2](../../scripts/demo_25_10/MediFlow_Ngoai_Tru_V2_Test_2026-10-26.md)
   để bật đúng overlay/gates. Sáu business apps, Gateway và Eureka phải UP. Chọn buổi còn
   suất, bác sĩ đúng khoa; chạy phần khám trước 17:00. Không chỉnh giờ máy hoặc ngày dữ liệu
   để giả tái khám. Ghi ngày dữ liệu Report, coverage và tình trạng consumer trước thử.
3. Mỗi vai trò dùng profile trình duyệt riêng; không chia sẻ phiên NURSE/PATIENT/DOCTOR.
   Dùng tài khoản synthetic theo demo guide, không đưa password/token vào biên bản.
   Ghi các ID patient/account/session/registration/record/Lab/payment request/receipt/lời hẹn.
4. Chuẩn bị một phiếu giấy giả lập và một patient cũ. Chỉ người vận hành stack chạy runners
   tạo dữ liệu. Full runner tạo giao dịch thật trên rehearsal; không chạy vào môi trường dự án.

## Phân vai

Từ phân công mới 27/10, mọi lỗi backend trong lượt thử do Vinh xử lý, kể cả Core/Gateway/
Pharmacy/Report/Support. Bảng dưới phân vai thử UX, không giao backend lại cho Huy/Hoàng Anh.
Frontend Huy giữ Pharmacy/Report, Hoàng Anh giữ Organization/Patient; Vinh giữ các phần của
mình và Billing/Notification trước đây của Lộc. [Đánh giá dependency hiện hành](17-backend-ownership-and-blocker-audit-2026-10-27.md).

| Người | Vai thao tác chính | Trách nhiệm quan sát |
|---|---|---|
| Hoàng Anh | ADMIN/MANAGER chuẩn bị tổ chức; NURSE tiếp nhận; PATIENT dùng web | Tìm trùng, đúng hồ sơ, scope khoa, cấp account, recovery, phiên và quyền |
| Vinh | CASHIER, DOCTOR, LAB_TECH; kiểm notification | Khoản thu/clearance, lượt khám, Lab, kết luận, lời hẹn, thông báo và correlation khi lỗi |
| Huy | PHARMACIST; MANAGER xem Report | Danh mục/tồn/cấp phát, thu thuốc, số liệu thu/hoàn, coverage và đánh giá cách dùng |

Nếu cần chuyển vai trên một máy, logout trước rồi dùng profile riêng. Reviewer còn lại
đọc kết quả màn hình và ghi lỗi; không chỉ đối chiếu người viết code với chính API của mình.

## Vòng thử bắt buộc

| ID | Thao tác | Kết quả mong đợi | Người ghi kết quả |
|---|---|---|---|
| D01 | NURSE tìm patient theo thông tin phiếu; tạo hồ sơ lần đầu, cấp account một lần | Hồ sơ/account liên kết đúng; không tạo trùng; thông tin đăng nhập được giao riêng | Hoàng Anh |
| D02 | Bệnh nhân chọn chưa dùng web; NURSE chọn buổi và đăng ký khám, CASHIER thu khám | Đi tiếp được đến khám mà không cần patient login/đổi mật khẩu; đúng lịch/khoa/giá/receipt | Hoàng Anh + Vinh |
| D03 | DOCTOR khám, chỉ định Lab; thu Lab; LAB_TECH trả kết quả; bác sĩ kết luận và tạo lời hẹn | Chưa thanh toán/chưa xong chỉ định thì guard đúng; đọc Lab vẫn cùng lượt; chỉ kết quả completed được công bố | Vinh |
| D04 | PATIENT đăng nhập lần đầu khi muốn xem kết quả; **người thử tự nhập và đổi mật khẩu** | Bị giới hạn trước đổi; sau đổi phải đăng nhập lại; credential/phiên cũ bị từ chối; thấy own exam/Lab/thông báo | Hoàng Anh + Vinh |
| D05 | Xem lời hẹn, tải lại kết quả, dùng tài khoản patient khác thử truy cập | Tên bác sĩ dễ hiểu, có khoảng ngày/hướng dẫn; không lộ kết quả người khác; đọc không tạo lượt/thu mới | Hoàng Anh |
| D06 | Người cũ quay lại, tìm đúng hồ sơ; đăng ký NEW_VISIT hoặc FOLLOW_UP đúng nguồn | Không cấp account mới; không bắt đổi mật khẩu chỉ vì tái khám; lời hẹn không tự thành lịch; đổi bác sĩ theo policy/lý do/đồng ý | Hoàng Anh + Vinh |
| D07 | Khi người bệnh thực sự tới ngày hẹn khác, tiếp nhận và khám tiếp | Lượt mới liên kết đúng nguồn/lời hẹn; giữ kết quả/receipt cũ. Ghi ngày thực tế; booking ngày mai hôm nay chỉ đạt bước đặt trước | Vinh |
| D08 | NURSE gặp mất phản hồi sau cấp account; tải lại và đối soát cùng yêu cầu | Thông báo không khẳng định thất bại; trả cùng account/key; không tạo trùng. Dùng fault proxy chỉ trên rehearsal | Hoàng Anh |
| D09 | Màn hình chưa có kết quả; đang chờ; lỗi nguồn rồi thử lại; logout/chuyển patient | Phân biệt rỗng với lỗi; không lộ dữ liệu cũ; retry không thu thêm và không tự gửi lại mutation | Cả ba |
| D10 | Kê đơn, thu/cấp phát, thiếu tồn/hết hạn/clearance không hợp lệ theo ca trong 06 | Không cấp sai thuốc/lượng; không âm tồn; retry không cấp/thu hai lần; đúng người bệnh | Huy + Vinh |
| D11 | Ca EXAM/LAB zero theo dataset hỗ trợ; ca thu dương; hoàn tiền theo policy, xem Report | Zero không bị ghi cash; thu/hoàn/thuốc khớp ledger; coverage chưa đủ phải thể hiện chưa đủ, không đổi thành 0 | Huy + Vinh |
| D12 | Quên mật khẩu: kiểm danh tính theo quy trình nhóm thống nhất trước reset | Đúng patient/account; thu hồi phiên cũ; không cấp account mới hoặc tiết lộ mật khẩu cho người không được xác minh | Hoàng Anh |

Mỗi dòng ghi PASS/FAIL/BLOCKED và evidence vào bảng kết quả bên dưới. Ca còn thiếu dữ liệu,
chưa tới ngày, sai giờ, thiếu coverage hoặc chưa thống nhất xác minh danh tính là BLOCKED,
không là PASS. Nếu sai kỳ vọng nghiệp vụ hoặc có lỗi kỹ thuật, mở lỗi và sửa theo owner;
không chuyển lỗi thành phần UX được miễn. Phải kiểm đủ các biến thể trong 06/SC tương ứng
trước ký WF-11; 12 dòng trên là thứ tự thao tác, không rút gọn số tiêu chí.

## Ghi nhận và kết thúc

Sao chép một dòng cho mỗi ca/biến thể, giữ bản kết quả theo ngày trong `docs/acceptance/`:

| Ca/tiêu chí ONB/WF/SC | Người + role | SHA/flags/thời gian | Thao tác/URL + IDs | Mong đợi | Thực tế + ảnh/correlation | PASS/FAIL/BLOCKED + owner xử lý |
|---|---|---|---|---|---|---|
| Chưa chạy | | | | | | |

Chụp màn hình đủ ngữ cảnh, che password/token và thông tin cá nhân thật. Không đưa ảnh
cấp account chứa mật khẩu vào Git/PR. Lỗi cần bước tái hiện cụ thể, không chỉ ghi “UX khó dùng”.

Vinh đối chiếu evidence với ONB/WF/SC; Huy ký Pharmacy/Report; Hoàng Anh ký Core/Gateway và
tiếp nhận. Chỉ đóng handoff khi cả producer/consumer đủ acceptance. Ba người thống nhất GO
khi không còn FAIL/BLOCKED thuộc demo bắt buộc. Ghi rõ phần ngoài MVP Inpatient/Surgery được
hoãn; source vẫn giữ. Sau thử, dừng đúng stack riêng bằng stopFile và tắt fault proxy.
