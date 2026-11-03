# Rà UI/UX bác sĩ → Lab → thu ngân — 03/11/2026

## Phạm vi và mức bằng chứng

Rà source hiện tại trên baseline `2e3fa734` cộng thay đổi Lab của lượt trước:
login/sidebar, lịch khám bác sĩ, phiếu khám, hồ sơ, lập chỉ định, danh sách/chi tiết/
thao tác Lab, thu phí khám, hàng thu Lab/Rx, tra cứu hóa đơn và hoàn tiền.
Không sửa frontend Pharmacy/Patient/Organization/Report. Phần phát hiện bên dưới
ghi lại trạng thái trước khi sửa; phần cập nhật cuối tài liệu ghi thay đổi đã làm
sau khi người dùng yêu cầu tiến hành cải tiến. Chưa commit hoặc triển khai.

Graph generation `2026-11-03T03:24:36Z`; đọc trực tiếp source các màn được nêu.
Coverage báo partial ở một dòng test ScheduleManagementWorkspace ngoài luồng
đang đánh giá; không dùng test đó làm bằng chứng. Đây là audit source/UI state,
chưa có browser acceptance mới của bố cục đề xuất. Kết quả chạy liên dịch vụ
trong [audit trước](2026-11-03-lab-flow-audit.md) xác minh nghiệp vụ, không chứng minh
UI đã đủ dùng hay mọi nút đều hợp lý.

## Phát hiện và quyết định đề xuất

### 1. P1 — Nút tải lại có thể làm mất bản nháp kết quả Lab

`LabDetail.tsx:71,98,183–188`: retry đổi requestKey, trả loading và unmount
LabActions, trong khi state kết quả/lý do nằm trong LabActions. Nút tải lại không
nhận busy/dirty từ form. Hồ sơ khám có cơ chế tương tự với các form con.
Đây là kết luận từ state/remount trong source; chưa tái hiện bằng browser ở lượt này.

**Đề xuất:** một thao tác cập nhật dữ liệu ở header, không remount form đang sửa;
khóa refresh khi đang gửi. Bản nháp có thay đổi cần được giữ hoặc xác nhận bỏ.
Sau kết quả chưa rõ, đọc trạng thái để đối soát; không xem refresh đơn thuần là
bằng chứng yêu cầu trước đó thất bại và không tự mở lại quyền gửi lệnh.

### 2. P1 — Luồng hoàn tiền Lab chưa được nối tới UI thu ngân

`billing/api.ts:45` và `RefundDueLookup.tsx:91` chỉ tra cứu prescription-adjustments.
Backend `OutpatientAdjustmentController.java:21` và Gateway đã cho ADMIN/CASHIER
đọc lab-adjustments; packaged test chứng minh paid cancellation phát sinh nghĩa vụ.
UI chưa có lối xử lý nghĩa vụ này. Không được nói hủy Lab đã có đầy đủ quy trình
hoàn tiền trên giao diện chỉ vì backend trả đúng.

**Đề xuất:** một khu vực “Hoàn tiền” cho thu ngân, hỗ trợ loại nguồn Lab/Rx theo
contract hiện có, không thêm nút hoàn tiền lên màn bác sĩ/Lab. Giai đoạn đầu có
thể tra cứu đúng nguồn được cung cấp; danh sách hoàn theo bệnh nhân cần contract
discovery riêng nếu backend chưa có. Không giả lập hàng đợi hoàn tiền bằng việc
lọc các payment request chưa thu. Giữ ghi nhận tiền thực hoàn, xác nhận đối soát,
khóa unknown outcome và backend giới hạn cộng dồn.

### 3. P1 — Hai bước lập chỉ định là hai ranh giới nghiệp vụ lộ ra cho bác sĩ

`LabOrderWorkspace.tsx:44–65`, ClinicalLabOrderForm và CreateSourceLabRequest:
Clinical source và Lab request do hai lần bấm khác nhau; order chỉ ở local state.
Rời/reload sau bước đầu làm mất state tiếp tục của màn này. “Mở hàng đợi” không
khôi phục Clinical source chưa từng được gửi sang Lab.

**Đề xuất:** đích dài hạn một thao tác “Gửi chỉ định xét nghiệm”, bên trong xử lý
hai bước theo thứ tự, giữ source chính xác và phục hồi kết quả chưa rõ. Chỉ bỏ nút
bước hai sau khi có cách tìm lại source/receipt an toàn. Không gộp hai POST một cách
mù quáng hoặc tự tạo Clinical source mới khi bước hai lỗi. Trong phạm vi UI ngắn hạn,
giữ hai bước nhưng không dẫn ra hàng đợi trong lúc chưa hoàn thành gửi.

### 4. P1 — Hàng thu chưa đủ thông tin phân biệt nhiều xét nghiệm cùng bệnh nhân

`PaymentRequestWorkspace.tsx:98–110`: mỗi item hiển thị bệnh nhân, loại Lab/Rx,
khoa, thời điểm và số tiền, chưa có tên xét nghiệm. PaymentRequestDiscoveryDTO
hiện không có labType/service display name. Hai khoản cùng bệnh nhân/khoa/số tiền
khó phân biệt bằng thông tin chuyên môn. Không tự gọi Lab từ Billing feature để nối tên.

**Đề xuất:** bổ sung service label từ Billing read model với nguồn dữ liệu có thẩm
quyền nếu cần; trước đó chỉ hiển thị các trường thật được trả về và không bịa tên.
Xem đây là thiếu contract hiển thị, không giải quyết bằng thêm một nút “Xem”.

### 5. P2 — “Mở hàng đợi xét nghiệm” làm bác sĩ mất ngữ cảnh

`CreateSourceLabRequest.tsx:27` luôn dẫn `/lab`, kể cả sau tạo thành công hoặc chưa
gửi xong. Màn đã có “Mở xét nghiệm” và route có “Quay lại hồ sơ khám”.

**Đề xuất:** bỏ liên kết hàng đợi toàn cục tại màn lập chỉ định. Sau tạo thành công,
hành động chính là “Về hồ sơ khám”, liên kết phụ “Xem phiếu xét nghiệm” tới đúng đơn.
Trong tình huống chưa rõ kết quả, chỉ cung cấp tra cứu có thể đối soát đúng source;
không coi `/lab` là công cụ tra cứu source khi API chưa hỗ trợ sourceOrderId filter.

### 6. P2 — Quay lại từ Lab mất bộ lọc và lượt khám

LabTable mở chi tiết bằng `/lab/{id}`; LabDetail luôn quay về `/lab`.
Filter/page không nằm trong URL. Đường “Xem xét nghiệm của lượt khám” từ hồ sơ có
episodeId nhưng khi quay lại từ detail sẽ mất. Nút “Tất cả” reset cả episode scope.

**Đề xuất:** lưu filter/page trong URL; giữ nguồn quay lại an toàn trong nội bộ app.
Khi xem theo lượt khám, lượt là ngữ cảnh cố định; xóa bộ lọc chỉ xóa status/khoa,
không vô tình mở rộng ra toàn bộ người bệnh. Điều hướng về hồ sơ đúng record nếu
đã vào từ hồ sơ. Không dùng router.back() mù quáng cho hành động nghiệp vụ.

### 7. P2 — Lab chưa có cách vào làm việc theo vai trò

`LabTable.tsx:77–88`: mặc định mọi trạng thái cho cả bác sĩ và LAB_TECH.
Đã hủy/hoàn tất/chờ thu trộn với danh sách thực sự có thể xử lý.

**Đề xuất:** kỹ thuật viên mặc định “Sẵn sàng thực hiện” (READY), có các bộ lọc
“Đang thực hiện”, “Chờ thanh toán”, “Đã hoàn tất”, “Đã hủy”. Đây là view/filter,
không biến mỗi trạng thái thành một nút lệnh. Bác sĩ ưu tiên xem xét nghiệm của
lượt đang khám, giữ trang tìm kiếm chung cho nhu cầu tra cứu.
Không lấy một trang dữ liệu rồi lọc client để giả tổng hàng đợi.

### 8. P2 — Bộ lọc Lab lộ dữ liệu kỹ thuật và cả nghiệp vụ deferred

`LabTable.tsx:183,214–236` có “Mã khoa”, UUID đợt chăm sóc, “Nội trú” ngay trong
form chính. Màn không có tìm bệnh nhân trực tiếp; controller search hiện cũng
không có patientId/date parameters (doctor patient endpoint là API khác, không
được thay vào cho LAB_TECH vì quyền khác).

**Đề xuất:** khu vực chính chỉ trạng thái/khoa và ngữ cảnh lượt đang xem; thông tin
UUID nằm ở hỗ trợ nâng cao. Không đưa Nội trú vào luồng MVP ngoại trú. Tìm bệnh nhân/
ngày cho kỹ thuật viên là gap backend search cần triển khai thật trước khi vẽ ô tìm.

### 9. P2 — Hủy đang đứng cùng cấp với thao tác thực hiện

`LabActions.tsx`: Hủy xét nghiệm đặt cạnh Bắt đầu/Nhập kết quả. Còn nhiều hướng dẫn
thu ngân trong màn kỹ thuật viên và các mục tham chiếu/bàn giao lặp UUID.

**Đề xuất:** READY chỉ có primary “Bắt đầu xét nghiệm”; IN_PROGRESS primary “Nhập
kết quả”; COMPLETED ưu tiên bảng kết quả. “Hủy xét nghiệm” là secondary ở mục
“Thao tác khác”, chỉ xuất hiện khi backend cho phép, mở form lý do/xác nhận rõ.
Không giấu tên hành động hủy sau icon vô nghĩa. Gộp một khu hỗ trợ kỹ thuật; bàn giao
thu ngân không phải khối thường trực của kỹ thuật viên.

### 10. P2 — Thu ngân có nhiều cửa vào và nút trùng mục đích

Sidebar có Viện phí, Thu phí Lab/Rx, Thu phí khám; trang /billing lại có card dẫn
tới hai màn; InvoiceLookup lại thêm “Thanh toán khoản phí đã chọn”. Login CASHIER
đến scheduled-payments khi scheduling bật, dễ tưởng đó là mọi khoản thu.
“Lập hóa đơn” compatibility là nút primary ngay cạnh lối tới luồng ledger hiện hành.

**Đề xuất:** một entry “Thu ngân” và navigation nội bộ ổn định: “Phí khám”,
“Xét nghiệm & thuốc”, “Hoàn tiền”, “Lịch sử hóa đơn”. Tab khác không có nghĩa không
có khoản cần thu. Chỉ mount workspace đang chọn, giữ lock không đổi tab/người bệnh
khi giao dịch chưa rõ. Compatibility invoice creation ở khu phù hợp/thu gọn,
không xóa API hoặc chức năng lưu trữ lịch sử. Card dẫn đường thêm ở lượt trước chỉ
là cách khắc phục tìm đường ngắn hạn, chưa phải cấu trúc navigation cuối cùng.

### 11. P2 — Nút đọc lại dữ liệu thu ngân bị phân tán

`PaymentRequestWorkspace.tsx:73`, `PaymentRequestLookupForm.tsx:343,371`:
“Cập nhật hàng chờ”, “Đọc lại khoản đang đối chiếu”, “Kiểm tra báo giá hiện hành”.
Nút “Chỉ khoản cùng lượt đã chọn” còn hiện sau khi đã áp dụng đúng lượt; “Chọn khoản”
vẫn là nút trên item đã chọn dù handler trả về ngay. ScheduledPaymentWorkspace mở
manual lookup mặc định (`manualLookupOpen=true`).

**Đề xuất:** một refresh phù hợp phạm vi đang xem, tự đọc lại request + quote khi
chọn khoản. Refresh quote riêng chỉ trong lỗi/expiry/recovery có ý nghĩa; không gọi
renew khi thực tế API chỉ đọc. Item đã chọn thể hiện “Đang đối chiếu”, không giữ
nút no-op; chỉ hiện giới hạn cùng lượt khi nó thật sự thay đổi bộ lọc. Manual lookup
đóng mặc định. Giữ các nút retry cùng giao dịch/kiểm tra pending khi thật sự cần.

### 12. P2 — Link Thu phí khám không theo feature gate

PaymentRequestWorkspace luôn hiện `/billing/scheduled-payments`, trong khi sidebar
và card mới trên /billing có kiểm tra scheduling flag.

**Đề xuất:** truyền schedulingEnabled từ server composition; không có nút đi đến
chức năng chưa được mở. Không bật flag hoặc suy ra quyền từ nút.

### 13. P2 — Hồ sơ khám phân tán thao tác và có form mở quá sớm

MedicalRecordDetail: sửa triệu chứng/kê đơn ở header, chỉ định Lab bên dưới chẩn đoán,
xem Lab ở cuối; AddDiagnosisForm và CompleteRecordForm cùng mở toàn bộ. ExaminationRecordLink
đã tìm đúng một record vẫn hiện “Tìm lại hồ sơ”.

**Đề xuất:** khu nội dung chính theo “Khám & chẩn đoán / Xét nghiệm / Đơn thuốc”.
Chỉ định và danh sách Lab cùng lượt nằm cùng khu; giữ link tới feature Pharmacy
hiện có, không sửa feature owner khác. “Hoàn tất khám” mở phần chốt xử trí khi
người dùng chọn; điều kiện diagnosis/backend vẫn giữ. Bỏ “Tìm lại hồ sơ” khi đã
xác định được duy nhất record; retry chỉ khi lỗi/không tìm được. Một nút thêm chẩn
đoán mở form thay cho form chiếm màn hình thường trực.

### 14. P2 — Các form thanh toán còn đưa trường bất biến vào dạng nhập liệu

LedgerPaymentForm: mã tiền readOnly vẫn là input “ISO 4217”, reference hiện cả khi
chọn CASH. Có nhiều khối nhắc số dư/khoản phí giống nhau.

**Đề xuất:** hiển thị tiền tệ cạnh số tiền, chỉ hiện mã đối chiếu khi hình thức cần.
Giữ khả năng thu một phần vì contract hỗ trợ; không tự bỏ quyền này để giảm một ô.
Biên nhận gọn, mã kỹ thuật ở mục đối soát; không bỏ bằng chứng giao dịch.

### 15. P3 — Disclosure kỹ thuật lồng nhau và retry trên từng nhãn

ReferenceText technical=true sinh nút “Thông tin kỹ thuật” riêng cho từng UUID,
kể cả bên trong details hỗ trợ đã thu gọn. Khi nhiều reference lỗi, mỗi nhãn sinh
“Tải lại tên”. Chỉ giảm nút ở màn cha chưa đủ để tránh rải control trên UI.

**Đề xuất:** ở khu hỗ trợ đã thu gọn thì hiện trực tiếp các mã có nhãn; khu nghiệp
vụ hiển thị tên và trạng thái thiếu tên. Retry tập trung theo phần dữ liệu thay vì
từng dòng; thay shared component cần kiểm tra tác động các consumer, không đổi
đồng loạt mọi feature trong một lần dọn Lab.

## Ba hướng xử lý

1. Chỉ xóa link/nút trùng: ít thay đổi, nhưng vẫn hai bước dễ bỏ dở, khó đi lại theo
   lượt khám và thiếu hoàn tiền Lab trên UI.
2. **Khuyến nghị — tinh gọn theo công việc, giữ contract hiện hành:** sửa các rủi ro
   refresh, thống nhất navigation/ngữ cảnh, state-based actions và nối tra cứu hoàn
   Lab. Tách backend gaps (search, service labels, source recovery, requote) thành
   phần có contract/acceptance riêng. Không cần thay toàn bộ giao diện hoặc API.
3. Thiết kế lại toàn bộ workspace + contract một lần: trải nghiệm thống nhất hơn,
   phạm vi lớn và dễ ảnh hưởng payment recovery; không cần cho đợt tinh gọn đầu.

## Tiêu chí chấp nhận hướng khuyến nghị

- Mỗi khu công việc có một primary action phù hợp trạng thái; navigation dùng
  link/tab, không trình bày thành dãy nút lệnh. Secondary chỉ khi có mục đích thật.
- Không nút no-op, không link sang feature đang tắt, không bắt nhập/copy UUID ở
  đường đi thường ngày. Chức năng chưa có contract không dựng control giả.
- Bác sĩ lập/xem/hủy Lab rồi quay đúng lượt; kỹ thuật viên quay đúng danh sách và
  filter/page; tên người bệnh/ngữ cảnh luôn hiện trước command.
- Refresh không mất bản nháp, không ngắt command đang gửi, không xóa bằng chứng
  unknown outcome. Cảnh báo trước khi bỏ dữ liệu đang sửa.
- CASHIER thấy rõ loại hàng thu đang xem và có đường xử lý Lab refund thực;
  không cho thu/hoàn trùng khi chuyển tab, retry hoặc mất mạng.
- Test hành vi theo role × state, filter/context retention, dirty/busy refresh,
  paid/unpaid cancellation và payment uncertainty; typecheck/lint/build; browser
  desktop + màn hình hẹp + bàn phím với API thật hoặc fixtures được nêu rõ.

## Cập nhật triển khai sau khi được yêu cầu cải tiến

Đã tinh gọn bố cục bác sĩ, Lab và thu ngân trên theme hiện có. Tên bệnh nhân,
ngày sinh, khoa và trạng thái đứng trước thao tác; dùng typography và đường phân
cách thay cho nhiều khối trang trí. Header gom hồ sơ cá nhân/giao diện/đăng xuất
vào menu Tài khoản, đóng bằng Escape và trả focus về tiêu đề. Sidebar đóng trên
mobile không còn để các link ngoài màn hình lọt vào thứ tự bàn phím. Thu ngân
chỉ còn một mục ở sidebar; các khu thu/hóa đơn/hoàn tiền dùng điều hướng nội bộ.

- **Dùng chung:** ReferenceText/ReferenceInput không dùng UUID, kể cả UUID nằm
  trong chuỗi dài, làm tên hiển thị. Giữ nguyên UUID trong request và khả năng
  tra cứu chính xác; mã chỉ hiện khi chủ động mở phần hỗ trợ. Không cắt mã rồi
  giả làm mã nghiệp vụ. Mã lỗi/correlation trong AsyncState và CommandFeedback
  cũng thu gọn; retry vẫn đứng trước hỗ trợ trong thứ tự Tab. Chưa thay custom
  resolver riêng của các feature khác.
- **Bác sĩ:** nhóm chỉ định/xem Lab theo đúng lượt khám; thêm chẩn đoán và hoàn
  tất khám mở khi cần. Bỏ Tìm lại hồ sơ sau khi đã tìm được. Giữ bản nháp khi đọc
  lại; khóa đọc/ghi đồng thời, không để một phản hồi đọc cũ ghi đè kết quả lệnh
  vừa hoàn tất. Nếu trạng thái mới xung đột với bản nháp, yêu cầu chủ động xem dữ
  liệu mới. Correlation ID nằm trong disclosure hỗ trợ.
- **Lab:** kỹ thuật viên vào danh sách READY mặc định; filter/page/lượt khám
  lưu trong URL và quay lại đúng danh sách. Reset giữ ngữ cảnh lượt, bao gồm
  episodeType đã có trong URL. Thu gọn tra cứu UUID và thao tác hủy; chỉ hiện
  hành động chính theo trạng thái. Refresh không unmount bản nháp kết quả, không
  mở lại quyền gửi sau unknown outcome. Sau tạo quay đúng hồ sơ/phiếu; bỏ lối
  hàng đợi toàn cục không khôi phục được source.
- **Thu ngân:** một dải điều hướng có xét feature gate; đăng nhập không bật
  scheduling đi thẳng hàng Lab/Rx. Item đã chọn hiện trạng thái thay cho nút
  no-op; chỉ hiện lọc cùng lượt nếu thật sự thay đổi filter. Tra cứu mã thủ công
  thu gọn; tiền tệ trình bày như thông tin, mã chuyển khoản chỉ khi cần. Nối tra
  cứu Lab refund vào endpoint thật đang có, giữ exact source và khóa giao dịch.

### Giới hạn còn lại

Các phát hiện 3, 4 và phần tìm bệnh nhân/ngày của 8 cần contract backend riêng:
phục hồi Clinical source sau reload, service label trong Billing discovery,
và search thực cho LAB_TECH. Hiện vẫn giữ hai bước lập chỉ định/gửi Lab và chặn
tạo lại khi chưa rõ kết quả. Hoàn tiền Lab tra cứu theo đúng source được bàn
giao; chưa có hàng hoàn theo tên bệnh nhân. Không thêm ô tìm hoặc nút để giả
lập các khả năng này.

Rà UUID ngoài phạm vi sửa: AdmissionTable của Inpatient còn cột UUID nhưng là
feature deferred, không được kích hoạt bởi đợt này. ReportDepartmentName có
custom resolver riêng chưa dùng guard UUID dùng chung; mã tham chiếu đã nằm
trong disclosure, cần owner Report áp guard tương đương nếu nhãn API trả UUID.
NotificationDetail và follow-up có mục kỹ thuật chủ động mở; UUID trong URL,
request và mục hỗ trợ là chủ ý, không phải nhãn nghiệp vụ.

Bằng chứng browser dùng API fixture có ghi rõ dữ liệu thử, tách khỏi acceptance
backend thật: [ảnh và ghi nhận UI](../acceptance/lab-flow-2026-11-03/ui/README.md).
Kết quả kiểm thử cuối được ghi tại đó và trong kế hoạch triển khai.
