# Demo ngoại trú: thao tác UI từ tiếp nhận đến nhận thuốc

Ngày viết: 28/10/2026. Hướng dẫn đối chiếu source tại `e1a12d19` và seed
`prepare_ui_demo.py`. Đây là kịch bản thao tác, chưa là biên bản mọi bước đã được
nghiệm thu trên trình duyệt. Chỉ dùng dữ liệu tổng hợp trong môi trường demo cách ly.

## 1. Khởi động và đọc dữ liệu mẫu

1. Mở Docker Desktop, chờ Engine chạy.
2. Nhấp đúp `start-backend.bat` ở thư mục gốc. Chờ `READY`; giữ cửa sổ mở.
   File này build JAR, phục hồi snapshot, tạo phòng và chuẩn bị lịch qua Gateway.
3. Xem dòng `UI DATA:` trong cửa sổ backend, mở file `DEMO-DATA.local.md` tại đường
   dẫn đó. File ghi **tên khoa, phòng, tên và CCCD của hai bệnh nhân cũ, ngày có lịch**.
   Không cần tự điền UUID để tạo buổi khám.
4. Nhấp đúp `start-frontend.bat`, mở <http://127.0.0.1:3000/login>.

Seed tạo một buổi 07:00–17:00, 40 chỗ mỗi ngày cho hôm nay và ngày mai, cùng bác sĩ
`demo.doctor`. Sau 17:00 chỉ tạo lịch ngày mai. Có thể đăng ký trước cho ngày mai,
nhưng phải chờ đúng ngày/giờ mới bắt đầu khám được. Không đổi đồng hồ máy để lách điều kiện.

**Muốn demo trọn luồng, bắt đầu trong khoảng 07:00–16:00 giờ Việt Nam.** Seed chuẩn bị
lượt nguồn tái khám trong 07:00–16:45 để chừa thời gian trước khi đóng buổi.
Chỉ khi in `FOLLOW-UP SOURCE READY`, cả ba tình huống dưới đây mới có đủ dữ liệu nền.
Ngoài giờ, dòng `PARTIAL` có nghĩa chỉ chuẩn bị lịch, chưa chuẩn bị được lượt nguồn.
Nếu giữ stack qua đêm, chạy lại vào giờ khám:

```powershell
python scripts/demo_25_10/prepare_ui_demo.py
```

Stack tự dừng sau hai giờ; nếu đã dừng, chạy lại backend vào giờ khám. Lần chạy mới
phục hồi dữ liệu ban đầu; thông tin tạo trong lần demo trước không tự chuyển sang.
Chạy seed trên cùng stack dùng lại journal và các command đã xác nhận; không xóa
journal để thử lại một lỗi chưa rõ kết quả. Nếu đã có lịch do bạn lập gây trùng,
script báo lỗi; hãy dùng stack demo mới hoặc đối soát lịch, không sửa DB để vượt kiểm tra.

| Vai trò | Tên đăng nhập | Mật khẩu mẫu |
|---|---|---|
| Điều dưỡng | `demo.nurse` | `MediFlowDemo@2026` |
| Thu ngân | `demo.cashier` | `MediFlowDemo@2026` |
| Bác sĩ | `demo.doctor` | `MediFlowDemo@2026` |
| Kỹ thuật viên xét nghiệm | `demo.lab` | `MediFlowDemo@2026` |
| Dược sĩ | `demo.pharmacist` | `MediFlowDemo@2026` |
| Quản trị, khi cần kiểm lịch | `demo.admin` | `MediFlowDemo@2026` |
| Bệnh nhân cũ khám đợt mới | `demo.patient` | `MediFlowDemo@2026` |
| Bệnh nhân tái khám | `demo.patient2` | `MediFlowDemo@2026` |

Mỗi lần đổi vai trò, bấm đăng xuất rồi đăng nhập lại. Các tab cùng trình duyệt dùng
chung phiên; không coi mỗi tab là một tài khoản độc lập. Có thể dùng các hồ sơ trình
duyệt khác nhau để giữ từng vai trò. Hướng dẫn dưới đây cho phép đổi vai trò tuần tự.

## 2. Chọn một trong ba điểm bắt đầu

Ba trường hợp cùng dùng buổi đã seed. Không tạo ba hồ sơ cho cùng một người.
Sau khi đăng ký, mỗi trường hợp đều đi tiếp từ mục 3 đến mục 10.

### A. Bệnh nhân mới: tạo hồ sơ và cấp tài khoản bằng nurse

1. Đăng nhập `demo.nurse`.
2. Menu **Bệnh nhân** → **Thêm bệnh nhân** (`/patients/new`).
3. Nhập ví dụ: họ tên `Demo Nguyễn Lan 01`, ngày sinh `1995-01-01`, giới tính nữ,
   CCCD tổng hợp `990000000001`, điện thoại `0900000001`, địa chỉ `Dữ liệu demo`.
   Nếu CCCD đã tồn tại, tìm lại đúng hồ sơ hoặc dùng CCCD tổng hợp khác; không tạo trùng.
4. Bấm **Tạo bệnh nhân**. Tại trang chi tiết, xem thông báo đã tạo và khối
   **Tài khoản bệnh nhân và đăng ký khám**.
5. Bấm **Cấp tài khoản bệnh nhân**. Ghi lại tên đăng nhập trả về (CCCD), mật khẩu
   ban đầu `1`. Không đăng nhập bệnh nhân lúc này: nurse tiếp tục chăm sóc được ngay.
6. Bấm **Tiếp tục đăng ký khám**. Bệnh nhân đã được chọn sẵn tại trang tiếp nhận.
7. Thực hiện mục 3 với loại **Khám mới**.

Khối cấp tài khoản hiện chỉ xuất hiện ngay sau tạo hồ sơ (`?notice=created`). Giữ
trang này đến khi xong bước 6. Nếu rời trang, vẫn có thể mở **Tiếp nhận ca khám** và
tìm hồ sơ vừa tạo để đặt lịch; không tạo lại bệnh nhân. Đây là giới hạn UX hiện tại.

### B. Bệnh nhân cũ đến khám đợt mới

1. Đăng nhập `demo.nurse` → **Bệnh nhân**, tìm CCCD của `demo.patient` ghi trong file
   `DEMO-DATA.local.md`; kiểm họ tên/ngày sinh để xác nhận đúng người.
2. Không tạo hồ sơ, không cấp lại tài khoản và không đặt lại mật khẩu.
3. Menu **Tiếp nhận ca khám** (`/appointments/reception`).
4. Thực hiện mục 3; chọn người này và loại **Khám mới**. “Khám mới” là một lượt mới,
   không có nghĩa phải là bệnh nhân lần đầu tới bệnh viện.

### C. Bệnh nhân tái khám

1. Kiểm file dữ liệu có `historyReady=true` / **Có lượt nguồn tái khám hợp lệ**.
   Seed đã tạo, thu phí khám và hoàn tất **một lượt nền V2** của `demo.patient2`
   qua API; chưa đăng ký lượt tái khám bạn sẽ thao tác.
2. Đăng nhập `demo.nurse` → **Tiếp nhận ca khám**.
3. Chọn khoa và buổi cùng bác sĩ được ghi trong file dữ liệu.
4. Chọn bệnh nhân của `demo.patient2`, đổi **Loại lượt khám** sang **Tái khám**.
5. Bấm nút tải/tìm lượt khám trước; chọn lượt đã hoàn tất có chẩn đoán dữ liệu nền.
   Dùng đề xuất tái khám của bác sĩ. Khoảng khuyến nghị là ngày seed đến bảy ngày sau.
6. Giữ cùng bác sĩ cho lần demo đầu. Nếu đổi bác sĩ, phải nhập lý do và xác nhận
   bệnh nhân đồng ý; lịch bác sĩ khác chưa nằm trong seed này.
7. Tiếp tục đăng ký như mục 3. Giá trước hỗ trợ 80.000, người bệnh trả 40.000 VND.

Lượt nền là dữ liệu mô phỏng để có nguồn hợp lệ, không phải bằng chứng người bệnh
đã quay lại vào một ngày khác. Demo tái khám ngày hôm sau cần chạy đúng ngày đó.

## 3. NURSE — chọn buổi và đăng ký tại quầy

1. Ở **Chọn buổi khám**, tại ô **Khoa**, nhập tên khoa trong file dữ liệu → bấm
   **Tìm** → chọn kết quả. Gõ chữ vào ô chưa đồng nghĩa đã chọn khoa.
2. **Từ ngày**, **Đến ngày**: cùng ngày hôm nay → **Tải buổi khám**.
3. Chọn thẻ 07:00–17:00 của đúng bác sĩ, trạng thái mở/còn chỗ → **Chọn buổi**.
4. Nếu chưa có bệnh nhân sẵn: tại **Bệnh nhân**, tìm CCCD/họ tên → **Tìm** → chọn
   đúng kết quả. Chọn **Khám mới** hoặc **Tái khám** theo mục 2.
5. **Hình thức tiếp nhận**: chọn tại quầy (`AT_COUNTER`); nhập ghi chú nếu cần.
6. Bấm **Đăng ký và giữ chỗ**. Kỳ vọng **Đang chờ thu tiền**, chưa có số thứ tự.
7. Ghi lại **mã phiếu đăng ký** cho thu ngân; đó cũng là mã lượt/đợt chăm sóc dùng
   để đối chiếu các bước sau. Lưu thêm mã bệnh nhân khi cần tra cứu phí Lab/thuốc.

Tại quầy đã ghi nhận có mặt; không bấm tiếp nhận kiểu lịch cũ. Nếu chọn đăng ký trước
(`PREBOOKED`), sau thanh toán nurse cần tải phiếu trong buổi và **Ghi nhận đã đến**
khi khách tới. Không khám trước giờ/ngày đã hẹn.

Giữ suất và báo giá có thời hạn. Chuyển sang thu ngân ngay; không để phiếu chờ quá lâu
trong lúc thuyết trình. Nếu hết hạn, đọc trạng thái thực tế và đăng ký lại theo luồng,
không gửi một khoản thu mới khi giao dịch cũ chưa rõ kết quả.

## 4. CASHIER — thu phí khám

1. Đăng nhập `demo.cashier` → menu **Thu phí lịch khám** (`/billing/scheduled-payments`).
2. Chọn đúng phiếu trong danh sách. Nếu chưa thấy, tải lại; hoặc mở **Tra cứu phiếu
   được cung cấp**, dán mã phiếu ở mục 3 → **Tra cứu khoản thu**.
3. Kiểm đúng bệnh nhân, báo giá và số tiền: khám mới 100.000 − 50.000 hỗ trợ =
   **50.000 VND**; tái khám 80.000 − 40.000 = **40.000 VND**.
4. Chọn hình thức tiền mặt, nhập số tiền đúng báo giá nếu form yêu cầu → **Thu phí lịch khám**.
5. Chờ giao dịch hoàn tất. Nếu đang xử lý, dùng **Kiểm tra trạng thái giao dịch**;
   không tạo yêu cầu thu mới. Bấm **Cập nhật số dư** khi cần.
6. Kỳ vọng phiếu xác nhận, có số thứ tự và đủ điều kiện khám trong giờ buổi khám.

Hỗ trợ BHYT là mô phỏng theo cấu hình; phần hỗ trợ không phải số tiền thu tiền mặt.
Lượt nền tái khám do seed chuẩn bị đã có một khoản thu riêng: không cộng khoản đó
vào số tiền của bệnh nhân đang demo.

## 5. DOCTOR — nhận bệnh nhân, khám và chỉ định Lab

1. Đăng nhập `demo.doctor` → **Lịch khám của tôi** (`/appointments/my-calendar`).
2. Chọn ngày demo → **Tải lịch của tôi** → **Xem phiếu** ở buổi 07:00–17:00.
3. Tìm đúng bệnh nhân/số thứ tự → **Xem phiếu khám** → **Cập nhật trạng thái** nếu cần.
4. Bấm **Bắt đầu khám**, xác nhận trong khối xác nhận. Chờ trạng thái đang khám.
5. Bấm **Mở hồ sơ khám**. Ghi lại mã hồ sơ trên URL `/records/<mã>` và mã lượt.
6. Bấm **Sửa triệu chứng**, nhập triệu chứng mô phỏng, lưu thay đổi và quay lại hồ sơ.
7. Trong **Chẩn đoán**, nhập `Theo dõi sức khỏe — dữ liệu demo`, ICD `Z00`, thêm chẩn đoán.
8. Bấm **Chỉ định xét nghiệm**. Trang có hai bước; hoàn thành cả hai trước khi rời:
   - **Loại xét nghiệm**: `CBC`; **Mã giá**: `LAB_CBC`; **Ngày yêu cầu**: hôm nay → **Lập chỉ định**.
   - Khối gửi yêu cầu xét nghiệm hiện ra với dữ liệu đã gắn hồ sơ → **Tạo yêu cầu từ chỉ định**
     → **Mở xét nghiệm**. Ghi lại mã xét nghiệm trên URL `/lab/<mã>`.
9. Không hoàn tất hồ sơ khi xét nghiệm đang chờ. Giữ nguyên lượt khám để bác sĩ đọc
   kết quả; không tạo lượt khám thứ hai chỉ để trả kết quả.

## 6. CASHIER — thu phí xét nghiệm trước khi Lab thực hiện

1. Đăng nhập `demo.cashier` → **Viện phí** → phần tra cứu khoản phí
   (`/billing/payment-requests`).
2. **Loại khoản phí**: xét nghiệm (`LAB_TEST`). Nhập **Mã xét nghiệm**, chọn đúng
   **Mã bệnh nhân**, nhập **Mã đợt điều trị** = mã phiếu đăng ký ở mục 3.
3. **Tra cứu khoản phí**. Nếu sự kiện chưa tới Billing, chờ vài giây rồi tra cứu lại.
4. Kiểm báo giá CBC: 120.000, hỗ trợ 84.000, người bệnh trả **36.000 VND**.
5. Chọn tiền mặt → **Xác nhận thu theo báo giá**. Chờ xác nhận thành công.
   Không dùng luồng hóa đơn cũ hoặc tự tạo khoản tiền để thay cho khoản Lab này.

Màn hình tra cứu Lab/thuốc còn cần các mã nguồn và mã lượt; đây là thao tác UI hiện
có, chưa phải trải nghiệm bàn giao tự động giữa các quầy.

## 7. LAB_TECH — thực hiện và trả kết quả

1. Đăng nhập `demo.lab` → **Xét nghiệm** (`/lab`). Tìm/chọn yêu cầu CBC đúng bệnh nhân;
   nếu cần, mở trực tiếp `/lab/<mã xét nghiệm>` đã ghi ở mục 5.
2. Tải lại để nhận xác nhận tài chính → **Bắt đầu xét nghiệm**.
   Trước khi thu tiền, thao tác phải bị chặn; không chọn chế độ cấp cứu để bỏ qua thu.
3. Bấm **Nhập kết quả**. Nhập dữ liệu minh họa:

   | Chỉ số | Giá trị | Đơn vị | Khoảng tham chiếu |
   |---|---|---|---|
   | WBC | 7.0 | G/L | 4–10 |

4. Ngày thực hiện hôm nay; kết luận `Kết quả mô phỏng phục vụ demo`.
   Bấm **Lưu kết quả**. Kỳ vọng trạng thái hoàn tất và thấy bảng kết quả.

## 8. DOCTOR — đọc kết quả, kê đơn và hoàn tất khám

1. Đăng nhập `demo.doctor`, mở lại hồ sơ ở mục 5 → **Cập nhật hồ sơ**.
   Xem kết quả Lab/đính kèm; nếu chưa xuất hiện, đợi sự kiện đồng bộ rồi tải lại.
2. Vào **Dược** → **Đơn thuốc** → tạo đơn (`/pharmacy/prescriptions/new`).
3. Chọn đúng bệnh nhân, chọn **Hồ sơ khám** đang dùng ở mục 5.
   Hệ thống tự xác nhận bác sĩ, khoa, lượt khám và mã giá; không tự chế UUID hoặc mã giá.
4. Ngày kê đơn hôm nay. Trong dòng thuốc, tìm **DEMO-HIST-2025 — Thuốc minh họa A**,
   kiểm còn tồn và hạn 2030, số lượng `2`; cách dùng `Chỉ là dữ liệu demo, không dùng điều trị`.
5. Bấm **Tạo đơn thuốc**; ghi lại mã đơn từ URL/trang chi tiết. Giá mẫu thuốc A là
   10.000/viên, hai viên là **20.000 VND**, không có hỗ trợ thuốc theo policy demo.
   Luôn kiểm số tiền thực tế hệ thống trả về nếu catalogue đã được thay đổi.
6. Trở lại hồ sơ khám. Chọn hướng xử trí ngoại trú phù hợp, ghi kết luận; tích
   **Tôi đã kiểm tra chẩn đoán, chỉ định và xác nhận hoàn tất lần khám** → **Hoàn tất hồ sơ khám**.
7. Nếu chọn hướng tái khám, tại **Đề xuất tái khám** sau hoàn tất: nhập từ ngày mai
   đến bảy ngày sau, lý do, hướng dẫn → **Tạo đề xuất tái khám**. Đề xuất không tự đặt lịch.

Thứ tự demo ở đây là kê đơn khi hồ sơ còn mở, rồi hoàn tất sau khi đọc kết quả.
Nếu tạo đơn bị chặn, giữ nguyên hồ sơ và đọc lỗi, không chọn hồ sơ lịch sử thay thế.

## 9. CASHIER rồi PHARMACIST — thanh toán và nhận thuốc

1. Đăng nhập `demo.cashier` → `/billing/payment-requests`.
2. **Loại khoản phí**: đơn thuốc (`PRESCRIPTION`); nhập mã đơn vừa tạo, chọn đúng
   bệnh nhân, **Mã đợt điều trị** = mã phiếu mục 3 → **Tra cứu khoản phí**.
3. Kiểm số còn phải thu, nhập đủ **20.000 VND** với catalogue mẫu và chọn tiền mặt
   trong form thu sổ cái → **Xác nhận thanh toán**. Đối chiếu kết quả giao dịch trước khi chuyển quầy.
4. Đăng nhập `demo.pharmacist` → **Dược** → **Đơn thuốc** (`/pharmacy/prescriptions`).
5. Tìm/chọn đúng bệnh nhân và đơn vừa kê. Mở chi tiết → **Tải lại trạng thái** để nhận
   xác nhận đã thanh toán.
6. Bấm **Xuất thuốc** → kiểm đơn/số lượng → **Xác nhận xuất thuốc**.
7. Kỳ vọng có phiếu xuất, trạng thái cấp phát hoàn tất, tồn thuốc A giảm đúng 2.
   Bàn giao thuốc minh họa và hướng dẫn cho bệnh nhân. Không xuất hai lần cùng đơn.

Tổng người bệnh trả cho kịch bản khám mới + CBC + hai thuốc A: **106.000 VND**.
Với tái khám cùng xét nghiệm/thuốc: **96.000 VND**. Đây là tổng ba khoản riêng;
không thu thêm một hóa đơn tổng để tránh thu trùng.

## 10. PATIENT — tùy chọn đăng nhập xem kết quả và thông báo

1. Với bệnh nhân mới: đăng nhập bằng CCCD đã cấp, mật khẩu `1`.
2. Làm theo màn hình đổi mật khẩu bắt buộc, nhập mật khẩu mới đáp ứng yêu cầu;
   đăng nhập lại bằng mật khẩu mới khi hệ thống yêu cầu.
3. Menu **Kết quả khám của tôi** (`/records/mine`): xem chẩn đoán/kết luận của hồ sơ đã hoàn tất.
4. Menu **Kết quả xét nghiệm** (`/lab/mine`): xem CBC đã trả kết quả.
5. **Thông báo** (`/notifications`): kiểm thông báo kết quả, khám và lời hẹn tương ứng.
6. **Phiếu khám của tôi**: xem phiếu/đề xuất tái khám. Khi muốn đặt buổi tái khám,
   liên hệ nurse; không tạo lại hồ sơ hay cấp lại tài khoản.

Với bệnh nhân cũ, dùng tài khoản `demo.patient` / `demo.patient2`, không cấp lại.
Không bắt bệnh nhân đăng nhập để nurse đặt lịch, bác sĩ khám, trả kết quả hoặc cấp thuốc.

## 11. Các dấu hiệu cần dừng để đối soát

| Hiện tượng | Kiểm tra/thao tác tiếp theo |
|---|---|
| Không có lịch hôm nay | Xem ngày trong DEMO-DATA; sau 17:00 chỉ có lịch ngày mai. |
| Có chữ tên khoa nhưng không tải được | Bấm Tìm và chọn kết quả để có lựa chọn thực sự. |
| Tái khám không có lượt nguồn | Kiểm historyReady và đúng bệnh nhân demo.patient2; lịch sử cũ không tự thành lượt V2 hợp lệ. |
| Bác sĩ chưa thấy Bắt đầu khám | Kiểm đã thu, đúng bác sĩ, đã ghi nhận đến, đúng giờ và còn hiệu lực. |
| Lab chưa làm được dù đã thu | Tải lại trạng thái để chờ clearance qua broker; không thu lại. |
| Thuốc chưa xuất được | Kiểm đúng đơn đã thu, sự kiện tài chính, tồn kho và hạn dùng. |
| Timeout sau thao tác ghi | Dùng kiểm tra trạng thái/gửi lại cùng yêu cầu nếu UI cung cấp; không tạo lệnh mới. |
| Quầy Lab/thuốc phải nhập mã thủ công | Là giới hạn UX hiện có; giữ bảng mã bệnh nhân, lượt, hồ sơ, xét nghiệm, đơn để bàn giao. |

Ghi lại bước, vai trò, thời điểm, thông báo lỗi và mã theo dõi khi đánh giá UX. Các
trạng thái đạt từ seed/API không thay cho kiểm thử click trên UI của ba developer.
