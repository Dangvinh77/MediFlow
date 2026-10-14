# Luồng nghiệp vụ ngoại trú

## Luồng đích

Định danh → check-in → thanh toán khám → khám → Lab nếu cần → kê đơn nếu cần →
thanh toán thuốc → cấp thuốc → hoàn tất ngoại trú → báo cáo và thông báo cập nhật bất đồng bộ.

Đích dùng quyền theo purpose/target cho EXAM, LAB_TEST và PRESCRIPTION. Giữ V0 làm compatibility
và rollback theo [contract modes](03-contracts.md); V0 không được coi là đã đạt mọi gate đích.
Không đưa quyết toán nội trú vào luồng này để hoàn thành backend ngoại trú.

## Core

Patient UUID bất biến, identity number không trùng, ngày sinh/contact/bảo hiểm hợp lệ theo DTO.
Account username duy nhất, password BCrypt; account bị khóa không login. SYSTEM chỉ là service
token, không là human account. Staff thuộc khoa hoạt động; bác sĩ có eligibility đúng khoa.
Account/patient mapping có nguồn chính thức; không lấy patientId từ một UUID bất kỳ trong JWT.

Core không tạo medical record, charge, stock, permission khám/thuốc. Room authority đang có
được giữ dữ liệu nhưng không tạo task Surgery resource booking.

## Clinical

Appointment không quá khứ, giữ khung giờ và ràng buộc PENDING/day hiện có. Tạo record từ
appointment cập nhật ARRIVED trong cùng local transaction. Check-in không cho phép bắt đầu khám.

Ở profile V1, check-in/request tạo fact có exact visit ID; Billing issuer tạo EXAM charge.
Chỉ bắt đầu khi exact EXAM clearance hợp lệ hoặc audited emergency override mà code đã hỗ trợ.
Không tự thêm override mới. Bắt đầu exam tạo/reuse một record; completion có diagnosis và
disposition. MVP chỉ disposition ngoại trú; admission/transfer workflows không nghiệm thu đợt này.

Lab/prescription references giữ UUID thật. Clinical nhận result/fill fact idempotently; không
copy entity của Lab/Pharmacy, không suy luận chỉ định từ chuỗi diagnosis.

## Lab

Bác sĩ tạo explicit test request với record/patient/department/source và episode đích có nguồn.
V1 charge lúc lab.request.created, không chờ kết quả. LAB_TEST clearance phải khớp từng testId.
Không bắt đầu/ghi kết quả nếu thiếu quyền; expiry kiểm tại thời điểm hành động.

Kết quả không rỗng, performed date không trước request date; hoàn thành một lần, terminal không
sửa trong MVP. Giữ resultVersion riêng envelope version. Fact kết quả cập nhật Clinical/Report/
Notification. Không tự tạo test từ medicalrecord.created hoặc đoán testId từ invoice.

## Pharmacy

Creation xác minh identity và exact outpatient clinical context. Giá lấy từ server catalogue,
snapshot theo từng dòng, không nhận giá client. Lock drug rows có thứ tự; kiểm quantity/expiry/
available stock; tạo prescription, pending dispense slip và reservations cùng transaction.
Reserve không trừ stock thật.

Billing nhận prescription.created đúng mode, tạo một charge/request/invoice. Ngoại trú V1
cần exact non-expired PRESCRIPTION clearance và authenticated dispense actor; receipt không
tự động cấp thuốc. V0 payment.completed saga là compatibility với SYSTEM actor có kiểu rõ ràng,
không giả staff UUID. M6-10 phải hoàn thiện driving API/authority thực cho V1, không mock provider.

Dispense khóa prescription/slip/reservations/drug rows; kiểm lại quyền và thời gian sau lock.
Tất cả dòng hợp lệ mới trừ stock. Stock/reservation/slip/prescription/outbox commit cùng nhau.
Command/event gửi lặp không xuất kho hai lần. Terminal failure không được thành success muộn.
Cancel/expiry release reservations và phát fact để Billing xử lý financial adjustment đúng mode.

## Billing

Chỉ Billing owns tiền. V1 account theo exact OUTPATIENT_VISIT identity; invoice/request chọn
charge cùng account, không gom mọi phí chưa trả của patient. Source charge có business key
chống trùng; giá của dịch vụ theo catalog/policy đã có, Rx theo server snapshot contract.

Payment có idempotency, persisted state và allocation guards. Transaction/outbox atomic.
Clearance khớp purpose và target, không là patient-wide paid flag. Duplicate callback/event
không tạo thêm payment/charge/grant. Refund/reversal V1 là record mới liên kết payment gốc,
không sửa lịch sử để balance về zero. Không tự tích hợp bank/card provider ngoài.

MVP không cần admission accounts/deposits/insurance settlement/post-final corrections. Dữ liệu
và kernel có sẵn vẫn giữ. Không đổi quy tắc earned final-settlement-only đã chốt: số thu tiền
ngoại trú của MVP được gọi cash received/net cash, không tự công bố earned revenue.

## Thanh toán và cấp thuốc khi có lỗi

- Pharmacy tắt trước khi nhận payment event: queue giữ fact, retry khi service trở lại.
- DB rollback: không persist stock effect/outbox/inbox claim một phần.
- Commit dispense rồi receipt finalize/ACK lỗi: replay đọc committed outcome, không trừ lần hai.
- Expired reservation hoặc stock failure: Pharmacy lưu terminal outcome và failure fact đúng
  transaction boundary; Billing thực hiện compensation một lần bằng policy đúng mode.
- Payment đến sau cancel/expiry: không mở lại prescription, compensation không lặp.
- Refund chưa thành công: trạng thái vẫn pending/failed; notification không được ghi “đã hoàn”.

Compatibility V0 có ledger reversal riêng; không mô tả đó là tiền đã hoàn qua ngân hàng.
Chỉ test/record đã chứng minh completed refund mới được báo refunded ở V1.

## Support

Report nhận facts, chống trùng theo event và operation; tổng hợp lượt khám, Lab, đơn/items xuất
và cash/refund có source rõ ràng. Không suy từ thanh toán sang earned, không đọc producer DB.
Khoảng chưa có source coverage hiển thị unavailable; zero chỉ khi chứng minh khoảng có coverage
và không có operations. Legacy response fields giữ compatibility, không đổi ý nghĩa âm thầm.

Notification tạo in-app intent cho sự kiện ngoại trú đã chọn, người nhận có authoritative
patient/account mapping. Persist history/claim atomic; người dùng chỉ đọc của mình. Không cần
email/SMS hay Surgery reminder để đạt MVP. Report rebuild không phát thêm notification.

## Trạng thái và audit

Giữ state machines của mode được dùng; không free-form status PATCH để bỏ guards. Actor lấy
từ signed credential, không từ request fields untrusted. UTC Instant cho facts, zone cấu hình
cho report date; UUID/money precision không đổi. Có history và correlation, không log PII/token.
