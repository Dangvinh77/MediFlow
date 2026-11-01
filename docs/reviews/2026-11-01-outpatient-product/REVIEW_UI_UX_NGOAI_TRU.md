# MediFlow — Review sản phẩm ngoại trú từ góc nhìn bệnh viện nhỏ tại Việt Nam

Thực hiện tối 01/11 đến rạng sáng 02/11/2026, giờ Việt Nam. Bản được review: `f3850620` trên nền master `7e6a21d7`. Đây là đánh giá sản phẩm và đề xuất cải thiện; không phải xác nhận đủ điều kiện triển khai bệnh viện, chứng nhận an toàn lâm sàng hoặc biên bản đã nghiệm thu tất cả nhánh nghiệp vụ.

## 1. Phán quyết của khách hàng

**Nếu là người đại diện bệnh viện, tôi đồng ý tiếp tục đầu tư để thử nghiệm có giám sát, nhưng chưa đồng ý dùng MediFlow làm hệ thống vận hành ngoại trú chính.**

Lý do không nằm ở việc thiếu gradient, icon hay dashboard nhiều biểu đồ. Giao diện hiện có khá sạch, có bộ khung nhất quán, nhiều ràng buộc tài chính được thực hiện nghiêm túc. Tuy nhiên, trải nghiệm vẫn thường yêu cầu nhân viên tự tìm phân hệ, tự nhớ bước, tự bàn giao mã và tự tải lại trạng thái. Nhân viên đang phải giữ một phần quy trình trong đầu thay cho phần mềm.

Một hệ thống dùng hàng ngày cần trả lời rất nhanh:

1. Tôi đang phục vụ ai, đúng lượt khám nào?
2. Người này đang chờ ai, chờ việc gì và bao lâu?
3. Tôi được làm gì tiếp theo, điều kiện nào còn thiếu?
4. Khi bàn giao, người nhận có thấy công việc và xác nhận đã nhận chưa?
5. Nếu lỗi mạng hoặc dịch vụ chậm, việc vừa làm có thành công không, có được làm lại không?
6. Người phụ trách khoa cần can thiệp ở đâu để buổi khám không tắc?

MediFlow đã có một phần câu trả lời cho 1, 3 và 5. Câu trả lời cho 2, 4 và 6 còn yếu hoặc phân tán. Đây là khoảng cách lớn nhất giữa **một hệ thống có nhiều chức năng** và **một sản phẩm giúp bệnh viện làm việc hiệu quả**.

Không nên phủ nhận công sức backend: chống lặp lệnh, phiên bản cập nhật, xác nhận tài chính, liên kết lượt tái khám và tách chức vụ trưởng khoa khỏi chuyên môn bác sĩ đều là nền tảng có giá trị. Việc cần làm là đưa giá trị đó tới người dùng bằng ngôn ngữ công việc, không phơi bày cấu trúc kỹ thuật.

## 2. Bằng chứng, phương pháp và giới hạn

### 2.1. Đã thực hiện trực tiếp

- Khởi động bằng hai file bat hiện có, dữ liệu demo cách ly `snapshot-20261101-231524`, Gateway 28080, frontend 3000 ở chế độ phát triển.
- Quan sát screenshot và thao tác trên trình duyệt IAB, cửa sổ thông thường khoảng 1280 × 720; không mở toàn màn hình.
- Đăng nhập lần lượt `demo.nurse`, `demo.cashier`, `demo.doctor`, `demo.lab`, `demo.pharmacist`, `demo.patient`, `demo.manager`, `demo.admin`.
- Nurse tìm khoa, chọn buổi 02/11/2026, chọn Nguyễn Văn An Demo, đăng ký trước một lượt khám mới.
- Cashier thấy phiếu đó trong hàng chờ, đọc báo giá và ghi nhận thu mẫu 50.000 VND.
- Doctor thấy cùng phiếu đã xác nhận, số thứ tự 1, chưa đến viện trong lịch 02/11.
- Patient thấy đúng phiếu, khoa, bác sĩ, phòng, số thứ tự và thông báo chờ thu/đã xác nhận.
- Mở hồ sơ khám, form chỉ định, form kê đơn, xét nghiệm đang thực hiện và form nhập kết quả từ dữ liệu có sẵn; mở đơn thuốc và bước xác nhận xuất thuốc có sẵn.
- Manager chọn khoa chưa được giao và bị từ chối; xem trang báo cáo cũ phạm vi toàn bệnh viện.
- Admin xem nhân sự khoa, xem trước bổ nhiệm, xem danh sách/tạo tài khoản, hồ sơ manager và bộ sửa phạm vi quản lý. Không xác nhận bổ nhiệm hoặc thay đổi quyền trong phiên review này.
- Admin yêu cầu báo cáo ngoại trú ngày hiện tại và quan sát trạng thái chưa sẵn sàng.

Phiếu mới trong phiên: `78e3a5cb-5619-48c6-b60b-30958f51de38`; bệnh nhân tổng hợp Nguyễn Văn An Demo. Mã này chỉ truy vết bằng chứng của phiên đã dọn, không phải dữ liệu cố định cho lần demo tiếp theo.

### 2.2. Cách đọc các nhận định

| Ký hiệu | Ý nghĩa |
|---|---|
| **UI** | Quan sát trực tiếp trong đợt review này, có ảnh đi kèm |
| **SRC** | Đối chiếu source hiện tại, không đồng nghĩa đã chạy hết nhánh trên UI |
| **OLD** | Bằng chứng nghiệm thu trước đó, không tính thành thao tác mới |
| **ĐX** | Đề xuất sản phẩm; chưa phải chức năng đang có hoặc yêu cầu đã được duyệt |
| **CXT** | Cần xác minh thêm trước khi kết luận |

Các vấn đề bố cục là đánh giá chuyên gia từ ảnh. Các khoảng thiếu về nghiệp vụ là đánh giá theo bối cảnh ngoại trú giả định ở mục 3, không thay cho khảo sát tại cơ sở thật.

### 2.3. Chưa được phép tính là đã nghiệm thu

- Trọn hành trình khám mới/tái khám qua đủ quầy trên cùng lượt, vì phiên này ngoài 07:00–17:00. Giữ giờ thực theo quyết định của người dùng; không sửa đồng hồ hoặc nới guard.
- Lượt nguồn tái khám V2: seed báo PARTIAL ngoài giờ; không dùng hồ sơ lịch sử khác để giả vờ đã chạy nhánh tái khám hiện tại.
- Bệnh nhân mới tự đổi mật khẩu; xem kết quả hoàn tất của chính lượt vừa tạo. Tài khoản patient dùng trong phiên này chưa có kết quả hoàn tất.
- Xác nhận xuất thuốc, hủy/hoàn tiền, đổi đơn, xuất thuốc một phần trong phiên này. Chỉ mở giao diện và đọc điều kiện.
- Lưới phân ca và chuyển workspace trưởng khoa: đối chiếu source hiện tại cùng [biên bản trước](../../acceptance/weekly-roster-2026-11-01/README.md), không cấp quyền mới chỉ để chụp ảnh.
- Mobile thực, tablet, máy in, máy quét, bàn phím thuần, screen reader, độ tương phản từng trạng thái, kiểm thử tải và nhiều nhân viên làm đồng thời. Thử viewport hẹp không tạo được bằng chứng ổn định nên không dùng để kết luận responsive đạt hoặc lỗi.
- Không đo được p50/p95, Core Web Vitals hoặc thời gian tiếp nhận chuẩn bằng phiên duy nhất này. Thời gian chờ công cụ điều khiển trình duyệt và biên dịch Next dev không được gán thành độ trễ sản phẩm.

**Không có lỗi kết nối Docker/trình duyệt ban đầu nào được dùng làm bằng chứng hệ thống chạy thật không ổn định.** Ảnh 01 là lúc backend chưa sẵn sàng. Một số link cần đợi điều hướng; lỗi công cụ đã được phục hồi, không tự động ghi thành lỗi ứng dụng.

## 3. Tiêu chuẩn đánh giá cho một cơ sở nhỏ

Để đánh giá cụ thể, giả định cơ sở có nhiều chuyên khoa ngoại trú, một số phòng khám, một quầy tiếp nhận, một quầy thu ngân, bộ phận xét nghiệm và cấp thuốc. Nhân viên có thể kiêm nhiệm, nhưng hành động phải truy được về đúng người và đúng quyền. Không mặc định cơ sở có bệnh án điện tử được công nhận, kết nối BHYT, máy xét nghiệm tự động hoặc thanh toán ngân hàng thật.

Giá trị khách hàng cần mua là: **ít chờ hơn, ít nhập lại hơn, ít nhầm bệnh nhân hơn, biết việc nào đang tắc và đối soát được tiền cùng dịch vụ đã cung cấp.** Microservices chỉ có ý nghĩa khi phục vụ những giá trị đó.

### Thang nhận định hiện tại

Điểm dưới đây là nhận định định hướng, không phải kết quả khảo sát người dùng hay phép đo chất lượng backend. 1 = gây khó khăn rõ; 3 = dùng được khi được hướng dẫn; 5 = tự giải thích, nhanh và xử lý tốt tình huống thường gặp.

| Góc độ | Điểm /5 | Nhận định |
|---|---:|---|
| Sạch và nhất quán về hình thức | 3 | Bố cục sáng, hệ màu và control khá đồng nhất |
| Đúng trọng tâm công việc mỗi vai trò | 2 | Phần lớn trang bắt đầu từ tra cứu/form thay vì hàng việc cần xử lý |
| Liên tục giữa các quầy | 2 | Thu khám có hàng chờ tốt; Lab/Rx còn nặng bàn giao mã |
| Hỗ trợ quyết định lâm sàng trên UI | 2 | Có chẩn đoán/chỉ định/kê đơn nhưng hồ sơ còn mỏng, thiếu ngữ cảnh ngay điểm thao tác |
| Điều hành khoa | 2 | Đã có lịch và ca, chưa rõ thiếu người/quá tải/chờ kết quả |
| Tự phục vụ của bệnh nhân | 2 | Đúng quyền cá nhân, nhưng nhiều trang trống và phải tự nối thông tin |
| Minh bạch tài chính cho người dùng | 3 | Báo giá khá rõ; báo cáo, biên nhận và luồng sau thanh toán còn rời |
| Sẵn sàng vận hành tại cơ sở thật | Chưa chấm | Còn thiếu nghiệm thu xuyên suốt, thiết bị, tải, vận hành và chính sách thực tế |

## 4. Mười vấn đề ưu tiên cao nhất

P1 = nên giải quyết trước khi thử vận hành có giám sát; P2 = giảm hiệu quả và tính chuyên nghiệp; P3 = hoàn thiện sau. Các mức này dùng để lập backlog, không phải kết luận đã xảy ra tổn hại ngoài đời.

| ID | Mức | Phát hiện | Căn cứ | Tác động và kết quả cần đạt |
|---|---|---|---|---|
| MF-01 | P1 | Manager không có scope lịch vẫn xem được báo cáo cũ toàn viện | UI 33, 42, 46; SRC ReportController/ReportApplicationService | Không nhất quán với quyết định chỉ quản lý khoa được giao; cần một chính sách quyền/phạm vi thống nhất trên API báo cáo |
| MF-02 | P1 | Đăng ký thành công nhưng danh sách nurse vẫn ghi chưa có phiếu | UI 06 | Dễ tưởng chưa đăng ký rồi làm lại; cập nhật/đọc lại hàng phiếu và số chỗ ngay sau lưu |
| MF-03 | P1 | Thu Lab/Rx dựa vào nhiều mã nguồn và đợt điều trị | UI 13–14 | Dễ nhầm lượt, phải nhờ dev chỉ mã; có hàng chờ phí theo bệnh nhân/lượt, hệ thống giữ các ID |
| MF-04 | P1 | Bác sĩ phải nhập tự do loại xét nghiệm và mã giá, tự hoàn thành hai bước gửi | UI 19; SRC LabOrderWorkspace | Có thể rời giữa hai bước; chọn dịch vụ từ danh mục và theo dõi việc gửi bền vững |
| MF-05 | P1 | Điểm nhập kết quả/xác nhận xuất thuốc thiếu nhận diện bệnh nhân tại chỗ | UI 23, 27 | Tăng gánh nhớ và nguy cơ thao tác nhầm; giữ hai định danh, lượt và nội dung cần xác nhận ngay vùng thao tác |
| MF-06 | P1 | Mẫu BHYT được ép theo quy ước demo không phù hợp cách trình bày mã thực tế được đối chiếu | UI 09; SRC PatientForm; nguồn BHXH ở mục 18 | Phải sửa validation, dữ liệu và nội dung hướng dẫn đồng bộ; không chỉ sửa placeholder |
| MF-07 | P1 | Hồ sơ khám hiện chủ yếu triệu chứng, chẩn đoán và hướng xử trí | UI 18, 20; SRC MedicalRecordDTO | Chưa đủ thông tin phục vụ bàn khám thực tế; khảo sát và bổ sung tiền sử, dị ứng, sinh hiệu, thuốc đang dùng theo cấu trúc có chủ sở hữu |
| MF-08 | P1 | LAB_TECH nhìn bộ lọc khoa không sử dụng được; dược sĩ thiếu tên bác sĩ/khoa | UI 21, 26; SRC reference-directory/PharmacyIdentity | Cần read API tối thiểu theo công việc, không mở toàn quyền hồ sơ nhân sự/bệnh án |
| MF-09 | P1 | Báo cáo mặc định cho hôm nay trong khi báo cáo chốt cần kỳ đã kết thúc | UI 35, 43 | Người quản lý gặp ngõ cụt ngay lần đầu; tách điều hành đang diễn ra và báo cáo đã chốt |
| MF-10 | P2 | Trạng thái dịch vụ chưa gắn thành hành trình và người chịu trách nhiệm | UI 07, 17, 28, 31 | Biết “đã xác nhận” nhưng chưa thấy toàn bộ ai đang đợi ai; cần lịch sử bàn giao và bước tiếp theo theo quyền |

Không kết luận MF-01 là rò rỉ bệnh án: bằng chứng hiện tại là API/UI báo cáo tổng hợp ngoài scope đã chốt. Không kết luận MF-07 là không tồn tại ở mọi nơi trong repo: phạm vi xác minh là các màn hình, DTO và luồng ngoại trú được review.

## 5. Review gam màu, bố cục và nhận diện

### 5.1. Những gì nên giữ

- Nền sáng, chữ tối, xanh dương cho hành động chính phù hợp một công cụ công việc. Không cần đổi sang một phong cách “y tế” nhiều hình minh họa để có vẻ nghiêm túc.
- Có nhãn trạng thái bằng chữ, có focus, nhiều nút/ô nhập cao khoảng 44 px trong component chung. Đây là nền tảng tốt, chưa đủ để tự nhận đạt WCAG.
- Sidebar có nhóm nghiệp vụ, trạng thái trang đang chọn rõ, form có nhãn và dấu bắt buộc; một số trang có breadcrumb.
- Các ảnh 09, 24, 36 cho thấy khung tổ chức/patient/pharmacy có thể phát triển tiếp, không cần viết lại toàn bộ giao diện.

### 5.2. Vì sao vẫn tạo cảm giác tầm thường

**Quá nhiều khối có cùng độ quan trọng.** Header lớn, mô tả, breadcrumb, tiêu đề section, mô tả section, card bên trong card, rồi mới đến nội dung công việc. Ảnh 09 cho thấy gần hết viewport dành cho tiêu đề và bốn trường đầu. Ảnh 18 dành nhiều chiều cao cho nội dung rất ít. Vấn đề là phân bổ không gian, không phải thiếu trang trí.

**Cùng một bố cục cho những nghề khác nhau.** Dược sĩ vào danh mục, bác sĩ vào bộ lọc lịch, manager vào form lập buổi, bệnh nhân vào trang phiếu: các nhu cầu khác nhau nhưng đều giống màn hình quản trị bảng dữ liệu. Bàn làm việc của dược sĩ cần hàng đơn chờ; bác sĩ cần hàng bệnh nhân và hồ sơ; manager cần công suất và ngoại lệ; patient cần một hướng dẫn tiếp theo dễ hiểu.

**Sidebar lấn át chức năng chính.** Mỗi mục có nhãn và mô tả, nhiều nhóm viết hoa, chiều cao lớn. Ở ảnh 03 và 18, ngay desktop bình thường đã cần cuộn sidebar; trên vùng nhìn nhỏ, chi phí tìm menu tăng. Admin mang gần như toàn bộ menu nghiệp vụ, dễ tạo cảm giác admin là người được làm mọi công việc chuyên môn.

**Nút cần làm tiếp thường chưa nổi bật.** “Tiếp tục đăng ký khám” là link nhỏ trong khối tài khoản. “Chỉ định xét nghiệm” nằm thấp trong hồ sơ, còn “Kê đơn” ở phía trên. Sau thanh toán không có hướng dẫn thật rõ “Đến phòng nào/đã bàn giao cho ai”. Cần phân cấp theo bước hiện tại, không phân cấp theo thứ tự component được thêm vào source.

**Bảng không bảo vệ độ rộng cho nội dung quan trọng.** Ảnh 21 có nhãn trạng thái bị xuống nhiều dòng trong cột nhỏ trong khi tên bệnh nhân/kết luận kéo dài. Các dòng cũ/hủy/hoàn tất lẫn với việc đang chờ. Đây là bảng lịch sử dùng như hàng đợi, không phải hàng đợi được thiết kế cho công việc.

### 5.3. Hướng thị giác đề xuất

Giữ xanh dương hiện có làm màu hành động, giảm việc dùng cùng màu cho mọi thông tin. Bảng sau là quy ước thiết kế đề xuất, cần đo tương phản trên trạng thái thật trước khi phát hành.

| Thành phần | Hướng sử dụng |
|---|---|
| Nền toàn app | Xám rất nhạt; vùng công việc chính trắng |
| Xanh dương | Một hành động chính trong từng vùng tác vụ, link điều hướng có ngữ cảnh |
| Xanh lá | Đã hoàn tất/xác nhận thành công, không dùng cho mọi mục đang hoạt động |
| Hổ phách | Đang chờ, gần hết hạn, cần xác minh; có thời điểm/lý do kèm theo |
| Đỏ | Lỗi chặn, nguy cơ cao, thao tác hủy; tránh tô đỏ toàn bộ trạng thái lịch sử đã hủy |
| Xám | Đã lưu trữ, chưa áp dụng, dữ liệu bổ sung |
| Kiểu chữ | Nội dung thao tác thường 14–16 px; chữ 11–12 px chỉ cho phụ chú không thiết yếu |
| Số tiền/số thứ tự | Căn thẳng hàng, dùng tabular numerals; có đơn vị tiền rõ |

Không phân biệt role chỉ bằng đổi màu cả dashboard. Vai trò cần khác **nội dung mặc định, thứ tự công việc và hành động được phép**. Màu chỉ bổ trợ.

Chọn một kiểu bảng công việc gọn: hàng khoảng 48–64 px khi dữ liệu ngắn; tên bệnh nhân và ngày sinh không bị đẩy dưới các metadata; cột trạng thái có độ rộng tối thiểu; ghi chú dài xem mở rộng. Cho phép mật độ thoải mái/gọn nếu khảo sát cho thấy cần, không ép một bảng chật cho tất cả.

### 5.4. Ngôn ngữ sản phẩm

| Đang thấy | Nên hướng tới |
|---|---|
| “Billing đã xác nhận khoản thu hoặc nghĩa vụ…” | “Đã ghi nhận thu 50.000 ₫. Phiếu khám đã được xác nhận…” nếu cả hai trạng thái đã được đọc lại |
| “Chờ quyền tài chính” | “Chờ thanh toán” hoặc “Chờ xác nhận đủ điều kiện” đúng với nguồn dữ liệu |
| “khóa idempotency” | “Yêu cầu đang được kiểm tra; không tạo yêu cầu mới” |
| “Phiên bản contract/kết quả”, `OUTPATIENT_VISIT` | Giấu phần kỹ thuật trong mục hỗ trợ; hiển thị “Ngoại trú” |
| “Inactive tài khoản” | “Khóa tài khoản”, giải thích tác động và lý do |
| “Mã bệnh nhân” nhưng ô tìm tên được | “Tìm bệnh nhân theo tên/CCCD”, mã nội bộ ở phần chi tiết |
| `Manager is not assigned to this department` | “Bạn chưa được giao quản lý khoa này”; đưa về khoa được phép |
| “lượt V2” | “Lượt khám đã hoàn tất” với điều kiện nghiệp vụ giải thích bằng tiếng Việt |

Không bỏ đi tính chính xác để làm câu ngắn. “Đã thu tiền”, “đã xác nhận lịch”, “đã đủ điều kiện xét nghiệm” và “đã cấp phát” là các kết quả khác nhau, cần hiển thị riêng khi chúng chưa đồng bộ.

## 6. Điều dưỡng — quầy tiếp nhận và hỗ trợ khám

### 6.1. Tìm bệnh nhân trước khi tạo mới

**UI:** Danh sách có tìm tên/CCCD, ngày sinh, giới tính, số giấy tờ và điện thoại; có link tạo hồ sơ. Tìm Nguyễn Văn An Demo bằng CCCD hoạt động. Đây là nền tảng tốt cho chống tạo trùng.

**Phê bình:** Trang tiếp nhận đưa nút “Tạo hồ sơ bệnh nhân” màu chính trước khi nhân viên tìm người đã có hồ sơ. Người vội có thể chọn tạo mới vì đó là hành động nổi bật nhất. Bảng patient hiển thị toàn bộ CCCD/điện thoại; cần khảo sát mức cần thiết tại quầy và cách che thông tin trên màn hình chung. Không đánh đồng quyền nurse tra cứu với quyền người đứng bên cạnh nhìn thấy màn hình.

**ĐX:** Bắt đầu từ ô “Tìm bệnh nhân: CCCD, điện thoại, họ tên”; kết quả có ngày sinh, giới tính, phần định danh đủ đối chiếu. Không có kết quả mới hiện hành động “Tạo hồ sơ mới”. Khi gần trùng tên + ngày sinh/giấy tờ, đề nghị mở hồ sơ hiện có, không tự hợp nhất bệnh nhân. Cần thiết kế khôi phục khi tạo thành công nhưng mất phản hồi, dựa trên cơ chế chống lặp đã có.

**Nghiệm thu:** Nhân viên mới tìm được người cũ và đi đến đăng ký mà không đọc hướng dẫn UUID; gõ CCCD đã tồn tại phải dẫn về đúng hồ sơ thay vì lỗi chung rồi mất dữ liệu vừa nhập.

### 6.2. Tạo hồ sơ mới

**UI:** Form chia nhóm, có lỗi từng trường trong source, có thanh nút lưu cố định. Đổi định danh có lý do và lịch sử trong source — nên giữ.

**Khoảng thiếu thực tế:** CCCD bắt buộc với mọi bệnh nhân cần được xem xét lại cho trẻ em, người chưa có giấy tờ, người nước ngoài hoặc người đi khám chưa mang giấy tờ. Chưa thấy lựa chọn người giám hộ/người liên hệ, loại giấy tờ, ngày sinh chỉ biết năm. Không nên khắc phục bằng cách bắt nurse bịa CCCD để qua validation.

Mặc định giới tính “Nam” có thể dẫn tới lưu nhầm khi quầy bận. Nên yêu cầu xác nhận hoặc để “Chưa chọn” nếu hợp đồng cho phép. Địa chỉ một ô tự do dùng được cho MVP; dữ liệu cần liên thông phải có quy ước riêng, không hứa rằng ô text là đủ cho mọi tích hợp.

**BHYT:** Lỗi không phải do người demo quên dấu gạch. Chính quy ước `2-8-1` là quy ước hiện tại của hệ thống cần thay. Phải thống nhất định dạng lưu, chuẩn hóa đầu vào và hỗ trợ dữ liệu cũ. Thông tin thẻ không đủ để tự kết luận quyền lợi, đúng tuyến hoặc số tiền cơ quan bảo hiểm thanh toán.

### 6.3. Cấp tài khoản là công việc phụ của tiếp nhận

**SRC:** Khối cấp tài khoản giải thích rõ không cần patient đăng nhập để tiếp tục khám; thao tác cấp lại không đặt lại mật khẩu đã đổi. Đây là quyết định đúng với nghiệp vụ đã chốt.

**Phê bình:** Gom “Tài khoản bệnh nhân và đăng ký khám” vào một khối khiến nhân viên dễ hiểu phải cấp tài khoản rồi mới đăng ký. Link tiếp tục nhỏ, không thể hiện bước chính. Nurse cũng cần biết chắc **đã có tài khoản/chưa có tài khoản/chưa kiểm tra được**, thay vì mở lại hồ sơ và vẫn thấy một nút có thể bấm.

**ĐX:** Sau lưu hồ sơ, hành động chính “Đăng ký khám cho [tên]”. Tài khoản ở mục riêng “Kênh xem kết quả”, ghi rõ tùy nhu cầu người bệnh, cách bàn giao kín thông tin và trạng thái hiện tại. Không đặt lại mật khẩu để giải quyết việc khám đợt mới. Với mật khẩu ban đầu `1` đã chốt cho demo, cần tách chính sách demo khỏi phương án kích hoạt thực tế trước khi dùng thật.

### 6.4. Đăng ký khám mới và đặt trước

**UI 03–07:** Chọn khoa → hai ngày → tải buổi → chọn buổi → tìm/chọn bệnh nhân → loại lượt → hình thức → đăng ký. Có số chỗ, bác sĩ, phòng, hạn giữ chỗ và link mở phiếu. Sau tạo, thông báo thành công và danh sách “chưa có phiếu” cùng tồn tại; thẻ buổi cũng chưa phản ánh ngay chỗ vừa giữ.

**Phê bình:** Cần quá nhiều động tác để tiếp nhận một người đã đứng ở quầy. Bộ lọc khoảng ngày phục vụ tra cứu lịch dài ngày nhưng chiếm chỗ của thao tác phổ biến “hôm nay”. Không tự đưa vùng form vừa mở vào tầm nhìn. Hình thức “đang có mặt tại quầy” là mặc định cả khi chọn lịch ngày mai, nên dễ gửi một yêu cầu không đúng ý người dùng; phiên review chủ động đổi sang đặt trước.

**ĐX:** Với quầy, mặc định hôm nay và khoa/phòng được phân công, có nút ngày mai/chọn ngày. Chọn patient trước hoặc giữ patient từ hồ sơ. Danh sách buổi hiển thị giờ, bác sĩ, phòng, chỗ còn lại và mức tải. Khi chọn ngày tương lai, giải thích hình thức đặt trước; backend vẫn phải xác minh điều kiện có mặt theo ngày. Sau thành công, hiện tóm tắt lượt và nơi cần đến, cập nhật danh sách, chuyển sang tiếp nhận người tiếp theo chỉ khi nhân viên chủ động.

### 6.5. Tái khám

**SRC:** Có liên kết lượt đã hoàn tất, khuyến nghị ngày, lý do nếu không dùng khuyến nghị, đồng ý khi đổi bác sĩ. Đây là cấu trúc nghiêm túc hơn một checkbox “tái khám”. **CXT:** Nhánh này chưa được chạy xuyên suốt trong phiên ngoài giờ.

**ĐX:** Sau chọn patient, hiện “Có khuyến nghị tái khám: khoa…, bác sĩ…, khoảng ngày…”, mở đúng danh sách buổi phù hợp. Giải thích trường hợp ngoài khoảng ngày, bác sĩ cũ nghỉ, chuyển bác sĩ, lượt trước chưa hoàn tất. Cho nurse thấy điều kiện nào đang thiếu mà không bắt nhớ sự khác nhau giữa lượt cũ và registration V2. Thu phí theo chính sách authoritative; không suy rằng cứ chọn tái khám là được giảm tiền.

### 6.6. Điều dưỡng ngoài quầy hành chính

Nurse hiện nổi bật ở nhập định danh và tiếp nhận, chưa thấy một bàn điều dưỡng đo sinh hiệu/sàng lọc chuẩn bị cho bác sĩ trong luồng được review. Với bệnh viện nhỏ, đây là phần giúp nurse có vai trò chuyên môn rõ ràng: ghi nhận sinh hiệu, dị ứng do người bệnh khai báo, lý do đến khám, người hỗ trợ và cảnh báo cần gọi người có chuyên môn. Ngưỡng và quy tắc phải được người phụ trách chuyên môn xác nhận; không tự thiết kế thuật toán phân loại cấp cứu chỉ để demo đẹp.

## 7. Bác sĩ — cần một bàn khám, không chỉ một trang hồ sơ

### 7.1. Màn hình bắt đầu ngày làm việc

**UI 16–17:** Lịch tải theo bác sĩ đăng nhập; phiếu đã thanh toán xuất hiện đúng buổi, phân biệt chưa đến và đã xác nhận. Không bắt bác sĩ chọn lại chính mình. Đây là điểm tốt.

**Phê bình:** Đăng nhập vào bộ lọc lịch và thẻ buổi có thể chấp nhận cho quản lý lịch, chưa tối ưu cho bác sĩ bắt đầu khám. Cần xem phiếu ở phần thấp hơn, mở phiếu rồi mở hồ sơ. Bác sĩ phải tự tải lại để thấy thay đổi; chưa thấy điều phối “người tiếp theo”, người đang khám, người chờ đọc Lab và người vắng.

**ĐX:** Trang “Khám bệnh hôm nay” có ba vùng: hàng chờ của phòng/bác sĩ, hồ sơ đang mở, kết quả cần xem. Hiển thị giờ đến, số thứ tự, ưu tiên đã được cơ sở quy định, thời gian chờ, lần khám mới/tái khám và trạng thái đủ điều kiện. “Lịch làm việc” và “Lịch hẹn” chuyển thành tác vụ phụ. Gọi số, bỏ qua và gọi lại phải có trạng thái/audit riêng, không tự làm bằng sắp xếp UI.

### 7.2. Hồ sơ khám

**UI 18:** Có banner ngữ cảnh cố định, tên/ngày sinh/khoa/bác sĩ/ngày khám, chẩn đoán, sửa triệu chứng, kê đơn, Lab và hoàn tất. Có xác nhận khóa hồ sơ. Đây là những thành phần nên giữ.

**Phê bình chi tiết:**

- Nội dung khám rất mỏng so với tên gọi hồ sơ khám: chưa thấy phân biệt lý do đến, bệnh sử, tiền sử, khám thực thể, sinh hiệu, dị ứng, thuốc đang dùng và kế hoạch.
- Chẩn đoán là tên và ICD gõ tự do; bác sĩ cần tìm danh mục, xác nhận chẩn đoán chính/phụ và tạm thời/xác định nếu chính sách cơ sở yêu cầu. Không biến mọi chẩn đoán thành text không kiểm soát chỉ để làm báo cáo dễ.
- Kết quả Lab không hiện ngay trong bàn khám; phải sang danh sách khác. Chưa thấy đánh dấu bác sĩ đã xem kết quả hoặc việc cần xem lại.
- Hoàn tất nằm trên cùng trang với thêm chẩn đoán và chỉ định, nhưng không có tổng kiểm các tác vụ đang chờ đã đọc được từ nguồn. Checkbox xác nhận không thể thay cho hiển thị thông tin còn thiếu.
- Sửa triệu chứng điều hướng sang trang khác; dễ mất mạch làm việc. Cần kiểm tra mất dữ liệu khi Back, refresh, đổi patient và hết phiên trước khi thiết kế autosave.
- Có thông tin kỹ thuật được thu gọn, đây là cải thiện đúng. Không nên đưa ID trở lại màn hình chính khi thêm chức năng.

**ĐX:** Một workspace giữ patient/lượt xuyên suốt, tab “Khám”, “Chỉ định & kết quả”, “Đơn thuốc”, “Kết luận & tái khám”, bên cạnh lịch sử phù hợp quyền. Mỗi lần chuyển patient phải rõ và có bảo vệ bản nháp. Không nhồi tất cả dữ liệu vào một trang dài hơn để gọi là tích hợp.

### 7.3. Chỉ định Lab

**UI 19:** Form không có banner patient dễ nhận diện; bác sĩ nhập loại xét nghiệm và mã giá, sau đó phải tự hoàn thành bước chuyển sang Lab. Nội dung cảnh báo phải giữ mã nếu rời trang thừa nhận quy trình còn phụ thuộc trí nhớ.

**ĐX:** Chọn “Công thức máu…” từ danh mục dịch vụ có mã, mẫu bệnh phẩm, giá dự kiến, hướng dẫn chuẩn bị và nơi thực hiện. Mã giá do danh mục/version cung cấp. Một thao tác “Gửi chỉ định” phải tạo được một ý định bền vững; UI hiển thị “đã lập/đang gửi/đã tới Lab/chờ thu”. Nếu backend vẫn là hai lệnh, cần lưu tiến trình để quay lại tiếp tục đúng chỉ định; không ngụy trang hai giao dịch thành thành công nguyên khối.

### 7.4. Kê đơn

**UI 20:** Khi đi từ hồ sơ, patient/hồ sơ/bác sĩ/khoa được điền từ ngữ cảnh — tốt, không nên phê bình là phải nhập lại mọi thứ. Chọn thuốc có giá, tồn, hạn dùng, chặn thuốc hết tồn trong danh sách; giữ thuốc đã chọn khi đổi trang.

**Phê bình:** Giao diện vẫn dành phần đầu lớn cho thông tin đã biết. Trường ngày kê mặc định ngày hiện tại trong hồ sơ lịch sử nên phải làm rõ đang bổ sung lượt cũ hay kê mới; chưa thực hiện lưu để kết luận backend có cho phép hay không. Cách dùng là một ô tự do, số lượng nhập trực tiếp, chưa thấy cấu trúc liều/đường dùng/lần mỗi ngày/số ngày và kiểm tra liên quan. Không đủ bằng chứng về cảnh báo dị ứng, trùng hoạt chất hoặc tương tác.

**ĐX:** Giữ patient và hồ sơ khóa theo ngữ cảnh khi vào từ bàn khám; đổi ngữ cảnh là hành động riêng có xác nhận. Có liều, đơn vị, đường dùng, tần suất, số ngày và hướng dẫn; nếu số lượng tính từ cấu trúc, cho bác sĩ xác nhận. Không tự thêm cảnh báo tương tác bằng vài quy tắc tự viết: cần nguồn dữ liệu và người chịu trách nhiệm chuyên môn. Tồn khả dụng và thay thế thuốc phải rõ, dược sĩ không tự ý sửa quyết định kê đơn.

### 7.5. Kết thúc và tái khám

**ĐX:** Màn kết thúc tóm tắt chẩn đoán, chỉ định còn chờ, đơn hiện hành, lời dặn, hướng xử trí, khuyến nghị tái khám và tài liệu giao bệnh nhân. Phân biệt **kết thúc công việc bác sĩ** với **kết thúc tất cả dịch vụ của lượt**: tiền thuốc/cấp thuốc có thể còn chờ. Hồ sơ đã khóa cần đường bổ sung/đính chính có lịch sử, không cho sửa âm thầm và không mặc định phải mở lại toàn bộ lượt.

## 8. Thu ngân — đúng tiền, đúng lượt, đối soát được cuối ca

### 8.1. Thu phí khám

**UI 10–12:** Có hàng khoản phí khám chờ thu, tìm theo patient, chọn phiếu, báo giá do hệ thống cung cấp, hỗ trợ mô phỏng ghi rõ. Sau thu, khoản biến mất khỏi hàng chờ và có thông báo biên nhận. Đây là đoạn bàn giao tốt nhất được chạy trong phiên.

**Phê bình:** Cần cuộn nhiều để từ hàng chờ tới số tiền/nút thu; sau thu thông báo nói theo ngôn ngữ Billing hơn là công việc quầy. Thẻ hàng chờ chưa nhấn ngày khám, bác sĩ/phòng và số tiền ngay khi quét danh sách. Cần phân biệt khoản đặt trước với người đang đứng quầy.

**ĐX:** Cột trái là hàng chờ, bên phải là phiếu thu và bảng giá; patient/lượt/tổng tiền luôn nhìn thấy. Thu xong có số biên nhận, in/xem lại, bước tiếp theo và quay lại người tiếp theo. Chỉ báo “đã xác nhận lịch” khi đã kiểm tra trạng thái đó, không suy từ việc tiền đã ghi.

### 8.2. Thu Lab và thuốc

**UI 13–14:** Mục menu nói “xét nghiệm & thuốc” nhưng form mặc định “Khám”. Nhân viên phải chọn loại, nhập mã xét nghiệm/đơn, chọn patient, nhập mã đợt điều trị. Thông báo còn phải giải thích không được suy ra mã từ mã khác.

**Phê bình:** Đây là cách tổ chức gần với một công cụ kiểm tra API. Việc người dùng phải mở ghi chú giữ BN/PHIEU/HOSO/LAB/DON trong hướng dẫn demo là một chỉ báo mạnh về gánh nặng thao tác, dù từng trường có thể đã đúng contract.

**ĐX:** Hàng “Khoản cần thu” thống nhất theo patient/lượt, nhóm Khám/Lab/Thuốc, có người phát sinh, thời điểm và số tiền. Chọn một dòng tự mang toàn bộ định danh tới backend. QR/mã ngắn có thể là phương án tìm nhanh phụ; không dùng UUID như mã đọc qua điện thoại. Vẫn giữ đối chiếu patient và lượt trước thu. Nếu cần thu nhiều khoản trong một lần, backend phải thiết kế phân bổ và thất bại từng phần trước; không nối nhiều nút thu bằng frontend rồi gọi là giao dịch duy nhất.

### 8.3. Biên nhận, hoàn tiền và cuối ca

**UI 15:** Có tìm hóa đơn và nghĩa vụ hoàn tiền đơn thuốc. **CXT:** Chưa ghi nhận hoàn tiền trong đợt này; không kết luận cơ chế hoàn tiền hỏng.

**Khoảng cần bổ sung:** Sổ thu theo ca/nhân viên/phương thức, tiền đầu ca, tiền thực nhận, chênh lệch, giao ca và lý do điều chỉnh. “Doanh thu”, “tiền đã thu”, “tiền hoàn”, “phải thu” và “dịch vụ đã thực hiện” phải khác nhau. Chuyển khoản đối chiếu thủ công không được hiển thị như kết nối ngân hàng tự động.

Với hoàn tiền, cần hành trình từ nguồn: đơn bị hủy/không cấp được → nghĩa vụ hoàn → quyền phê duyệt theo chính sách → khoản đã hoàn → biên nhận và trạng thái cập nhật. Không chỉ một ô nhập mã đơn. Cần xử lý thu thành công nhưng mất phản hồi, nhiều quầy cùng chọn khoản, báo giá hết hạn và tiền đã ghi nhưng Lab chưa nhận xác nhận.

## 9. Kỹ thuật viên xét nghiệm — quản lý công việc và mẫu

### 9.1. Hàng đợi

**UI 21:** Mặc định tất cả trạng thái, gồm hồ sơ hoàn tất/hủy cũ. Không có tìm trực tiếp theo tên patient trong bảng đang review; lọc khoa của LAB_TECH lại báo chưa có quyền tìm, mã đợt chăm sóc yêu cầu UUID. Trạng thái và nhãn tài chính bị bóp nhiều dòng.

**ĐX:** Mặc định việc hôm nay cần làm, chia “Chờ đủ điều kiện”, “Chờ lấy/nhận mẫu”, “Đang xử lý”, “Chờ duyệt”, “Đã trả”; trạng thái mẫu chỉ thêm khi backend đã mô hình hóa. Có tìm tên/ngày sinh/mã phiếu/mã mẫu, thời điểm nhận, hạn trả dự kiến, cảnh báo trễ và quyền ưu tiên được quy định. Lịch sử là tab riêng. Xét nghiệm chưa thu tiền cần trạng thái và người/quầy đang xử lý, không chỉ một nhãn chờ.

### 9.2. Nhập và công bố kết quả

**UI 22–23:** Mỗi chỉ số phải gõ lại tên, giá trị, đơn vị và khoảng tham chiếu. Có thêm dòng, ngày thực hiện và kết luận. Khi cuộn đến form nhập, patient không còn ở vùng nhìn. Chưa thấy quy trình mẫu/duyệt kết quả ngay màn này.

**Phê bình:** CBC không thể đại diện nghiêm túc chỉ bằng một dòng WBC nhập tay tùy ý. Một hệ thống xét nghiệm cần mẫu chỉ số theo dịch vụ, kiểu dữ liệu, đơn vị và khoảng tham chiếu có quản lý phiên bản. Kết quả định tính và định lượng khác nhau. Khoảng tham chiếu có thể phụ thuộc dữ liệu patient và phương pháp; không tự suy là một dải dùng cho mọi người.

**ĐX:** Template dịch vụ, nhập dạng bảng bằng bàn phím, kiểm tra thiếu chỉ số, lưu nháp và xác nhận công bố. Bất thường phải có ký hiệu/chữ, không chỉ màu. “Giá trị nguy cấp” cần người nhận, thời điểm liên hệ và xác nhận theo quy trình cơ sở; không tự đặt ngưỡng. Chỉnh kết quả đã công bố phải có phiên bản, lý do, người sửa và thông báo bác sĩ. Phân biệt “Lab đã công bố”, “bác sĩ đã xem” và “patient được phép xem”.

### 9.3. Mẫu bệnh phẩm là khoảng cách sản phẩm

Trong các màn hình được review chưa thấy chuỗi nhận dạng mẫu, người lấy, lúc lấy/nhận, chất lượng, từ chối/lấy lại. Đây là phần nên đưa vào giai đoạn nâng độ nghiêm túc của LIS nhỏ, trước khi nói đến tích hợp máy. Không cần triển khai nội kiểm phức tạp hoặc kết nối máy ngay, nhưng không nên trình bày việc bấm “Bắt đầu” là đã kiểm soát được mẫu.

## 10. Dược sĩ — cấp phát khác với quản lý danh mục

### 10.1. Màn hình mặc định

**UI 24–25:** Dược sĩ đăng nhập vào danh mục thuốc, phải chuyển tab Đơn thuốc. Danh mục có tồn thấp/hết hàng/hạn dùng — nên giữ. Danh sách đơn có tìm patient, thuốc, tiền, trạng thái và thanh toán, tốt hơn tra cứu bằng UUID đơn thuần.

**Phê bình:** Quầy phát thuốc cần ưu tiên đơn chờ xử lý và người đang chờ, không phải “Thêm thuốc”. Danh sách mặc định trộn đơn hủy/cấp phát từ các kỳ cũ; trạng thái “Còn hiệu lực” không giúp biết ngay có được phát lúc này hay không. “Tồn hiện có có thể gồm thuốc giữ cho đơn khác” là lời giải thích kỹ thuật cần chuyển thành số tồn/đã giữ/khả dụng rõ ràng.

**ĐX:** Trang “Cấp thuốc hôm nay” với hàng “Chờ thanh toán”, “Sẵn sàng soạn”, “Đang soạn”, “Đã giao”, “Cần xử lý”. Backend hiện có trạng thái nào thì dùng đúng trạng thái đó; không thêm nhãn giả nếu chưa có người nhận soạn. Danh mục và kho là nhóm tác vụ riêng.

### 10.2. Đối chiếu và xác nhận

**UI 26–27:** Mở đơn lịch sử mẫu thấy patient có tên/ngày sinh, bác sĩ và khoa lại ghi chưa được cấp quyền tra cứu tên. Vùng thao tác xuất thuốc nằm trước thông tin patient/dòng thuốc; mở xác nhận chỉ thấy tổng tiền và số dòng. Đây là quan sát ở đơn có sẵn, không phải đơn V2 mới tạo trong phiên.

**ĐX:** Patient, ngày sinh, mã lượt dễ đọc, bác sĩ kê, thời điểm, thuốc/số lượng/cách dùng và trạng thái đủ điều kiện phải nằm sát quyết định phát. Xác nhận cuối cần tên patient và bảng thuốc, không chỉ số tiền. Xem tên bác sĩ/khoa tối thiểu không cần quyền đọc toàn hồ sơ nhân sự.

Cần làm rõ “đã trừ tồn/xuất kho” và “đã giao người bệnh”. Nếu hiện tại chỉ có một trạng thái, thống nhất định nghĩa và audit trước khi tách. Không tự tính rằng xuất thuốc thành công là người bệnh đã cầm đúng thuốc.

### 10.3. Tình huống thực tế

Khảo sát và bổ sung theo thứ tự: thuốc không đủ, thuốc bị giữ bởi đơn khác, hết hạn khi đang xử lý, người bệnh chưa đóng tiền, đã đóng nhưng không phát được, bác sĩ đổi đơn, trả thuốc/hoàn tiền. Không tự ý thay thuốc hoặc phát một phần nếu contract hiện tại chưa hỗ trợ. Khi có nhiều lô thuốc, tồn một số và một ngày hết hạn cho toàn loại thuốc là chưa đủ để khẳng định có quản lý lô/FEFO; cần mô hình lô, giao dịch nhập/xuất/kiểm kê và truy vết trước.

## 11. Bệnh nhân — cần được hướng dẫn, không phải hiểu HIS

### 11.1. Điều đang làm tốt

**UI 28–31:** Tài khoản patient tự có đúng dữ liệu của mình, không yêu cầu dán mã patient. Phiếu có ngày, bác sĩ, khoa, phòng và số thứ tự; lời nhắc đến quầy xác nhận có mặt rõ. Thông báo chờ thanh toán/đã xác nhận phát sinh theo chính lượt nurse/cashier vừa xử lý. Đây là minh chứng phối hợp service có ý nghĩa, không phải dữ liệu chèn riêng cho mỗi trang.

### 11.2. Điều làm trải nghiệm còn sơ sài

- Cùng một shell quản trị, sidebar nhiều nhóm và menu “Hồ sơ cá nhân”, chọn theme, đăng xuất chiếm sự chú ý.
- Patient vào “Kết quả khám” hoặc “Kết quả Lab” chỉ thấy một dòng chưa có kết quả và phân trang vô hiệu; không được nối với phiếu đang chờ khám đã tồn tại.
- Thông báo mới và cũ cùng trong lịch sử; lời nhắc chờ thanh toán cũ vẫn tồn tại dù đã có thông báo xác nhận mới. Cần phân biệt lịch sử với việc còn phải làm.
- Khung 07:00–17:00 trông như giờ hẹn nhưng thực ra là buổi khám; patient có thể hiểu phải chờ cả ngày hoặc được khám đúng 07:00. Cần giải thích số thứ tự và giờ dự kiến chỉ khi hệ thống có cơ sở tính.
- Chưa có bằng chứng UI in/tải tài liệu, liên hệ cơ sở, hướng dẫn di chuyển, xem đơn thuốc và dặn dò trong một trang tổng kết lượt.

### 11.3. Hướng đề xuất

Một trang “Lượt khám của bạn” với lời nhắc tiếp theo ở đầu: “Đã xác nhận lịch ngày… Hãy đến quầy…”, hoặc “Đang chờ kết quả…”, “Kết quả đã sẵn sàng…”. Các mục lịch sử khám, kết quả, đơn thuốc, thanh toán truy cập từ cùng lượt. Patient không nhất thiết được đặt lịch hay trả tiền online; các tính năng đó chỉ thêm nếu quy trình và tích hợp đã chốt.

Tài khoản portal vẫn tùy nhu cầu theo quyết định hiện tại. Không biến việc chưa dùng portal thành rào cản khám. Hỗ trợ người cao tuổi bằng chữ đủ lớn, đường dẫn quay lại rõ, trạng thái dễ hiểu; quan hệ người giám hộ/đại diện cần mô hình quyền riêng nếu cơ sở muốn hỗ trợ, không chia sẻ chung mật khẩu.

Kết quả không nên được AI hoặc UI tự diễn giải thành chẩn đoán cho bệnh nhân. Cần trình bày nguyên bản có người chịu trách nhiệm, thời điểm, lời dặn và nơi liên hệ. Quyết định thời điểm công bố cho patient là chính sách nghiệp vụ cần xác nhận.

## 12. Manager và trưởng khoa — điều phối năng lực, giải quyết tắc nghẽn

### 12.1. Phân biệt hai kiểu quyền hiện đang có

**UI/SRC:** Tài khoản role `MANAGER` có trang quản lý buổi khám/phạm vi được cấp. Bác sĩ được bổ nhiệm trưởng khoa vẫn là `DOCTOR`, có capabilities quản lý khoa và chuyển workspace theo phương án B. Không nên trình bày hai đường này như cùng một trường role có thể đổi qua lại.

**OLD:** Bổ nhiệm/miễn nhiệm, giữ quyền bác sĩ, chuyển sang quản lý và về khám đã được ghi trong biên bản tuần trước. **SRC:** `DepartmentManagementWorkspace` hiện lấy khoa quản lý cho `DOCTOR`; lưới là `DoctorWorkShiftsWorkspace`, chưa phải hệ thống phân ca đầy đủ cho nurse/Lab/kế toán/dược. Không được demo danh sách 12 nhân sự rồi nói tất cả đã được phân ca.

**MF-01 cần ưu tiên:** UI admin xác nhận `demo.manager` có phiên bản scope 0, chọn 0 khoa; manager bị từ chối ở lịch khoa Nội, nhưng vào báo cáo cũ vẫn nhận dữ liệu zero-filled phạm vi toàn viện, không bị từ chối. `ReportController` chỉ kiểm role ADMIN/MANAGER, truyền khoa tùy chọn tới `ReportApplicationService`; reader được xem không kiểm phạm vi người gọi. `OutpatientReportController` cũng cần audit cùng chính sách, không mặc định an toàn chỉ vì báo cáo có kiểm độ phủ.

Đây là khác biệt với thiết kế đã chốt “các khoa được phân công”. Cần backend quyết định quyền và phạm vi của từng loại báo cáo, trường hợp không truyền khoa, khoa ngoài quyền, nhiều khoa và quyền bị thu hồi khi token còn hiệu lực. Bác sĩ trưởng khoa cần được cấp capability đọc báo cáo khoa phù hợp; chỉ kiểm `MANAGER` sẽ có thể chặn sai người đang thật sự quản lý. Frontend lọc dropdown không thay cho kiểm tra API.

### 12.2. Dashboard trưởng khoa cần trả lời điều gì?

Một trưởng khoa cần biết **ca nào thiếu người, phòng nào quá tải, bệnh nhân nào đang chờ quá lâu và cần chuyển việc cho ai**. Các số “sức chứa”, “giữ chỗ”, “đã xác nhận”, “đã phục vụ” đang có là hữu ích nhưng không bằng số người đang chờ thật. Source đã ghi chú điều này; cần giữ sự phân biệt.

**ĐX màn tổng quan khoa:**

| Vùng | Nội dung cần thấy | Hành động từ số liệu |
|---|---|---|
| Nhân lực hôm nay | Người được phân ca, ca chưa có người, lịch vắng đã duyệt | Mở ca, bố trí người thay đúng điều kiện |
| Phòng/buổi | Công suất, số xác nhận, số đã đến, đang khám, hoàn tất | Mở đúng buổi, ngừng nhận hoặc điều chỉnh có kiểm soát |
| Điểm nghẽn | Chờ khám, chờ Lab, kết quả chưa đọc, chờ cấp thuốc | Mở danh sách liên quan trong phạm vi được phép |
| Ngoại lệ | Bác sĩ vắng, phòng không dùng được, chỉ định/thu tiền cần đối soát | Giao người xử lý, ghi lý do, theo dõi kết quả |
| Chất lượng vận hành | Thời gian chờ, tỷ lệ bỏ hẹn, tải theo buổi | Xem xu hướng và danh sách làm căn cứ |

Không tự dựng KPI bằng cách đếm số card frontend. Nếu chưa có mốc “đã đến/được gọi/nhận mẫu/đọc kết quả”, phải bổ sung dữ liệu nguồn trước khi tính thời gian chờ tương ứng. Một số chỉ số liên khoa chỉ nên hiện trạng thái tiến độ cần phối hợp, không mở toàn bộ nội dung chuyên môn hoặc tài chính ngoài quyền.

### 12.3. Lưới phân ca tuần

**OLD/SRC:** Đã có lưới 7 ngày, tổng giờ hiệu lực, cột bác sĩ cố định, ngày/tuần, kiểm trùng, chặn sửa ca có buổi liên quan, tạo buổi từ ca và đọc nhiều trang trước tổng. Đây là tiến bộ thực chất, không nên viết lại chỉ vì muốn drag-and-drop.

**Khoảng thiếu sản phẩm nên phát triển:**

1. Hiển thị rõ ca làm việc khác buổi nhận bệnh nhân; một bác sĩ có mặt không tự động có buổi mở đăng ký.
2. Ca trống/thiếu người và phòng chưa phủ giờ phải nhìn ra trên lưới, không chỉ thấy các ca đã có.
3. Lịch mẫu lặp theo tuần, sao chép tuần và xem trước xung đột. Chưa có endpoint hàng loạt thì phải làm rõ thao tác từng ca, không báo lưu cả tuần nếu chỉ thành công một phần.
4. Luồng xin nghỉ/đổi ca/người thay: người yêu cầu, người duyệt, lý do, ảnh hưởng tới các lượt đã đăng ký và thông báo. Không hủy lịch đã có bệnh nhân bằng cách bỏ qua guard đang có.
5. Bản nháp và lịch đã công bố nếu cơ sở thực sự có quy trình duyệt; người được xếp lịch xác nhận đã nhận thông tin. Chỉ thêm khi có nhu cầu, không bắt cơ sở nhỏ phê duyệt ba cấp.
6. Mở rộng sang điều dưỡng/kỹ thuật viên theo vị trí và phạm vi khoa nếu quyết định sản phẩm chốt; không biến `DoctorWorkShift` thành một ca nhân sự chung bằng cách chỉ đổi nhãn UI.
7. Giữ tổng giờ, ca hủy, ca chồng lấn và giờ nghỉ có định nghĩa rõ. Tổng số giờ là thông tin tải, không tự kết luận tuân thủ lao động hoặc đủ năng lực chuyên môn.

### 12.4. Chuyển workspace theo phương án B

Giữ một tài khoản và chuyên môn; nút chuyển cần nói rõ “Khám bệnh”/“Quản lý khoa”, kèm khoa hiện hành và trạng thái đang dùng. Khi quay về khám, trở lại ngữ cảnh gần nhất nếu còn hợp lệ; nếu có bản nháp phải cảnh báo trước. Việc chuyển giao diện không tự tạo/thu hồi quyền backend trong phiên.

Khi miễn nhiệm, workspace quản lý phải đóng hoặc chuyển về khám có lời giải thích; nội dung khám còn nguyên. Phải thử khi đang mở tab, lệnh đang gửi và dữ liệu cũ còn cache. Phần lịch sử người ra quyết định vẫn gắn cùng nhân sự, không tạo account quản lý thứ hai.

### 12.5. Không nên thêm gì chỉ để manager trông “cao cấp”?

Không thêm chat nội bộ, AI dự đoán, biểu đồ doanh thu toàn viện, chấm hiệu suất cá nhân hoặc phân quyền hệ thống vào dashboard khoa chỉ để nhiều mục hơn. Một danh sách ca thiếu người có link xử lý đáng giá hơn bốn biểu đồ không dẫn tới hành động. Cơ sở nhỏ có thể xử lý nghỉ/đổi ca bằng một luồng duyệt đơn giản và thông báo đủ dùng.

## 13. Admin — quản trị cơ sở và quyền truy cập

### 13.1. Điểm tốt

**UI 36–42, 45:** Có khoa/phòng/nhân sự/tài khoản, dữ liệu liên kết đúng loại hồ sơ, role tự xác định từ chức danh, giấy phép bắt buộc cho bác sĩ trong form. Bổ nhiệm có tìm người, xem trước, lý do và lịch sử. Tài khoản manager có link sang phạm vi quản lý. Đây là các khả năng quản trị thực tế cần giữ.

### 13.2. Phê bình

- Admin có sidebar hầu hết nghiệp vụ, dễ bị dùng như tài khoản toàn năng để demo qua mọi quầy. UI hồ sơ khám còn cho ADMIN thao tác chẩn đoán/hoàn tất trong source. Quyền kỹ thuật không đồng nghĩa tư cách chuyên môn; cần rà lại mục đích quyền ADMIN và cơ chế hỗ trợ đặc biệt có audit, không mặc định admin khám bệnh thay bác sĩ.
- Thay đổi phạm vi manager đặt trong trang “Quản lý buổi khám”, dưới nhiều nội dung lập lịch. Quyền truy cập nên là một khu vực quản trị rõ ràng, không phải một mục ẩn ở cuối form nghiệp vụ.
- Bổ nhiệm trưởng khoa ở dưới danh sách 12 người, phải cuộn dài. Chức vụ và người đương nhiệm nên gần thông tin khoa, hành động bổ nhiệm mở panel riêng có tóm tắt người/khoa/quyền/tác động.
- Có trạng thái hiển thị pha Anh–Việt: Active, inactive, MANAGER. Cần thuật ngữ nhất quán.
- Form tạo account mặc định mật khẩu `1`, username lấy từ CCCD. Điều này đúng thiết kế demo đang có nhưng cần quyết định riêng cho triển khai thật, nhất là account nhân viên; đổi mật khẩu sau lần đầu không giải quyết toàn bộ khoảng thời gian trước kích hoạt.
- Form có cả “Tạo nhân sự” và “Lưu và tạo tài khoản”; cần giải thích rõ hai kết quả và xử lý trường hợp hồ sơ đã lưu nhưng tạo account chưa xong, tránh tạo trùng nhân sự.

### 13.3. Hướng đề xuất

Admin có các nhóm: tổ chức và phòng, nhân sự chuyên môn, tài khoản và truy cập, bổ nhiệm/phạm vi, danh mục/cấu hình được phép, nhật ký và vận hành. Manager có nhóm: lịch, tải, phân công, ngoại lệ và kết quả hoạt động của khoa. Admin có thể xem thông tin quản lý cần thiết nhưng không là người điều hành mọi ca khám hàng ngày.

Tóm tắt quyền trên account: chuyên môn, chức vụ bổ sung, khoa được giao, hiệu lực, lịch sử thay đổi, trạng thái truy cập. Với thao tác nhạy cảm, hiển thị tác động thật, lý do và xác nhận đúng đối tượng. Không thêm checkbox role tùy ý phá vỡ liên hệ chức danh/chuyên môn hiện có.

## 14. Luồng liên vai trò — chỗ sản phẩm cần nổi bật nhất

### 14.1. Hành trình chuẩn đề xuất

| Chặng | Người chịu trách nhiệm | Công việc cần hiện tự động cho người tiếp theo | Điều kiện chưa xong phải nói rõ |
|---|---|---|---|
| Tìm/tạo hồ sơ | Nurse | Patient và lượt đăng ký có đúng định danh | Hồ sơ chưa lưu hoặc chưa xác minh được trùng |
| Đăng ký khám | Nurse | Khoản khám chờ thu cho cashier | Giữ chỗ có hạn, chưa xác nhận lịch |
| Thu phí khám | Cashier | Phiếu đủ tài chính; nurse/bác sĩ đọc được trạng thái | Đã thu nhưng xác nhận lượt đang xử lý |
| Có mặt/sàng lọc | Nurse | Hàng chờ của đúng bác sĩ/phòng | Chưa tới viện, thiếu điều kiện chuẩn bị |
| Khám/chỉ định | Doctor | Khoản thu Lab, yêu cầu Lab, patient biết bước tiếp | Chỉ định đã lập nhưng chưa gửi xong |
| Thu Lab | Cashier | Lab được phép tiếp nhận theo nguồn tài chính | Thu đã ghi, tín hiệu xác nhận đang trễ |
| Nhận mẫu/thực hiện/trả kết quả | Lab | Kết quả cần xem của bác sĩ, trạng thái patient phù hợp | Mẫu không đạt, chờ lấy lại hoặc chờ duyệt |
| Đọc kết quả/kê đơn/kết luận | Doctor | Khoản thuốc chờ thu, đơn cần cấp phát, lời dặn | Chờ kết quả còn lại hoặc cần đính chính |
| Thu thuốc | Cashier | Đơn đủ điều kiện tới quầy dược | Đã thu nhưng chưa đủ điều kiện phát |
| Cấp/giao thuốc | Pharmacist | Trạng thái giao thuốc và chứng từ | Thiếu thuốc, phát thất bại, cần hoàn/đổi |
| Theo dõi sau khám | Patient/nurse theo việc | Kết quả, đơn, dặn dò, khuyến nghị tái khám cùng lượt | Portal tùy nhu cầu; việc khám không bị chặn bởi chưa kích hoạt account |

Một số trạng thái trong bảng là mục tiêu cần phát triển, không phải trạng thái đã có. Không được hiển thị bước mẫu/duyệt/giao thuốc nếu backend chưa ghi nhận.

### 14.2. Cần một hồ sơ lượt khám dùng chung về ngữ cảnh

Đề xuất một header xuyên các trang liên quan: tên, ngày sinh, mã bệnh nhân dễ đọc, lượt/ngày, khoa, bác sĩ, phòng, tình trạng đang xử lý. Bên dưới là tiến trình theo mốc sự kiện và công việc còn chờ, mỗi role chỉ thấy phần phù hợp.

Đây không phải yêu cầu gộp database. Có thể dùng API composition hoặc read model được thiết kế có chủ sở hữu và quyền phù hợp. Mỗi trạng thái quan trọng vẫn được xác minh ở service có thẩm quyền khi thực hiện lệnh.

Nếu một phần chưa đọc được, hiển thị “Chưa cập nhật được trạng thái thanh toán, dữ liệu lúc…” và giữ phần khám đang dùng được nếu nghiệp vụ cho phép. Không suy thanh toán từ badge cũ, không chặn cả màn chỉ vì không tải được tên của một tham chiếu phụ.

### 14.3. Bàn giao phải có trạng thái nhận

Một link “chuyển sang Lab” không phải bàn giao hoàn chỉnh. Cần ít nhất: công việc được tạo lúc nào, ở hàng chờ nào, ai/đơn vị nào có trách nhiệm, trạng thái mới nhất và cách xử lý khi quá lâu. Với cơ sở nhỏ, có thể chưa gán từng người cho mọi bước; nhưng phải chỉ rõ quầy/đơn vị chịu trách nhiệm.

Thông báo chung không thay cho hàng công việc. Thông báo có thể đã đọc nhưng xét nghiệm chưa làm; xét nghiệm có thể đã làm dù thông báo chưa đọc. Hai vòng đời này phải tách biệt.

## 15. Làm nổi bật microservices bằng giá trị vận hành

### 15.1. Điều cần điều chỉnh trong mục tiêu demo

Người mua không cần thấy tám tên service trên màn hình. Họ cần thấy nhân viên ở các quầy làm việc cùng một lượt, thông tin tự tới đúng nơi và hệ thống không thu/phát lặp khi mạng chậm. Đây là cách kiến trúc có thể được chứng minh bằng sản phẩm.

Repo hiện mô tả sáu deployment nghiệp vụ, tám context đang hoạt động, cùng Gateway/Eureka; không nên đếm menu hoặc JVM rồi gọi đó là số microservices. Core và Support gộp triển khai nhưng vẫn có ranh giới persistence. Không cần tách thêm service hoặc microfrontend chỉ để presentation trông phức tạp.

### 15.2. Bốn minh chứng nên xây thành trải nghiệm tốt

| Minh chứng | Người dùng cần thấy | Kiểm chứng kỹ thuật riêng |
|---|---|---|
| Liên thông công việc | Nurse đăng ký, cashier thấy khoản; Lab có yêu cầu; bác sĩ nhận kết quả | Correlation của cùng lượt, sự kiện/consumer và thẩm quyền dữ liệu |
| Chống làm lặp | Mất phản hồi: biết đang kiểm tra, quay lại vẫn thấy cùng kết quả, không thu hai lần | Idempotency, recovery, audit; không chỉ disable nút |
| Suy giảm có kiểm soát | Một phần chậm: giữ phần còn dùng được, chỉ khóa hành động cần xác minh | Timeout/circuit breaker/retry, stale/read model và điều kiện nghiệp vụ |
| Dữ liệu quản lý đáng tin | Điều hành gần hiện tại khác báo cáo đã đối soát, có thời điểm cập nhật | Projection, độ phủ, reconciliation, replay và quyền phạm vi |

Các cơ chế chống lặp/độ phủ đã có trong source là điểm mạnh. UI hiện lại diễn đạt chúng bằng “idempotency”, “Billing”, “contract”, hoặc yêu cầu giữ nhiều mã. Cần chuyển thành feedback rõ: **đã ghi nhận**, **đang xác minh**, **chưa gửi được**, **không tạo lại**, **liên hệ quầy nào**.

### 15.3. Tách màn quản trị vận hành khỏi UI chuyên môn

Admin được phép có trang tình trạng liên thông: tác vụ đồng bộ chậm, lần thử gần nhất, mã hỗ trợ, hàng lỗi cần xử lý, audit thao tác khôi phục. Nurse/doctor chỉ thấy ảnh hưởng tới lượt của mình. Không đưa nút replay/outbox lên bàn khám, không cung cấp payload chứa thông tin sức khỏe cho mọi role.

Demo sự cố phải ở môi trường cách ly, có kịch bản và phục hồi. Ví dụ: thu phí mẫu, ngắt đường nhận sự kiện trong môi trường thử, UI vẫn ghi “đã thu, đang xác nhận”; khôi phục, trạng thái chỉ cập nhật một lần. **Chưa thực hiện fault injection trong review này.** Không được trình bày bảng trên như kết quả đã chạy thành công.

## 16. So sánh với sản phẩm thực tế

Đối chiếu dưới đây dựa trên tài liệu chính thức/public, không phải dùng thử toàn bộ sản phẩm đối thủ, không đánh giá độc lập giá, tốc độ, chất lượng triển khai hay mức tuân thủ. Mục tiêu là học cách tổ chức công việc phù hợp, không yêu cầu MediFlow có ngay toàn bộ HIS bệnh viện lớn.

| Tham chiếu | Điều tài liệu thể hiện | Bài học phù hợp MediFlow |
|---|---|---|
| [VNPT HIS](https://www.vnptit.vn/products/health/his) | Các phân hệ được tích hợp, hỗ trợ tác vụ và cảnh báo | Định vị theo hiệu quả vận hành; chỉ đưa cảnh báo/gói mẫu khi có nguồn dữ liệu và nghiệp vụ đúng |
| [VNPT Home & Clinic trong bộ giải pháp y tế](https://vnpt.vn/doanh-nghiep/giai-phap-cntt/y-te-dien-tu) | Nhắm cơ sở khám, quản lý tiếp nhận, khám, dược và viện phí | Tập trung ngoại trú hoàn chỉnh trước khi mở nội trú/phẫu thuật |
| [OpenMRS O3 Service Queues](https://o3-docs.openmrs.org/en-US/docs/configure-o3/configure-service-queues/) | Hàng đợi có trạng thái, ưu tiên, cột dữ liệu và số lượt cấu hình | Thiết kế hàng việc theo địa điểm/bước, không lấy bảng lịch sử làm dashboard mặc định |
| [Bahmni Clinical Services](https://www.bahmni.org/clinical-services/) | Form và nội dung lâm sàng được nhóm theo patient/visit dashboard | Giữ bối cảnh bệnh nhân và lượt; cho xem thông tin đã ghi và kết quả trong bàn khám |
| [Bahmni Billing and Accounting](https://www.bahmni.org/billing-and-accounting) | Chỉ định thuốc tạo dữ liệu dự thảo thu phí; có hồ sơ tài chính patient | Hạn chế nhập lại ở quầy, để nguồn chuyên môn đưa đúng dòng dịch vụ tới tài chính |

Từ các tham chiếu này, nhận định của tôi là MediFlow đang có nhiều mảnh nền tảng cần thiết, nhưng khoảng cách lớn nhất là **tổ chức công việc và ngữ cảnh liên tục**, kế tiếp là độ sâu hồ sơ/mẫu xét nghiệm/cấp phát. Không có bằng chứng để nói đổi sang màu của VNPT hoặc sao chép dashboard Bahmni sẽ làm sản phẩm đạt mức vận hành của họ.

## 17. Báo cáo, hiệu năng và khả năng tiếp cận

### 17.1. Báo cáo cần hai sản phẩm khác nhau

**Bảng điều hành trong ngày:** phục vụ quyết định ngay, ghi thời điểm cập nhật, trạng thái nguồn và giới hạn phạm vi. Có thể chưa chốt nhưng không được giả vờ chính xác tuyệt đối.

**Báo cáo chốt kỳ:** có nguồn đầy đủ, đối soát, thời điểm và định nghĩa số liệu. Source hiện đã fail-closed khi không có coverage hợp lệ; nên giữ. UI nên mặc định kỳ gần nhất đã sẵn sàng hoặc giải thích trước khi người dùng bấm. Không gỡ guard độ phủ chỉ để biểu đồ có số.

Ảnh 34/46 cho thấy báo cáo cũ có thể hiện các số 0 cùng cảnh báo nguồn chưa bao phủ ngoại trú; ảnh 43 cho thấy báo cáo mới từ chối kỳ chưa đủ điều kiện. Không dùng số 0 của báo cáo cũ để kết luận không có giao dịch. Cũng không dùng giao dịch thu khám vừa tạo để chứng minh báo cáo cũ sai số khi nó không cam kết bao phủ nguồn đó.

Một hệ thống nghiêm túc cần drill-down: từ tổng lượt sang danh sách lượt, từ tiền thu sang khoản thu, từ chênh lệch sang lý do, trong đúng quyền. Biểu đồ không có danh sách đối chiếu sẽ khó dùng khi trưởng khoa/thu ngân bị hỏi “số này từ đâu?”.

### 17.2. Hiệu năng cảm nhận

**Quan sát:** Nhiều thao tác dùng tải thủ công, tìm rồi chọn rồi bấm tìm lần nữa. Tra cứu tên tham chiếu có độ trễ riêng; lúc chờ có placeholder. Không có bằng chứng định lượng để kết luận backend chậm.

**ĐX:** Đo theo nhiệm vụ trên bản build production và dữ liệu đủ lớn: thời gian tìm patient, thời gian chọn buổi, thời gian tới form sẵn sàng, nhận khoản chờ thu, nhận kết quả ở bàn khám. Giảm thao tác thừa trước khi tối ưu vài mili giây API. Có loading riêng từng vùng, giữ nội dung cũ với nhãn thời điểm nếu phù hợp, tránh nhảy bố cục khi tên được resolve. Cache theo phiên/quyền và hủy phản hồi cũ khi đổi patient/khoa; không tối ưu bằng cách bỏ kiểm tra authority.

**Mục tiêu nghiệm thu đề xuất, chưa phải số đo:** thao tác chính có phản hồi tức thời nhìn thấy; tác vụ đọc thông thường p95 dưới khoảng 2 giây trên cấu hình/LAN đã thống nhất; cập nhật liên quầy tới người nhận trong khoảng 5 giây ở tải thử đã chốt. Nếu chưa đạt thì hiển thị trạng thái chậm rõ. Ngưỡng phải được đội dự án xác nhận theo môi trường, không hứa như SLA hiện có.

### 17.3. Khả năng tiếp cận

Đối chiếu [WCAG 2.2](https://www.w3.org/TR/WCAG22/): cần kiểm contrast, focus, điều hướng bàn phím, thông báo trạng thái, thông tin không chỉ phụ thuộc màu và vùng bấm. Không được tự nhận đạt chỉ vì component dùng `aria-label` và nút cao 44 px. WCAG 2.2 mức AA về target size có ngưỡng và ngoại lệ riêng; 44 px ở đây là lựa chọn usability cho sản phẩm, không diễn giải thành yêu cầu AA bắt buộc cho mọi control.

Các ca phải thử: Tab qua bộ chọn patient; Enter chọn kết quả; Escape đóng danh sách; focus sau mở panel và sau lỗi; không để thanh lưu cố định che ô đang nhập; zoom 200%; màu trạng thái ở sáng/tối; đọc số tiền; chữ dài, tên tiếng Việt dài và người trùng tên. Định dạng ngày native đang hiển thị `mm/dd/yyyy` trong browser trong khi nhãn phụ diễn giải `dd/MM/yyyy`; nên có một cách hiển thị ngày rõ và thống nhất tại điểm quyết định, không phụ thuộc trí nhớ thiết lập trình duyệt.

Không xem responsive staff dashboard là mục tiêu duy nhất. Patient cần thử điện thoại thật; quầy khám cần thử màn hình laptop và máy bàn phổ biến, có bàn phím và máy in. Lưới tuần có thể cuộn ngang với cột người cố định, nhưng không dùng cuộn ngang cho toàn trang hoặc bắt điều dưỡng dò mốc giờ mà mất tên người.

## 18. Thích nghi với ngoại trú Việt Nam

### 18.1. BHYT và chính sách tài chính

Nguồn [BHXH Việt Nam về mẫu thẻ mới](https://baohiemxahoi.gov.vn/tintuc/pages/hoat-dong-he-thong-bao-hiem-xa-hoi.aspx?CateID=52&ItemID=15882&OtItem=date) mô tả sử dụng 10 ký tự mã số BHXH trên thẻ thay cho mã thẻ 15 ký tự. [Giải thích mã thẻ cũ của BHXH](https://baohiemxahoi.gov.vn/chuyen-trang-bhxh-bhyt/tu-van-hoi-dap/Pages/default.aspx?ItemID=15654) nêu cấu trúc 15 ký tự. Mẫu `01-12345678-9` trên form MediFlow không khớp hai cách biểu diễn đó. Đây là đối chiếu định dạng, không phải tư vấn quyền hưởng BHYT của một người cụ thể.

Cần tách ba việc: ghi mã/thông tin bảo hiểm, xác minh thông tin với nguồn được phép, tính và đối soát quyền lợi theo chính sách/tích hợp được duyệt. Báo giá demo tự áp tỷ lệ mô phỏng không được biến thành tuyên bố đã kết nối cổng BHYT hoặc giải quyết đúng quyền lợi thực tế. Giữ chữ “mô phỏng” trong môi trường demo; khi dùng thật phải có quy trình và kiểm chứng chuyên biệt.

### 18.2. Giấy tờ và thiết bị

Cơ sở vẫn có thể nhận form giấy như đầu vào đã chốt. Phần mềm cần biết ai nhập, khi nào, nguồn từ phiếu giấy và sửa đổi ra sao nếu có nhu cầu lưu nguồn. Không tự lưu ảnh giấy chứa thông tin sức khỏe vào thư mục công khai. Với người không có CCCD, trẻ em và người giám hộ, cần thống nhất dữ liệu định danh có thể dùng; không cho seed demo quyết định chính sách thực tế.

Ưu tiên khảo sát đầu ra: phiếu khám/số thứ tự, phiếu thu, chỉ định xét nghiệm, kết quả và đơn thuốc. Cần xem bản in thật về tên cơ sở, patient, ngày giờ, người phụ trách, thuốc/cách dùng và số trang. In được bằng trình duyệt không đồng nghĩa chứng từ đáp ứng mọi yêu cầu chuyên môn/pháp lý; chưa nghiệm thu máy in trong phiên này.

### 18.3. Giờ làm việc và tình huống cuối ngày

Giữ giờ thực là đúng với yêu cầu nghiệm thu. Tuy nhiên, vận hành không nên mặc định toàn bệnh viện mãi chỉ có một giờ cứng. Cần cấu hình lịch cơ sở/khoa/buổi và chính sách ngày nghỉ theo nhu cầu đã xác nhận, trong khi backend vẫn chặn hành động ngoài điều kiện. Trường hợp đã bắt đầu khám trước đóng cửa nhưng còn Lab/kết luận sau giờ cần quyết định rõ; không tự nới bằng một flag “demo”.

Lịch seed kín 01/11–31/01 là để có dữ liệu chọn ngày, không chứng minh người thật làm việc liên tục không nghỉ hoặc capacity 40 là phù hợp. Demo quản lý tốt cần cả ca nghỉ, phòng đóng, buổi gần đầy, buổi đầy và phân công thiếu người có chủ đích.

## 19. Lộ trình cải thiện đề xuất

Ba hướng có thể chọn:

| Hướng | Lợi ích | Giới hạn |
|---|---|---|
| Chỉ chỉnh hình thức | Nhanh làm đồng đều spacing/màu/nhãn | Không giải quyết bàn giao mã, thiếu ngữ cảnh, scope và công việc chờ |
| **Hoàn thiện từng hành trình liên quầy** | Thấy giá trị sớm, giảm nhập lại, nghiệm thu được với nhiều role | Cần phối hợp frontend và backend, phải giữ contract/thẩm quyền |
| Mở rộng thành HIS/LIS/kho toàn diện ngay | Nhiều chức năng để trình diễn | Rất dễ quá tải đội nhỏ, nhiều chức năng hời hợt và tăng rủi ro |

**Khuyến nghị hướng thứ hai**, đồng thời sửa các vấn đề an toàn/quyền/dữ liệu ưu tiên cao. Các giai đoạn dưới là đề xuất để thảo luận, chưa phải cam kết lịch hoặc quyền mở rộng scope source.

### Giai đoạn 1 — ngăn nhầm và loại bỏ ngõ cụt

1. Thống nhất quyền/phạm vi Report, lịch, quản lý khoa; test manager không scope, ngoài scope, trưởng khoa, miễn nhiệm với token cũ.
2. Sửa trạng thái cũ sau nurse đăng ký; đồng bộ danh sách/số chỗ và lỗi sau lưu.
3. Hiện ngữ cảnh patient/lượt tại form chỉ định, nhập kết quả và xác nhận phát thuốc.
4. Read API tối thiểu cho tên khoa/bác sĩ phục vụ Lab/pharmacy; không mở quyền hồ sơ đầy đủ.
5. Bỏ jargon và sửa các tên/nhãn gây nhầm; báo cáo cũ không làm màn mặc định cho ngoại trú hiện tại.
6. Chốt và sửa BHYT/định danh với migration và tương thích dữ liệu; không vá riêng frontend.

**Cửa nghiệm thu:** Người test không nhầm patient/lượt, không thấy thành công cùng danh sách “chưa có”, không đọc/ghi ngoài scope, không phải bịa giấy tờ để tạo hồ sơ thuộc nhóm được hỗ trợ.

### Giai đoạn 2 — một hành trình không dùng sổ chép UUID

1. Bàn tiếp nhận theo patient và ngày, bước tiếp theo rõ.
2. Hàng khoản thu thống nhất Khám/Lab/Rx.
3. Bàn khám có hàng chờ, ngữ cảnh và kết quả cần xem.
4. Chỉ định chọn danh mục, tiến trình gửi được lưu bền vững.
5. Hàng Lab và quầy dược mặc định việc đang cần làm.
6. Lượt patient tổng hợp phiếu, kết quả, đơn và lời dặn theo quyền.

**Cửa nghiệm thu:** Ba hành trình mới/cũ khám mới/tái khám đi hết qua đúng account các quầy trong giờ thực. Không người nào phải mở devtools, Postman, database, ghi BN/PHIEU/HOSO/LAB/DON vào Notepad để hoàn thành công việc thông thường. Mã hỗ trợ vẫn có thể tra ở phần chi tiết.

### Giai đoạn 3 — làm sâu chuyên môn và quản lý khoa

1. Sinh hiệu, tiền sử/dị ứng/thuốc đang dùng và nội dung khám có cấu trúc phù hợp.
2. Mẫu xét nghiệm/chỉ số và vòng đời mẫu, duyệt/công bố/đính chính theo quy trình đã chốt.
3. Kê đơn có cấu trúc, đối chiếu cấp phát, tồn khả dụng; lô thuốc nếu cơ sở cần.
4. Lịch tuần có ca thiếu, sao chép/xem trước, nghỉ/đổi ca và ảnh hưởng tới người đã đăng ký.
5. Bảng điều hành khoa có điểm nghẽn, drill-down và người chịu trách nhiệm.
6. Sổ thu cuối ca, hoàn tiền từ nguồn và chứng từ.

**Cửa nghiệm thu:** Người dùng giải quyết được các tình huống thường gặp mà không nhờ dev sửa dữ liệu. Cảnh báo chuyên môn có nguồn và người chịu trách nhiệm, không làm đẹp bằng ngưỡng tùy ý.

### Giai đoạn 4 — tính sẵn sàng và phần khác biệt

Đo hiệu năng ở bản production và dữ liệu lớn; kiểm tra nhiều quầy cùng làm, mất phản hồi, khôi phục, backup/restore, quyền sau thu hồi, audit, in và thiết bị. Demo liên thông chậm có phục hồi, báo cáo chốt có căn cứ và giao diện quản trị theo dõi tác vụ lỗi. Giữ Surgery/Inpatient hoãn đúng phạm vi; bảo tồn mã mở rộng không có nghĩa hiện menu chưa hoạt động trong sản phẩm ngoại trú.

## 20. Backlog để chuyển thành công việc cụ thể

BE = Vinh; FE theo ownership hiện tại: Organization/Patient — Hoàng Anh, Pharmacy/Report — Huy, Clinical/Lab/Billing/Notification và shared được giao — Vinh. Bảng này đề xuất phối hợp, không tự cấp quyền sửa source frontend của người khác hoặc thay phân công đã chốt.

| ID | Ưu tiên | Kết quả cần bàn giao | Phần ảnh hưởng | Tiêu chí nghiệm thu chính |
|---|---|---|---|---|
| MF-01 | P1 | Một chính sách scope quản lý và báo cáo | BE Report/Core/Gateway; FE Report/Organization/shared | No-scope/ngoài-scope từ chối ở API; trưởng khoa chỉ đọc khoa được bổ nhiệm; thu hồi có hiệu lực |
| MF-02 | P1 | Nurse thấy đúng trạng thái ngay sau đăng ký | FE Appointment, API nếu cần | Thành công đồng thời cập nhật hàng phiếu và sức chứa; không kết quả đọc cũ đè mới |
| MF-03 | P1 | Hàng khoản thu Lab/Rx theo lượt | BE Billing + hợp đồng nguồn; FE Billing | Chọn patient/lượt tự giữ ID; đúng khoản, đúng số dư; không nhập lại mã đợt |
| MF-04 | P1 | Danh mục chỉ định và tiến trình gửi phục hồi được | BE Clinical/Lab/danh mục giá; FE Clinical | Rời/tải lại giữa hai bước tiếp tục đúng chỉ định, không tạo trùng |
| MF-05 | P1 | Nhận diện tại điểm thao tác rủi ro | FE shared/Lab/Pharmacy | Hai định danh + lượt + nội dung cần xác nhận nhìn thấy sát nút quyết định |
| MF-06 | P1 | Quy ước BHYT và định danh hợp lý | BE Core/Billing; FE Patient | Dữ liệu hợp lệ theo quy ước đã xác nhận; tương thích seed/cũ; không tự nhận đã xác minh quyền lợi |
| MF-07 | P1 | Hồ sơ ngoại trú đủ dữ liệu nền cần thiết | BE Clinical/Core; FE Clinical/Patient | Lưu/đọc/version dữ liệu quan trọng; nguồn và lịch sử rõ |
| MF-08 | P1 | Tên khoa/bác sĩ tối thiểu cho quầy chuyên môn | BE Core; FE reference/Pharmacy/Lab | Người được phép xem label phục vụ việc, không xem hồ sơ đầy đủ ngoài quyền |
| MF-09 | P1 | Tách điều hành và báo cáo chốt | BE Report; FE Report/Appointment | Chưa đủ coverage hiện unavailable; không biến thiếu thành 0; mặc định kỳ hợp lệ |
| MF-10 | P2 | Timeline và bước tiếp theo theo lượt/role | BE composition/read model; FE shared/features | Có nguồn/trạng thái/thời điểm, không suy trạng thái service này từ service khác |
| MF-11 | P2 | Tìm trước, tạo mới sau ở tiếp nhận | FE Patient/Appointment; BE Core nếu mở search | Không tạo trùng, phân biệt người trùng tên, giữ patient khi chuyển đăng ký |
| MF-12 | P2 | Giấy tờ/giám hộ theo nhóm patient được hỗ trợ | BE Core; FE Patient | Không dùng định danh giả; chính sách trẻ em/không giấy tờ được test |
| MF-13 | P2 | Tài khoản portal phụ trợ việc chăm sóc | FE Patient/Organization; BE Core | Đăng ký khám không phụ thuộc kích hoạt; tài khoản cũ không bị reset |
| MF-14 | P2 | Bàn khám hôm nay với kết quả cần xem | FE Appointment/Clinical/Lab; BE nguồn mốc | Hàng chờ đúng phòng/quyền, kết quả mới đến có việc xem, tránh mất bản nháp |
| MF-15 | P2 | Chẩn đoán từ danh mục có cấu trúc | BE Clinical/danh mục; FE Clinical | Tìm theo tên/mã, không ép bác sĩ thuộc mã, loại chẩn đoán đã chốt |
| MF-16 | P2 | Template Lab và vòng đời mẫu | BE Lab; FE Lab | Chỉ số đúng dịch vụ, mẫu truy vết được, lỗi/mẫu lại có trạng thái |
| MF-17 | P2 | Duyệt và đính chính kết quả | BE Lab/Notification; FE Lab/Patient | Công bố có người/thời điểm, sửa có version/lý do, người liên quan nhận cập nhật |
| MF-18 | P2 | Kê đơn cấu trúc và quầy phát thuốc | BE Pharmacy/Clinical; FE Pharmacy | Cách dùng rõ, lượng/tồn đủ điều kiện, không tự ý thay thuốc |
| MF-19 | P2 | Tồn khả dụng/lô và giao dịch kho theo nhu cầu | BE Pharmacy; FE Pharmacy | Sổ tồn đối chiếu được, không chỉ sửa số tổng; lô/hạn có truy vết nếu triển khai |
| MF-20 | P2 | Biên nhận và sổ cuối ca | BE Billing; FE Billing | Khoản thu/phương thức/người thu/hoàn khớp, in/xem lại được |
| MF-21 | P2 | Giải quyết ngoại lệ lịch đã có patient | BE Clinical/Core/Notification; FE Appointment | Nghỉ/đổi người/phòng có lý do, ảnh hưởng được xem trước, không vượt guard |
| MF-22 | P2 | Lịch mẫu, ca thiếu và công bố | BE scheduling; FE Appointment | Xem trước trùng, kết quả từng ca rõ, người nhận lịch biết thay đổi |
| MF-23 | P2 | Dashboard manager có điểm nghẽn | BE read model đúng nguồn; FE Appointment/Report | Chỉ số có định nghĩa và drill-down, phân biệt booked/arrived/waiting |
| MF-24 | P2 | Admin quản trị quyền ở nơi riêng | BE Core nếu cần; FE Organization/shared | Quyền/chức vụ/phạm vi tóm tắt rõ; lịch sử và tác động trước lưu |
| MF-25 | P2 | Giới hạn quyền chuyên môn của ADMIN | BE Clinical/Lab/Pharmacy; FE role/capability | Theo chính sách đã duyệt, hỗ trợ đặc biệt có audit; không suy admin là bác sĩ |
| MF-26 | P2 | Trang tổng hợp lượt của patient | FE Patient/Clinical/Lab/Notification; BE composition | Có bước tiếp, kết quả/đơn/lời dặn đúng quyền, không yêu cầu ID |
| MF-27 | P2 | Chuẩn hóa ngày, tiền, lỗi, nhãn | FE shared/features; BE error code | dd/MM/yyyy dễ đối chiếu, VND nhất quán, lỗi Việt hóa đúng hành động |
| MF-28 | P2 | Chốt typography, spacing, bảng, focus | FE shared + chủ từng service | Desktop thường không cần cuộn để thấy ngữ cảnh; trạng thái không bị bóp chữ |
| MF-29 | P2 | Kiểm thử tác vụ ở tải thực tế | FE/BE theo luồng | Số liệu p50/p95 và ma trận đồng thời, không dùng thời gian tool làm kết quả |
| MF-30 | P3 | Chế độ hỗ trợ liên thông cho admin | BE các nguồn lỗi; FE admin | Lỗi/correlation/audit theo quyền, thao tác recovery có quy trình |

Độ lớn các mục khác nhau; cần tách thành PR vừa đủ sau khi duyệt nghiệp vụ. Không hứa hoàn tất toàn bộ bằng một đợt chỉnh CSS hoặc một PR lớn.

## 21. Cách demo để người xem cảm thấy đây là sản phẩm nghiêm túc

### 21.1. Một câu chuyện thay vì đổi role điền form

“Bà Lan đến khám lần đầu, điều dưỡng xác minh và tạo hồ sơ; thu ngân nhận khoản phí ngay; bác sĩ chỉ thấy lượt đủ điều kiện; Lab nhận chỉ định và trả kết quả; bác sĩ đọc, kết luận và kê đơn; quầy thuốc biết điều kiện cấp phát; trưởng khoa nhìn ra điểm nghẽn; người bệnh xem kết quả khi có nhu cầu.”

Chỉ trình diễn câu chuyện này khi đã chạy xuyên suốt cùng lượt trong giờ thật. Phần hiện tại mới chứng minh trực tiếp đoạn đăng ký trước → thu khám → lịch doctor → phiếu/thông báo patient. Không nối ảnh seed của nhiều bệnh nhân thành một hành trình rồi gọi là end-to-end.

### 21.2. Bộ dữ liệu demo cần có chiều sâu

Giữ lịch kín theo yêu cầu, nhưng thêm nhóm tình huống tách biệt: buổi ít người, gần đầy, đầy, bệnh nhân trùng tên, người lớn/trẻ em nếu hỗ trợ, lượt chờ thu, đã đến, đang khám, chờ Lab, có kết quả chưa xem, chờ thuốc, thiếu tồn và một ca nghỉ ảnh hưởng lịch. Không cần hàng nghìn patient ngẫu nhiên nếu chưa dùng được 20 trường hợp có chủ đích.

Giữ nhãn “Dữ liệu mô phỏng” ở cấp môi trường/bản in. Các tên kỹ thuật `CL06_IN_EXAM_WITH_DIAGNOSIS`, thuốc “minh họa A”, phòng không dấu và tuổi/ngày sinh lặp lại làm dữ liệu thuận tiện cho kiểm thử nhưng giảm tính thuyết phục khi trình bày sản phẩm. Có thể giữ mã kỹ thuật trong fixture, còn nhãn UI dùng tên tổng hợp tự nhiên, không dùng hồ sơ người thật.

### 21.3. Tiêu chí đo thành công của buổi nghiệm thu tiếp theo

Các mục tiêu dưới là đề xuất thử nghiệm với ít nhất một người đại diện mỗi nhóm nhân viên, không phải số liệu đã đạt:

- Người test hoàn thành tác vụ chính mà không được chỉ vị trí nút hoặc ý nghĩa UUID.
- Patient cũ đi đến đăng ký nhanh hơn tạo hồ sơ mới; không đổi mật khẩu hoặc cấp lại account để khám đợt mới.
- Ba người đóng nurse/cashier/doctor làm trên phiên tách biệt; mỗi người nhận việc từ hệ thống thay vì đọc mã qua chat. Không dùng nhiều tab chung cookie để giả lập role độc lập.
- Không thu/đăng ký/chỉ định/phát lặp khi bấm lại sau mất phản hồi; kết quả được đối soát rõ.
- Người test nhận diện đúng người trùng tên bằng định danh thứ hai trước chỉ định/thu/cấp phát.
- Trưởng khoa chỉ ra được một buổi thiếu người hoặc quá tải và đi tới thao tác xử lý đúng quyền.
- Patient nói được bước tiếp theo và biết kết quả nào đã có, không cần hiểu tên phân hệ.
- Báo cáo có đủ nguồn/định nghĩa/đối chiếu; số chưa sẵn sàng không trình bày như số 0 chắc chắn.

## 22. Ma trận phạm vi review và nguồn source

### 22.1. Phạm vi màn hình

| Nhóm | Đã quan sát trực tiếp | Chưa thực hiện đến kết quả cuối |
|---|---|---|
| Đăng nhập/phiên/theme | Login/logout các role, sáng/tối một màn | Reset mật khẩu, session expiry, keyboard/screen reader toàn luồng |
| Patient/nurse | Danh sách, tạo form, tìm patient, chọn buổi, tạo đặt trước, chi tiết phiếu | Tạo patient/cấp account mới trong phiên, sàng lọc, check-in ngày thực, tái khám V2 |
| Clinical | Lịch doctor và phiếu mới, hồ sơ seed, chỉ định form, kê đơn form | Khám/hoàn tất cùng lượt mới trong giờ, gửi chỉ định mới, kê đơn mới |
| Billing | Hàng chờ, báo giá, thu khám mẫu, lookup Lab/Rx, trang hóa đơn/hoàn | Thu Lab/Rx cùng lượt, refund/đối soát cuối ca, in |
| Lab | Hàng đợi, chi tiết, mở nhập kết quả seed | Nhận mẫu/duyệt/công bố/đính chính, kết quả của lượt mới |
| Pharmacy | Danh mục, hàng đơn, chi tiết seed, xác nhận trước xuất | Xuất/giao thuốc, kho/lô, đổi/hủy/hoàn theo lượt mới |
| Patient portal | Phiếu/thông báo đúng lượt mới; màn kết quả trống | Đổi mật khẩu lần đầu, kết quả hoàn tất đúng lượt, mobile thật |
| Manager | Trang lịch, lỗi scope, báo cáo cũ và khoa trống scope ở admin | Ghi lịch manager đúng scope trong phiên này |
| Trưởng khoa | Source + ảnh/biên bản trước | Chuyển workspace/lưới hiện tại với dữ liệu nhiều ca trong phiên mới |
| Admin | Khoa/nhân sự, xem trước bổ nhiệm, tài khoản/form, scope đọc, report unavailable | Lưu bổ nhiệm/miễn nhiệm/khóa/cấp scope, thiết bị và vận hành |
| Nội trú/phẫu thuật | Ngoài phạm vi ngoại trú hiện hành | Không mở rộng hoặc nghiệm thu hai service hoãn |

Review bao phủ các vai trò và các họ chức năng chính, **không tuyên bố đã bấm mọi control, chạy mọi lỗi và nghiệm thu mọi chức năng**. Các mục chưa thử là backlog nghiệm thu rõ ràng, không phải ngầm xác nhận đạt.

### 22.2. Source đã đối chiếu cho nhận định quan trọng

Graph có metadata thay đổi ở các đường dẫn quan trọng, nên đọc source hiện tại trực tiếp thay vì coi kết quả index cũ là đầy đủ. Các link sau là bằng chứng cho nhận định source, không thay cho screenshot:

- [Sidebar và cách nhóm role](../../../frontend/src/components/layout/DashboardSidebar.tsx), [header](../../../frontend/src/components/layout/DashboardHeader.tsx), [màu và typography](../../../frontend/src/app/globals.css), [login/landing theo role](../../../frontend/src/app/login/LoginForm.tsx).
- [Tiếp nhận và danh sách sau tạo](../../../frontend/src/features/appointment/components/ReceptionWorkspace.tsx), [luồng ca bác sĩ](../../../frontend/src/features/appointment/components/DoctorWorkShiftsWorkspace.tsx), [tổng quan khoa](../../../frontend/src/features/appointment/components/DepartmentOperationsSummary.tsx), [workspace quản lý khoa](../../../frontend/src/features/appointment/components/DepartmentManagementWorkspace.tsx).
- [Form patient và BHYT](../../../frontend/src/features/patient/components/PatientForm.tsx), [cấp tài khoản patient](../../../frontend/src/features/organization/components/PatientAccountIssuance.tsx).
- [Hồ sơ khám](../../../frontend/src/features/medical-record/components/MedicalRecordDetail.tsx), [DTO nội dung khám](../../../frontend/src/features/medical-record/types.ts), [form chỉ định](../../../frontend/src/features/medical-record/components/ClinicalLabOrderForm.tsx), [trang gửi chỉ định hai bước](../../../frontend/src/app/(dashboard)/records/[recordId]/lab-order/LabOrderWorkspace.tsx).
- [Lookup thu Lab/Rx](../../../frontend/src/features/billing/components/PaymentRequestLookupForm.tsx), [thu khám và phục hồi](../../../frontend/src/features/billing/components/ScheduledPaymentWorkspace.tsx).
- [Hàng Lab](../../../frontend/src/features/lab/components/LabTable.tsx), [form kết quả](../../../frontend/src/features/lab/components/LabActions.tsx).
- [Landing pharmacy](../../../frontend/src/app/(dashboard)/pharmacy/page.tsx), [hàng đơn thuốc](../../../frontend/src/features/pharmacy/components/prescription/PrescriptionList.tsx), [editor thuốc](../../../frontend/src/features/pharmacy/components/prescription/PrescriptionLinesEditor.tsx), [giới hạn resolve tên](../../../frontend/src/features/pharmacy/components/PharmacyIdentity.tsx).
- [Reference directory theo role](../../../frontend/src/app/(dashboard)/_components/reference-directory.ts), [thông báo patient](../../../frontend/src/features/notification/components/NotificationLookup.tsx).
- [Report UI](../../../frontend/src/features/report/components/OutpatientReportView.tsx), [result/coverage](../../../frontend/src/features/report/components/OutpatientReportResult.tsx), [controller report cũ](../../../backend/report-service/src/main/java/com/mediflow/report/web/ReportController.java), [reader report cũ](../../../backend/report-service/src/main/java/com/mediflow/report/application/service/ReportApplicationService.java), [controller ngoại trú](../../../backend/report-service/src/main/java/com/mediflow/report/web/OutpatientReportController.java), [reader coverage](../../../backend/report-service/src/main/java/com/mediflow/report/application/service/OutpatientReportReadService.java).

### 22.3. Dọn môi trường sau review

Đã đóng các tab kiểm thử do agent mở, trả viewport về mặc định và theme về Sáng. Chạy `clean-docker.bat` thành công: dừng backend demo, dọn container và image Postgres/RabbitMQ thuộc phiên. Dừng frontend do phiên này khởi động. Kiểm tra không còn listener 3000/28080 và không còn container chạy. Không xóa dữ liệu dự án khác, không global prune.

Source ứng dụng chưa được sửa trong nhiệm vụ review. Các đề xuất phía trên cần được chọn phạm vi trước khi triển khai; không tự đánh dấu đóng các checklist nghiệm thu bệnh nhân còn thiếu.
