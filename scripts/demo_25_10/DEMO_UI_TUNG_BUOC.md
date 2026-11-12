# Demo UI theo service: admin → tiếp nhận → khám → Lab → thuốc → ra về

Cập nhật **12/11/2026** (tiếp nhận khoa v2, Lab v2 duyệt hai người). Chỉ dùng dữ liệu
mẫu. Toàn bộ dữ liệu do `scripts/run/start-backend.bat` tự chuẩn bị; **không chạy file
Python hay SQL nào** trước hoặc giữa các quầy.

Phân công dưới đây là **phân công trình bày**, không thay đổi quyền sửa source.
Người trình bày có thể đăng nhập nhiều vai nghiệp vụ; ví dụ Huy trình bày Pharmacy
nhưng phải dùng vai DOCTOR khi tạo đơn, PHARMACIST khi xuất thuốc.
Bảng dưới đối chiếu với [bộ slide HTML](../../artifacts/service-demo/index.html).

## 0. Thứ tự trình bày và bàn giao

| Thứ tự | Người demo | Service / nội dung | Mục thao tác | Slide HTML |
|---|---|---|---|---|
| 1 | Hoàng Anh | Gateway, port hệ thống; Organization, khoa/nhân sự/tài khoản | 2 | 03–04 |
| 2 | Hoàng Anh | Patient: tìm/tạo hồ sơ, cấp account nếu cần | 3 | 05 |
| 3 | Vinh | Clinical: tiếp nhận, đăng ký và giữ chỗ | 4 | 06 |
| 4 | Lộc | Billing: thu phí khám | 5 | 07 |
| 4b | Vinh | Clinical: quầy điều dưỡng khoa cấp số phòng, ghi sinh hiệu | 5b | 07 |
| 5 | Vinh | Clinical: khám, chẩn đoán, chỉ định và tạo phiếu Lab | 6 | 08–09 |
| 6 | Lộc | Billing: thu phí Lab | 7 | 10 |
| 7 | Vinh | Lab: thực hiện, trả kết quả; Clinical: đọc lại kết quả | 8–9 | 11 |
| 8 | Huy | Pharmacy: tạo đơn từ hồ sơ đang mở | 10 | 12 |
| 9 | Lộc | Billing: thu tiền thuốc | 11 | 13 |
| 10 | Huy | Pharmacy: xuất thuốc | 12 | 14 |
| 11 | Vinh | Clinical: hoàn tất hồ sơ, hướng dẫn ra về | 13 | 15 |
| 12 | Lộc | Notification: thông báo; PATIENT xem kết quả chính chủ | 14 | 16 |
| 13 | Huy | Report: kỳ đã đối soát, báo cáo cuối luồng | 15 | 17 |

Chuẩn bị ở mục 1 trước khi lên trình bày. Chạy **một bệnh nhân A** xuyên suốt;
B/C và quản lý lịch nâng cao là nhánh bổ sung, thực hiện sau lượt chính.
Không gom ba lần thu của Lộc thành một đoạn cuối, không hoàn tất hồ sơ trước phần
kê đơn. Trong kịch bản này, bước hoàn tất hồ sơ đặt sau cấp thuốc để khớp bàn giao
trên slide; đây là thứ tự thuyết trình, không khẳng định mọi hệ thống bắt buộc cấp
thuốc trước khi bác sĩ có thể hoàn tất chuyên môn.

Tại mỗi bước ghi dữ liệu: chỉ rõ **bấm nút nào → service nào xử lý → event gì →
consumer/kết quả nào**. Event gửi vào exchange `mediflow.events`; queue nhận theo
binding thực tế. Gateway định tuyến và thao tác chỉ đọc không tự tạo domain event.
Queue rỗng có thể do consumer đã xử lý, không phải bằng chứng không phát event.

## 1. Chuẩn bị trước buổi demo

### 1.1. Khởi động

1. Mở Docker Desktop, chờ Engine hoạt động.
2. Nhấp đúp **start-backend.bat** trong thư mục **scripts/run**. Chờ **CALENDAR READY** rồi **READY**.
   Lần đầu cần thêm vài phút để chuẩn bị nhân sự và lịch. Giữ cửa sổ backend mở.
3. Muốn demo đủ cả tái khám, khởi động backend **trong 07:00–16:45 giờ Việt Nam** và
   kiểm dòng **FOLLOW-UP SOURCE READY**. Nếu hiện **PARTIAL**, vẫn demo tạo hồ sơ,
   chọn lịch được nhưng chưa có lượt nguồn tái khám.
4. Nhấp đúp **scripts/run/start-frontend.bat**. Mở **http://127.0.0.1:3000/login**.
5. Tùy chọn app mobile: sau READY nhấp đúp **scripts/run/start-mobile.bat** (mặc định
   Chrome; `-Target android` cho emulator, `-Target device` cho điện thoại thật).

Launcher tự build, khôi phục dữ liệu, tạo lịch, tài khoản, quyền phát hành Lab cho hai
KTV, kỳ báo cáo và bật cờ UI tiếp nhận khoa. Không cần chạy thêm lệnh nào.

Không chạy lại backend giữa các quầy: một lần khởi động mới phục hồi lại dữ liệu,
làm mất lượt bạn vừa thao tác. Phiên demo tự dừng sau hai giờ.

Nếu một trang vừa build được nhưng phiên dev cũ trả **404**, dừng riêng frontend,
chuyển thư mục `frontend/.next/dev` sang một thư mục lưu tạm dưới `target/` ở gốc
dự án, rồi chạy lại **scripts/run/start-frontend.bat** và tải lại trang. Không khởi động lại
backend để xử lý cache frontend, vì sẽ mất lượt đang demo. Đây là tình huống cache
đã quan sát khi nghiệm thu 02/11, không phải cách bỏ qua lỗi quyền hoặc lỗi nghiệp vụ.

### 1.2. Ngày và lịch dùng cho luồng chính

**Ngày demo D là ngày thực tế thao tác; ví dụ 12/11/2026, bắt đầu khoảng 08:00, hoàn tất trước 16:00.**

| Thông tin | Chọn/nhập chính xác |
|---|---|
| Ngày demo, viết tắt **D** trong các bước dưới | **12/11/2026** |
| Khoa | **Khoa Nội tổng quát Demo (DEMO-NOI)** |
| Bác sĩ | **Lê Bác Sĩ Demo** |
| Tài khoản bác sĩ | **demo.staff03@example.test** |
| Phòng | **Phong kham ngoai tru demo V2** |
| Buổi khám | **07:00–17:00**, đúng ngày D |
| Hình thức tiếp nhận khi demo đúng ngày, bệnh nhân có mặt | **Đang có mặt tại quầy** |

**Nếu bạn demo ngày khác:** D phải là **ngày thực tế bạn đang thao tác**, không giữ
ngày minh họa khi ngày đó đã qua. Khởi động backend vào buổi sáng ngày demo; lịch riêng
của `demo.staff03@example.test` được chuẩn bị cho ngày chạy. Tất cả ô ngày khám, ngày yêu cầu Lab,
ngày thực hiện Lab và ngày kê đơn trong luồng chính đều dùng **cùng D**.

Nếu chuẩn bị vào buổi tối: chỉ đăng ký trước cho ngày kế tiếp; chưa thể khám
ngay. Muốn demo liên tục đến nhận thuốc, khởi động phiên trong giờ khám ngày D;
không đổi giờ máy.

### 1.3. Tài khoản sẽ dùng

Mật khẩu seed mẫu trong bảng (nếu chưa đổi trong phiên): **`MediFlowDemo@2026`**.

| Lượt thao tác | Đăng nhập |
|---|---|
| Tạo hồ sơ, cấp tài khoản, đăng ký khám, cấp số phòng | `demo.staff04@example.test` |
| Thu phí khám, xét nghiệm và thuốc | `demo.staff19@example.test` |
| Khám, chỉ định, đọc kết quả, kê đơn | `demo.staff03@example.test` |
| Thực hiện xét nghiệm, nhập kết quả | `demo.staff15@example.test` |
| KTV thứ hai: phát hành kết quả (mục 8) | `demo.staff16@example.test` |
| Xuất thuốc | `demo.staff17@example.test` |
| Bệnh nhân cũ Nguyễn Văn An Demo xem kết quả | `demo.patient01@example.test` |
| Bệnh nhân tái khám Trần Thị Bình Demo xem kết quả | `demo.patient02@example.test` |
| Mở đầu quản trị, báo cáo; bổ nhiệm nếu trình bày nhánh phụ | `demo.staff01@example.test` |

Mỗi lần đổi người: **Đăng xuất → nhập tài khoản trong bảng → Đăng nhập**.
Các tab cùng trình duyệt dùng chung phiên; không giữ nhiều vai trò bằng nhiều tab.
Trên cửa sổ hẹp, bấm nút ba gạch **Mở điều hướng** để thấy menu.

### 1.4. Chọn một bệnh nhân cho mỗi lần chạy

| Tình huống | Tên cần tìm/nhập | CCCD | Ngày sinh | Tài khoản bệnh nhân |
|---|---|---|---|---|
| A — mới đến, bạn tự tạo | **Demo Nguyễn Lan 01** | `990000000001` | **01/01/1995** | Cấp sau khi tạo, đăng nhập bằng email của hồ sơ |
| B — bệnh nhân cũ khám đợt mới | **Nguyễn Văn An Demo** | `999881000001` | **12/04/1986** | `demo.patient01@example.test` |
| C — tái khám | **Trần Thị Bình Demo** | `999881000002` | **12/04/1987** | `demo.patient02@example.test` |

**Lượt chính chạy A đến hết mục 15.** Sau đó chạy B và C, mỗi trường hợp một lượt
riêng. Không chạy cả ba đăng ký trước rồi để phiếu chờ thu quá lâu.

### 1.5. Các mã cần giữ trong lúc đổi quầy

Hàng khoản thu mới tự giữ mã nguồn và lượt: **không cần chép BN/PHIEU/LAB/DON để thu
Lab hoặc thuốc**. Giữ URL hồ sơ, xét nghiệm và đơn để mở lại khi đổi quầy. Bảng mã
dưới phục vụ đối soát/hỗ trợ; các mã do UI sinh trong phiên, không dùng UUID trong ảnh cũ.

| Ghi chú của bạn | Lấy ở đâu | Dùng ở đâu |
|---|---|---|
| **BN** — mã bệnh nhân | URL trang Chi tiết bệnh nhân: phần sau `/patients/`; Lab cũng có thông tin bàn giao | Đối chiếu đúng người; ô chọn bệnh nhân vẫn tìm được bằng CCCD |
| **PHIEU** — mã phiếu đăng ký | Sau đăng ký, ô **Mã phiếu bàn giao thu ngân** | Thu khám; **Mã đợt điều trị** khi thu Lab/thuốc |
| **HOSO** — mã hồ sơ khám | Bác sĩ bấm **Mở hồ sơ khám**; phần sau `/records/` trên URL | Quay lại đọc kết quả, hoàn tất khám và chọn đúng hồ sơ kê đơn |
| **LAB** — mã xét nghiệm | Sau **Mở xét nghiệm**, phần sau `/lab/`; khối **Thông tin bàn giao thu ngân** | Thu Lab và mở lại đúng xét nghiệm |
| **DON** — mã đơn thuốc | Sau **Tạo đơn thuốc**, phần sau `/pharmacy/prescriptions/` | Thu thuốc và mở đơn để cấp phát |

Ví dụ ghi: `A / Demo Nguyễn Lan 01 / BN=… / PHIEU=… / HOSO=… / LAB=… / DON=…`.
Giữ **PHIEU**, **HOSO**, **LAB**, **DON** riêng; chúng không phải cùng một mã.

### 1.6. Kiểm tra trước phần Report và account mới

- Đăng nhập bằng **email seed**, không dùng username cũ như `demo.nurse`/`demo.doctor`.
  [Runbook tài khoản email](../../docs/plans/20-email-account-demo-runbook-2026-11-04.md)
  là nguồn hướng dẫn cho cấp account, mật khẩu tạm và hộp thư demo.
- Kiểm `/reports/outpatient` bằng ADMIN trước buổi demo: UI, consumer/read và kỳ
  đã đối soát phải sẵn sàng. Chưa có kỳ thì ghi nhận chưa sẵn sàng, không cam kết
  số liệu lượt vừa thao tác sẽ tự xuất hiện trên snapshot đã phát hành.
- Nếu chọn A và muốn đăng nhập PATIENT cuối luồng, dùng email nhận thư mà người
  demo kiểm soát và chuẩn bị nhận mật khẩu tạm. Địa chỉ `example.test` chỉ là mẫu;
  không coi là hộp thư thật. Hộp thư demo chỉ hiển thị account seed được xác minh,
  không mặc định hiển thị account A vừa tạo.
- Dùng URL frontend/Gateway mà launcher in. Gateway Compose mặc định `8080`,
  launcher demo dùng `28080`; không sửa frontend để gọi trực tiếp port service.

## 2. HOÀNG ANH — Gateway và Organization mở đầu

**Người trình bày: Hoàng Anh · Vai UI: ADMIN.**

### 2.1. Gateway và Organization trước khi tiếp nhận

1. Mở slide Gateway, giới thiệu Web `3000` → Gateway `8080` (hoặc `28080` trong
   launcher demo) → Core `8089`, Clinical `8082`, Lab `8084`, Pharmacy `8085`,
   Billing `8086`, Support `8087`; discovery Eureka `8761`.
2. Core chứa Organization/Patient; Support chứa Report/Notification, vẫn tách
   store theo context. RabbitMQ AMQP `5672`, UI quản trị `15672` trong Compose
   mặc định; nếu phiên demo ánh xạ khác thì dùng port thực tế của phiên.
3. Đăng nhập ADMIN → **Tổ chức**, xem **Khoa Nội tổng quát Demo**, nhân viên
   **Lê Bác Sĩ Demo** và tài khoản tương ứng. Giữ nguyên khoa/bác sĩ của lượt chính.
4. Mở Network của trình duyệt, chỉ một request đọc qua `/api/*` và Gateway.
   Việc đọc/định tuyến **không phát domain event**.
5. Nếu demo thêm thao tác tạo khoa/nhân viên, dùng danh mục riêng không thay
   dữ liệu của lượt chính: lưu khoa → `department.created`, tạo nhân viên →
   `staff.created`, chuyển khoa nhân viên → `staff.department.changed`.
   Không khẳng định thao tác bổ nhiệm bên dưới phát các event này.

### 2.2. Tùy chọn: bổ nhiệm/miễn nhiệm trên UI

Nhánh bổ nhiệm dưới đây tùy chọn, không phải điều kiện để khám. Nếu cần rút ngắn, chỉ xem dữ liệu khoa/nhân sự/tài khoản đã seed rồi chuyển mục 3.

1. **demo.staff01@example.test / MediFlowDemo@2026 → Tổ chức → Khoa Nội tổng quát Demo**.
2. Tại **Bổ nhiệm trưởng khoa**, chọn **Lê Bác Sĩ Demo · Bác sĩ · Khoa Nội tổng quát Demo**.
3. **Lý do**: `Demo bổ nhiệm trưởng khoa Nội` →
   **Xem trước bổ nhiệm → Xác nhận bổ nhiệm**.
4. Kiểm thông báo thành công, khối **Trưởng khoa** cũng hiện **Lê Bác Sĩ Demo**,
   mở **Lịch sử bổ nhiệm** để xem giờ Việt Nam.
5. Đăng xuất → **demo.staff03@example.test / MediFlowDemo@2026 → Sang Quản lý khoa**.
   **Về Khám bệnh** quay lại lịch cá nhân, cùng tài khoản.
6. Muốn miễn nhiệm: quay lại **demo.staff01@example.test**, mở cùng khoa, nhập lý do
   `Demo kết thúc nhiệm kỳ, vẫn giữ chuyên môn bác sĩ` →
   **Miễn nhiệm → Xác nhận miễn nhiệm**.
7. Đăng nhập **demo.staff03@example.test**: còn chức năng bác sĩ, không còn nút quản lý khoa.
   Bổ nhiệm lại nếu muốn thử mục 18.

Nếu thử thay người nhận nhiệm kỳ, chỉ chọn nhân viên và tài khoản đã được xác
minh trong phiên. Không giả định bác sĩ luân phiên có account đăng nhập: script
calendar hiện chỉ tạo nhân sự/lịch, không cấp account cho các bác sĩ này.

## 3. HOÀNG ANH / NURSE — Patient: chọn điểm bắt đầu A, B hoặc C

**Người trình bày: Hoàng Anh · Patient.**

**UI / RabbitMQ:** Tạo bệnh nhân → `patient.created`; lưu chỉnh sửa → `patient.updated`. Tìm hồ sơ là đọc dữ liệu. Cấp account là command Organization riêng.

**Bàn giao:** Vinh nhận BN để đăng ký ở mục 4.

### A. Bệnh nhân mới: tạo hồ sơ và cấp tài khoản

1. Đăng nhập **demo.staff04@example.test**, mật khẩu **MediFlowDemo@2026**.
2. Menu **Bệnh nhân → Thêm bệnh nhân**, hoặc mở **http://127.0.0.1:3000/patients/new**.
3. Điền bảng dưới:

   | Ô trên UI | Giá trị nhập |
   |---|---|
   | Họ tên | `Demo Nguyễn Lan 01` |
   | Ngày sinh | **01/01/1995**; ô dạng tháng/ngày/năm thì nhập **01/01/1995** |
   | Giới tính | **Nữ** |
   | Số CMND/CCCD | `990000000001` |
   | Số điện thoại | `0900000001` |
   | Email | Email nhận thư do người demo kiểm soát; `demo.nguyenlan01@example.test` chỉ là mẫu |
   | Số BHYT | **Để trống** trong lần demo đầu |
   | Địa chỉ | `Dữ liệu demo - TP. Hồ Chí Minh` |

   Nếu muốn thử BHYT, mẫu hợp lệ là `01-12345678-9` (2 số-8 số-1 số).
   Nếu CCCD đã tồn tại trong phiên, tìm lại hồ sơ đó; không tạo trùng.
4. Bấm **Tạo bệnh nhân**. Kỳ vọng tới **Chi tiết bệnh nhân**, tên **Demo Nguyễn Lan 01**.
   Ghi **BN** theo mục 1.5.
5. Cuộn xuống **Tài khoản bệnh nhân và đăng ký khám → Cấp tài khoản bệnh nhân**.
   Kiểm email hồ sơ và trạng thái gửi thư. Tên đăng nhập là **email**, mật khẩu tạm
   do hệ thống tạo và gửi qua email; không dùng CCCD hoặc mật khẩu mặc định `1`.
6. **Chưa cần đăng nhập bệnh nhân lúc này.** Ngay trong khối đó, dưới thông báo tài khoản,
   bấm liên kết chữ gạch chân **Tiếp tục đăng ký khám**.
7. Trang **Tiếp nhận lịch khám** mở ra, đường dẫn chứa `/appointments/reception?patientId=…`.
   Bệnh nhân được truyền sẵn. Đi tới **mục 4**, chọn **Khám đợt mới**.

Nếu đã rời trang chi tiết: **Bệnh nhân → tìm 990000000001 → mở Demo Nguyễn Lan 01**,
rồi thực hiện bước 5–6. Không cần tạo hồ sơ lần nữa.

### B. Bệnh nhân cũ đến khám đợt mới

1. Đăng nhập **demo.staff04@example.test** → **Bệnh nhân**, tìm CCCD **999881000001**.
2. Mở **Nguyễn Văn An Demo**, ngày sinh **12/04/1986**; ghi **BN**.
3. Bấm **Tiếp tục đăng ký khám** ở khối tài khoản/đăng ký khám.
   **Không cấp lại tài khoản, không đặt lại mật khẩu.**
4. Đi tới **mục 4**, chọn **Khám đợt mới**. Đây là lượt khám mới của người đã có hồ sơ.

### C. Bệnh nhân tái khám

1. Chỉ thực hiện khi cửa sổ backend đã báo **FOLLOW-UP SOURCE READY**.
2. Đăng nhập **demo.staff04@example.test** → **Bệnh nhân**, tìm **999881000002**.
3. Mở **Trần Thị Bình Demo**, ngày sinh **12/04/1987**; ghi **BN**.
   Không tạo hồ sơ hoặc cấp lại tài khoản.
4. Bấm **Tiếp tục đăng ký khám**, thực hiện bước chọn buổi ở **mục 4**.
5. Tại **Loại lượt khám**, chọn **Tái khám từ lượt đã hoàn tất**.
6. Bấm **Tìm lượt khám trước**. Trên thẻ lượt đã hoàn tất của **Trần Thị Bình Demo**,
   kiểm ngày hoàn tất **D**, **Khoa Nội tổng quát Demo**, bác sĩ **Lê Bác Sĩ Demo** →
   **Chọn lượt khám trước**. Thẻ không hiển thị chẩn đoán; không cần tìm chữ Z09 ở đây.
   Lượt nguồn được chuẩn bị vào ngày D để làm dữ liệu nền, không phải lượt bạn đang đăng ký.
7. Giữ tích **Dùng đề xuất của bác sĩ**. Giữ cùng bác sĩ **Lê Bác Sĩ Demo**.
   Tiếp tục bước **Đăng ký và giữ chỗ** ở mục 4.

Nếu không thấy lượt nguồn, dừng tình huống C, không chọn một hồ sơ lịch sử bất kỳ để
thay thế. Phiên khởi động ngoài giờ có thể chưa chuẩn bị được lượt nguồn.

## 4. VINH / NURSE — Clinical: chọn buổi và đăng ký khám

**Người trình bày: Vinh · Clinical.**

**UI / RabbitMQ:** **Đăng ký và giữ chỗ** → `outpatient.registration.changed` trong nhánh scheduled. Không trộn event lịch legacy để giải thích phiếu này.

**Bàn giao:** Lộc nhận PHIEU, thu khám ngay ở mục 5.

Trang: **http://127.0.0.1:3000/appointments/reception** (đi từ **Tiếp tục đăng ký khám**
thì bệnh nhân đã được điền sẵn).

1. Khối **1. Bệnh nhân**: kiểm dòng tên đã chọn, ví dụ **Nguyễn Văn An Demo · 12/04/1986**.
   Nếu trống: nhập CCCD → **Tìm** → bấm kết quả có tên.
   - A: **Demo Nguyễn Lan 01**, CCCD **990000000001**.
   - B: **Nguyễn Văn An Demo**, CCCD **999881000001**.
   - C: **Trần Thị Bình Demo**, CCCD **999881000002**.
2. Khối **2. Chọn buổi khám**: nhập **Nội tổng quát** vào ô **Khoa** → **Tìm** →
   bấm kết quả **Khoa Nội tổng quát Demo (DEMO-NOI)**.
   **Từ ngày** và **Đến ngày** mặc định là hôm nay (= D); dòng **Đã chọn:** hiển thị
   ngày/tháng/năm, ô lịch có thể hiện theo định dạng trình duyệt.
3. Bấm **Tải buổi khám**. Trong nhiều thẻ lịch, chọn đúng bộ ba:
   **Lê Bác Sĩ Demo / 07:00–17:00 / Phong kham ngoai tru demo V2** → **Chọn buổi**.
   Luồng chính không chọn thẻ bác sĩ có tên bắt đầu bằng **BS Demo…**.
4. Cuộn xuống form mới xuất hiện: **Tiếp nhận vào buổi [D] · 07:00**, có dòng
   **Còn … chỗ** và tên bệnh nhân đã chọn ở bước 1.
5. Nếu muốn xem trước các phiếu đã có, bấm **Tải phiếu trong buổi**.
6. **Loại lượt khám**: A/B chọn **Khám đợt mới**; C chọn
   **Tái khám từ lượt đã hoàn tất**, chọn nguồn theo mục 3C.
   Đây là danh sách thả xuống, **không có nút riêng “Khám mới”**.
7. **Hình thức tiếp nhận → Đang có mặt tại quầy**.
   **Ghi chú tùy chọn**: `Demo A - tiếp nhận khám mới` (đổi A thành B/C tương ứng).
8. Bấm **Đăng ký và giữ chỗ**. Kỳ vọng khối **Đang chờ thu tiền** có **Hạn giữ chỗ**,
   bác sĩ **Lê Bác Sĩ Demo**, **Số quầy: Chưa cấp trước khi xác nhận**; trong
   **Phiếu trong buổi** dòng bệnh nhân hiện **Chờ thanh toán · Chưa có số**.
   Chờ cập nhật xong: **Phiếu trong buổi** phải có bệnh nhân vừa đăng ký; số chỗ
   được lấy lại từ backend. Trong lúc tải lại, số chỗ cũ được ẩn và nút đăng ký bị khóa.
   Nếu hiện **Phiếu đã được tạo; chưa cập nhật được danh sách và số chỗ**, giữ phiếu
   đã tạo và bấm **Cập nhật danh sách và số chỗ**; nút này chỉ tải lại, không tạo phiếu.
   Nếu hiện **Chưa xác định phiếu đã được tạo**, dùng **Gửi lại cùng yêu cầu**;
   hệ thống giữ nguyên mã chống trùng và khóa đổi khoa/buổi trong lúc đối soát.
9. Sao chép **Mã phiếu bàn giao thu ngân** vào **PHIEU**. Có thể bấm
   **Mở phiếu khám vừa đăng ký** để kiểm đúng tên. Bấm **Tiếp nhận bệnh nhân tiếp theo**
   khi cần làm người khác. Đổi sang cashier ngay, trước hạn giữ chỗ.

Nếu đang đăng ký cho ngày sau, bước 7 phải chọn **Đăng ký trước, sẽ đến sau**.
Khi bệnh nhân tới đúng ngày, nurse cần **Ghi nhận đã đến** sau thanh toán.
Luồng chính đã chọn có mặt tại quầy nên không bấm tiếp nhận theo lịch cũ.

## 5. LỘC / CASHIER — Billing: thu phí khám

**Người trình bày: Lộc · Billing.**

**UI / RabbitMQ:** **Thu phí lịch khám** → `payment.completed`; đủ nghĩa vụ → `outpatient.financial.eligibility.granted`, Clinical xác nhận phiếu.

**Bàn giao:** điều dưỡng khoa cấp số phòng ở mục 5b, sau đó bác sĩ khám ở mục 6.

1. **Đăng xuất → demo.staff19@example.test / MediFlowDemo@2026**.
2. Menu **Thu phí lịch khám**, hoặc **http://127.0.0.1:3000/billing/scheduled-payments**.
3. Chọn đúng tên bệnh nhân trong danh sách. Nếu cần tra chính xác, mở
   **Tra cứu phiếu được cung cấp**, dán **PHIEU** vào **Mã phiếu đăng ký** → **Tra cứu khoản thu**.
4. Đối chiếu họ tên, ngày D, bác sĩ **Lê Bác Sĩ Demo** và số phải trả:

   | Tình huống | Giá mẫu | Hỗ trợ mẫu | Người bệnh trả |
   |---|---:|---:|---:|
   | A/B — Khám đợt mới | 100.000 | 50.000 | **50.000 VND** |
   | C — Tái khám | 80.000 | 40.000 | **40.000 VND** |

5. **Hình thức thu → Tiền mặt → Thu phí lịch khám**.
   Màn hình này lấy số tiền theo báo giá; không cần tự lập một hóa đơn khác.
6. Nếu hiện đang xử lý, bấm **Kiểm tra trạng thái giao dịch**, sau đó **Cập nhật số dư**
   khi cần. Kỳ vọng thanh toán hoàn tất, phiếu được xác nhận và có **số quầy**.
7. Ghi số quầy bên cạnh **PHIEU**, chuyển sang quầy điều dưỡng khoa (mục 5b).

Các số trên là policy mô phỏng của bộ demo. Nếu báo giá hiện tại khác, dùng số tiền
UI xác nhận và ghi lại khác biệt; không tự sửa số để khớp bảng hoặc thu lần hai.

## 5b. VINH / NURSE — Clinical: quầy điều dưỡng khoa cấp số phòng

**Người trình bày: Vinh · Clinical (tiếp nhận khoa v2).**

**Vì sao có bước này:** đã thu tiền chưa đủ để vào khám. Điều dưỡng khoa phải
**cấp số phòng** cho bệnh nhân đã có mặt; nếu bỏ qua, bác sĩ bấm **Bắt đầu khám** sẽ bị
chặn với thông báo bàn khoa cần cấp số phòng trước (`CLINICAL_ROOM_TICKET_REQUIRED`).

**Bàn giao:** bác sĩ thấy bệnh nhân trong nhóm **Được vào khám**, bắt đầu khám ở mục 6.

1. **Đăng xuất → demo.staff04@example.test / MediFlowDemo@2026**.
2. Menu **Quầy điều dưỡng khoa**, hoặc **http://127.0.0.1:3000/appointments/clinic-queue**.
3. **Ngày khám → D** (mặc định hôm nay) → **Tải hàng đợi**. Mỗi phòng một khối, ví dụ
   **P.R-… · Phong kham ngoai tru demo V2 · Tầng 1, khu Demo**.
4. Bệnh nhân vừa thu tiền nằm ở nhóm **Chờ cấp số phòng** (chưa thu thì nằm ở
   **Chưa đủ điều kiện · Chưa có số**). Bấm **Cấp số phòng**. Kỳ vọng dòng hiện
   **Số quầy … · Số phòng …** và chuyển sang nhóm **Được vào khám**.
5. Tùy chọn **Ghi sinh hiệu**: ví dụ Mạch `78`, Huyết áp tâm thu `120`, tâm trương `80`,
   Nhiệt độ `36.8` (dùng dấu chấm), Nhịp thở `18`, SpO2 `98`, Cân nặng `60` →
   **Lưu sinh hiệu**. Bác sĩ xem lại bằng **Xem sinh hiệu**.
6. Tùy chọn **Gọi vào khám** (bấm lại là **Gọi lại**). Các nút **Đến muộn**, **Vắng mặt**,
   **Ưu tiên** chỉ dùng khi muốn minh họa điều phối hàng đợi; không cần cho lượt chính.

Bệnh nhân đăng ký trước (**Đăng ký trước, sẽ đến sau**) phải được ghi nhận đã đến
trước, nếu không sẽ ở nhóm **Chưa đủ điều kiện** và không cấp số phòng được.

## 6. VINH / DOCTOR — Clinical: khám và lập yêu cầu Lab

**Người trình bày: Vinh · Clinical/Lab.**

**UI / RabbitMQ:** Bắt đầu khám/lưu hồ sơ → `appointment.status.changed` / `medicalrecord.created` theo command; thêm chẩn đoán → `diagnosis.added`. **Lập chỉ định** lưu nguồn tại Clinical; **Tạo yêu cầu từ chỉ định** ở Lab mới phát `lab.request.created`.

**Bàn giao:** Lộc nhận khoản LAB_TEST, thu phí tại mục 7; chưa hoàn tất hồ sơ.

1. **Đăng xuất → demo.staff03@example.test / MediFlowDemo@2026**.
2. **Lịch khám của tôi**: **http://127.0.0.1:3000/appointments/my-calendar**.
3. **Chế độ lịch → Theo ngày**, **Ngày tham chiếu → D**, ví dụ **12/11/2026** →
   **Tải lịch của tôi**.
4. Ở buổi **07:00–17:00 / Khoa Nội tổng quát Demo / Phong kham ngoai tru demo V2**,
   bấm **Xem phiếu**.
5. Chọn đúng tên bệnh nhân, số quầy/số phòng ghi ở mục 5–5b → **Xem phiếu khám**.
   Nếu chưa thấy trạng thái mới, bấm **Cập nhật trạng thái**. Bác sĩ cũng mở được
   **Quầy điều dưỡng khoa** để xem hàng đợi và sinh hiệu (không cấp số phòng).
6. **Bắt đầu khám → xác nhận trong khối xác nhận**. Chờ trạng thái đang khám →
   **Mở hồ sơ khám**. Lưu URL này, ghi **HOSO**.
7. **Sửa triệu chứng**: nhập `Khám sức khỏe định kỳ - dữ liệu demo` → lưu thay đổi.
8. Tại **Chẩn đoán**, nhập:
   - Tên chẩn đoán: `Theo dõi sức khỏe - dữ liệu demo`.
   - Mã ICD: `Z00`.
   - Bấm thêm chẩn đoán.
9. Bấm **Chỉ định xét nghiệm**. Chờ khối **Ngữ cảnh chỉ định xét nghiệm** tải xong;
   đối chiếu tên, ngày sinh, ngày khám, bác sĩ và khoa trước khi nhập. Nếu không xác
   minh được hồ sơ, bấm **Thử lại** để đọc lại; chưa có chỉ định nào được gửi. Điền:

   | Ô | Giá trị |
   |---|---|
   | Dịch vụ (danh mục) | **Công thức máu · Bộ xét nghiệm** |
   | Ngày yêu cầu | **D**, ví dụ **12/11/2026** |

   Mã giá `LAB-CBC` đi theo danh mục, không nhập tay. Nếu khối báo
   *Không tải được danh mục xét nghiệm*, bấm tải lại trang; không nhập thủ công,
   vì chỉ định thủ công không có mẫu báo cáo để duyệt ở mục 8.

10. **Lập chỉ định** → tại khối tiếp theo, **Tạo yêu cầu từ chỉ định → Mở xét nghiệm**.
    Không bỏ qua bước tạo yêu cầu thứ hai.
11. Lưu URL xét nghiệm để mở lại khi cần. Thu ngân chọn khoản trong hàng chờ theo
    bệnh nhân, không cần chép mã. Chỉ khi cần hỗ trợ mới mở **Thông tin bàn giao thu ngân**
    để lấy LAB và mã đợt PHIEU; không dùng HOSO thay cho PHIEU.
12. **Chưa hoàn tất hồ sơ khám**. Chuyển cashier thu xét nghiệm.

## 7. LỘC / CASHIER — Billing: thu phí CBC

**Người trình bày: Lộc · Billing.**

**UI / RabbitMQ:** **Xác nhận thu theo báo giá** → `payment.completed`; đủ tài chính cho đúng Lab → `financial.clearance.granted`.

**Bàn giao:** Vinh đổi vai LAB_TECH, kiểm clearance rồi làm xét nghiệm ở mục 8.

1. **Đăng xuất → demo.staff19@example.test / MediFlowDemo@2026**.
2. Menu **Thu phí xét nghiệm & thuốc**:
   **http://127.0.0.1:3000/billing/payment-requests**.
3. Ở **Khoản cần xử lý**, ô **Tìm bệnh nhân chờ thu** nhập CCCD của A/B/C →
   **Tìm → chọn đúng tên/ngày sinh**. **Loại khoản cần xử lý → Xét nghiệm**.
4. Chọn dòng đúng bệnh nhân, khoa Nội và **thời điểm phát sinh chỉ định vừa làm** →
   **Chọn khoản**. Hệ thống giữ toàn bộ mã nguồn/lượt và đọc lại số dư hiện hành;
   không nhập LAB/BN/PHIEU. Chưa thấy khoản thì **Cập nhật hàng chờ**, không lập lại chỉ định.
   Nếu bệnh nhân có nhiều lượt, **Chỉ khoản cùng lượt đã chọn** giới hạn danh sách theo
   lượt của dòng đã đối chiếu. Thời điểm phát sinh phí không phải ngày hẹn khám.
5. Tại chi tiết bên phải, kiểm đúng người/khoa, còn phải thu **36.000 VND**.
   Mở **Chi tiết giá và hỗ trợ mô phỏng**: giá mẫu **120.000**, hỗ trợ **84.000**.
6. Chọn **Tiền mặt → Xác nhận thu theo báo giá**.
7. Chờ **Đã thanh toán**, số còn phải thu **0 VND**, rồi chuyển Lab.
   Nếu kết quả giao dịch chưa rõ, kiểm trạng thái; không thu lại.

**Khi cần hỗ trợ bằng mã:** mở **Tra cứu bằng mã được bàn giao (hỗ trợ)**,
chọn Xét nghiệm, điền LAB và PHIEU, tìm đúng bệnh nhân rồi **Tra cứu khoản phí**.
Đường này dùng khi đã có mã chính xác; không phải bước thường lệ. Nếu đọc lại khoản
đang đối chiếu lỗi, dùng **Đọc lại khoản đang đối chiếu**. Giữ biên nhận thành công;
không tạo lại giao dịch vì lỗi tải dữ liệu. Pending/không rõ kết quả khóa đổi khoản;
gửi lại giữ nguyên yêu cầu, kể cả khi báo giá mới đã hết hạn.

## 8. VINH / LAB_TECH — Lab: bệnh phẩm, nhập kết quả và duyệt hai người

**Người trình bày: Vinh · Lab.**

**UI / RabbitMQ:** **Bắt đầu xét nghiệm** kiểm điều kiện đã thu; **Phát hành** →
`lab.result.created`. Người nhập và người phát hành phải là hai KTV khác nhau.

**Bàn giao:** Vinh đăng nhập lại DOCTOR đọc kết quả cùng lượt ở mục 9.

Cả hai KTV đã được cấp quyền **Phát hành kết quả xét nghiệm** lúc chuẩn bị
(ADMIN xem ở Organization → hồ sơ nhân sự → **Quyền đặc biệt**).

1. **Đăng xuất → demo.staff15@example.test / MediFlowDemo@2026**.
2. Mở **http://127.0.0.1:3000/lab** → chọn **Công thức máu** đúng tên bệnh nhân.
   Cách nhanh: mở lại URL **/lab/[LAB]** đã lưu ở mục 6.
3. Tải lại trạng thái để nhận xác nhận đã thu → **Bắt đầu xét nghiệm**.
   Nếu chưa thu thì bước này phải bị chặn; không dùng chế độ cấp cứu để vượt qua.
4. Khối **Bệnh phẩm** → **Thu nhận bệnh phẩm**, loại `WHOLE_BLOOD_EDTA` → **Thu nhận**
   → **Tiếp nhận**. (NURSE cũng thu nhận được; chỉ KTV tiếp nhận hoặc từ chối.)
5. Khối **Báo cáo xét nghiệm**: đối chiếu tên/ngày sinh rồi nhập đủ năm chỉ số:

   | Chỉ số | Giá trị |
   |---|---|
   | `WBC` | `7.0` |
   | `RBC` | `4.8` |
   | `HGB` | `140` |
   | `HCT` | `0.42` |
   | `PLT` | `250` |

   **Ngày thực hiện → D**; **Kết luận**: `Kết quả mô phỏng phục vụ demo`.
6. **Lưu nháp** → **Gửi duyệt**. Trạng thái **Chờ duyệt**.
7. Bấm **Phát hành** bằng chính tài khoản này: hệ thống từ chối
   *"Không thể tự phát hành báo cáo do chính bạn nhập hoặc gửi duyệt."* — điểm cần nói.
8. **Đăng xuất → demo.staff16@example.test** → mở cùng URL → **Phát hành**.
   Trạng thái **Đã phát hành**. Giữ URL Lab, chuyển lại bác sĩ.

Lúc phát hành, Lab hỏi Patient giới tính và ngày sinh để chọn khoảng tham chiếu
(khoảng mẫu chỉ dành cho người lớn, tách theo giới). Với số liệu trên, cờ là **Bình thường**.
Nếu Core không trả lời kịp, kết quả vẫn phát hành, cờ hiện **Chưa xác định**.

**Tùy chọn — cảnh báo nguy cấp:** nhập `HGB = 60` thay cho `140`. Sau khi KTV thứ hai
phát hành, cờ là **Thấp nguy cấp**; bác sĩ mở **http://127.0.0.1:3000/lab/critical**,
thấy cảnh báo của đúng bệnh nhân và bấm xác nhận đã nhận.

## 8b. Tùy chọn — đính chính kết quả đã phát hành

Chỉ dùng khi còn thời gian, sau mục 8.

1. **demo.staff15@example.test** mở lại **/lab/[LAB]** → **Đính chính** → lý do
   `Nhập nhầm, đo lại mẫu` → **Tạo bản đính chính**.
2. Sửa `WBC = 6.5` → **Lưu nháp** → **Gửi duyệt**.
3. **demo.staff16@example.test** → **Phát hành**.
4. Bác sĩ mở lại hồ sơ: tài liệu Lab hiện phiên bản 2 và giữ phiên bản cũ trong lịch sử.

## 9. VINH / DOCTOR — Clinical: đọc lại kết quả trong cùng lượt

**Người trình bày: Vinh · Clinical.**

**UI / RabbitMQ:** Đọc CBC qua UI là đọc dữ liệu, không phát lại `lab.result.created`, không tạo lượt/thu phí khám lần hai.

**Bàn giao:** Huy nhận HOSO đang mở, tạo đơn tại mục 10; giữ nguyên vai DOCTOR hoặc đăng nhập lại đúng bác sĩ.

1. **Đăng xuất → demo.staff03@example.test / MediFlowDemo@2026**.
2. Mở lại URL **/records/[HOSO]** đã lưu, **Cập nhật hồ sơ**.
3. **Xem xét nghiệm của lượt khám** → mở CBC của đúng người/đúng lượt, đọc bảng
   **WBC = 7.0 G/L** cùng bốn chỉ số còn lại.

## 10. HUY / DOCTOR — Pharmacy: tạo đơn thuốc

**Người trình bày: Huy · Pharmacy.**

**UI / RabbitMQ:** **Tạo đơn thuốc** → `prescription.created`; Billing chuẩn bị khoản PRESCRIPTION.

**Bàn giao:** Lộc thu tiền ở mục 11. Chưa hoàn tất hồ sơ, chưa xuất thuốc.

1. Giữ phiên DOCTOR đúng bác sĩ, hoặc đăng nhập lại bằng email ở mục 1.3.
2. Mở **http://127.0.0.1:3000/pharmacy/prescriptions/new** để tạo đơn, điền:

   | Ô | Chọn/nhập |
   |---|---|
   | Bệnh nhân | CCCD của A/B/C → tìm → chọn đúng tên |
   | Hồ sơ khám | Hồ sơ **ngày D**, đang mở, đúng **HOSO** ở mục 6 |
   | Bác sĩ kê đơn nếu cần chọn | **Lê Bác Sĩ Demo** |
   | Khoa khám | Hệ thống lấy **Khoa Nội tổng quát Demo**, không chọn khoa khác |
   | Ngày kê đơn | **D**, ví dụ **12/11/2026** |
   | Nhóm thu tiền thuốc nếu xuất hiện | **RX-DEMO**; nếu chỉ một nhóm và không hiện ô chọn, hệ thống đã dùng nhóm này |
   | Thuốc | Tìm **DEMO-HIST-2025**, chọn **Thuốc minh họa A** |
   | Số lượng | `2` |
   | Cách dùng | `Thuốc mô phỏng phục vụ demo, không dùng điều trị` |

3. Từ khóa **DEMO-HIST-2025** có nhiều kết quả: chọn đúng tên **Thuốc minh họa A**,
   không chọn **Thuốc cảnh báo tồn thấp**, **Thuốc hết tồn** hoặc **Thuốc kiểm kê**.
   Kiểm còn tồn, hạn dùng 2030, đơn giá mẫu **10.000/viên**, tổng **20.000 VND** →
   **Tạo đơn thuốc**. Ghi **DON** và lưu URL chi tiết.

Không chọn hồ sơ lịch sử của bệnh nhân để thay cho HOSO hiện tại khi kê đơn bị lỗi.

## 11. LỘC / CASHIER — Billing: thu tiền thuốc

**Người trình bày: Lộc · Billing.**

**UI / RabbitMQ:** **Xác nhận thanh toán** → `payment.completed`; đủ nghĩa vụ đúng đơn → `financial.clearance.granted`. Thanh toán không đồng nghĩa đã xuất kho.

**Bàn giao:** Huy dùng vai PHARMACIST để xuất thuốc ở mục 12.

1. **Đăng xuất → demo.staff19@example.test / MediFlowDemo@2026**.
2. Mở **http://127.0.0.1:3000/billing/payment-requests**.
3. **Tìm bệnh nhân chờ thu → CCCD A/B/C → Tìm → chọn đúng tên/ngày sinh**.
   **Loại khoản cần xử lý → Đơn thuốc**.
4. Chọn khoản đúng khoa và thời điểm tạo đơn vừa làm → **Chọn khoản**.
   Đối chiếu người/lượt và còn phải thu **20.000 VND**. Không cần nhập DON/PHIEU.
   Chỉ khi cần đối soát mới mở **Thông tin đối soát khoản đã chọn** để xem mã.
5. Trong form thu tiền: **Số tiền → 20000**, **Mã tiền → VND** (giữ mặc định),
   **Hình thức → Tiền mặt** → **Xác nhận thanh toán**.
6. Kỳ vọng giao dịch thành công và còn phải thu **0 VND**.

## 12. HUY / PHARMACIST — Pharmacy: cấp phát thuốc

**Người trình bày: Huy · Pharmacy.**

**UI / RabbitMQ:** **Xác nhận xuất thuốc** → `prescription.filled`; nhánh cấp thất bại có `prescription.dispense.failed`, không giả đã xuất.

**Bàn giao:** Vinh hoàn tất hồ sơ tại mục 13; giữ nguyên HOSO và DON.

1. **Đăng xuất → demo.staff17@example.test / MediFlowDemo@2026**.
2. Mở **http://127.0.0.1:3000/pharmacy/prescriptions**, tìm đúng bệnh nhân và **DON**.
   Có thể dùng ngay URL chi tiết đơn đã lưu ở mục 10.
3. **Tải lại trạng thái**, xác nhận đơn đã thu đủ, thuốc **DEMO-HIST-2025 — Thuốc minh họa A**,
   số lượng **2**.
4. **Xuất thuốc → Xác nhận xuất thuốc**.
5. Kỳ vọng có phiếu xuất, cấp phát hoàn tất, tồn thuốc A giảm đúng 2 so với trước xuất.
   Không bấm xuất lại cùng đơn.

| Tổng tiền mẫu cho một lượt | Khám | CBC | Thuốc | Tổng |
|---|---:|---:|---:|---:|
| A/B — Khám đợt mới | 50.000 | 36.000 | 20.000 | **106.000 VND** |
| C — Tái khám | 40.000 | 36.000 | 20.000 | **96.000 VND** |

Đã thu ba khoản riêng; không tạo thêm một hóa đơn tổng để thu lần nữa.

## 13. VINH / DOCTOR — Clinical: hoàn tất hồ sơ, hướng dẫn ra về

**Người trình bày: Vinh · Clinical.**

**UI / RabbitMQ:** **Hoàn tất hồ sơ khám** → `medicalrecord.completed`; **Tạo đề xuất tái khám** nếu dùng → `followup.recommendation.created`. Lời hẹn không tự giữ suất.

**Bàn giao:** Lộc trình bày Notification và portal chính chủ ở mục 14.

1. **Đăng xuất → đăng nhập DOCTOR đúng bác sĩ**, dùng email seed ở mục 1.3.
2. Quay lại URL **/records/[HOSO]**. Tại **Hoàn tất khám**:
   - **Hướng xử trí → Theo dõi ngoại trú**.
   - **Ghi chú xử trí**: `Đã xem CBC và kê đơn minh họa; kết thúc lượt khám demo.`
   - Tích **Tôi đã kiểm tra chẩn đoán, chỉ định và xác nhận hoàn tất lần khám.**
   - **Hoàn tất hồ sơ khám**.
3. Kỳ vọng hồ sơ hoàn tất. Nếu demo thêm đề xuất tái khám, nhập:
   - Từ ngày **D + 1 ngày** đến **D + 7 ngày**.
   - Lý do: `Kiểm tra lại kết quả - dữ liệu demo`.
   - Hướng dẫn chuẩn bị: `Mang kết quả CBC và đơn thuốc lần trước`.
   - **Tạo đề xuất tái khám**. Đề xuất này **chưa tự đặt một buổi khám mới**.

## 14. LỘC / PATIENT — Notification và kết quả cá nhân

**Người trình bày: Lộc · Service: Notification · Vai UI: PATIENT.**

Thực hiện sau khi hoàn tất khám/cấp thuốc; sau đó Huy trình bày Report. Bệnh nhân
không cần đăng nhập để các quầy tiếp tục xử lý.

1. Đăng xuất tài khoản nhân viên.
2. Đăng nhập đúng người vừa hoàn thành:

   | Tình huống | Tài khoản | Mật khẩu |
   |---|---|---|
   | A — Demo Nguyễn Lan 01 | Email thực tế đã cấp tài khoản ở mục 3A | Mật khẩu tạm trong email, hoặc mật khẩu đã đổi |
   | B — Nguyễn Văn An Demo | `demo.patient01@example.test` | `MediFlowDemo@2026` |
   | C — Trần Thị Bình Demo | `demo.patient02@example.test` | `MediFlowDemo@2026` |

3. A: người demo nhận thư tự đăng nhập bằng email/mật khẩu tạm và đổi mật khẩu
   khi UI yêu cầu, rồi đăng nhập lại. Nếu chưa nhận được thư, giữ hồ sơ đã cấp;
   kiểm trạng thái gửi/gửi lại đúng thư theo runbook, không cấp một account khác.
   Có thể chọn B từ đầu buổi nếu chưa chuẩn bị được hộp thư cho A.
4. **Thông báo**: **http://127.0.0.1:3000/notifications** → kiểm thông báo
   kết quả Lab/hồ sơ khám của đúng bệnh nhân. Nêu chuỗi `lab.result.created` /
   `medicalrecord.completed` → `notification.q` → lưu IN_APP → `notification.sent`.
   `notification.sent` không có nghĩa người bệnh đã đọc hoặc email/SMS đã tới.
5. **Kết quả khám của tôi**: **http://127.0.0.1:3000/records/mine** →
   hồ sơ ngày **D**, bác sĩ **Lê Bác Sĩ Demo**, chẩn đoán **Z00**, đã hoàn tất.
6. **Kết quả xét nghiệm**: **http://127.0.0.1:3000/lab/mine** →
   CBC ngày **D**, WBC **7.0 G/L**, kết luận mô phỏng.
7. **Phiếu khám của tôi** → xem phiếu và đề xuất tái khám. Nếu đăng ký lại, nurse
   dùng hồ sơ/tài khoản cũ. Xem dữ liệu không phát lại event hoàn tất kết quả.

Nếu A chưa đăng nhập được thì không dùng account B/C để chứng minh đã xem kết quả
của A. Ghi nhận phần portal A chưa thực hiện; thông báo vẫn được lưu theo patient.

## 15. HUY / ADMIN hoặc trưởng khoa — Report cuối luồng

**Người trình bày: Huy · Service: Report (Support) · Vai UI: ADMIN/trưởng khoa.**

1. Đăng xuất PATIENT → đăng nhập ADMIN bằng email seed trong mục 1.3.
2. Mở **http://127.0.0.1:3000/reports/outpatient**.
3. Chọn **Theo ngày**; trưởng khoa chỉ thấy khoa của mình.
4. Tại **Kỳ có dữ liệu đã đối soát**, bấm **Tìm kỳ có dữ liệu** → đối chiếu khoa,
   phạm vi ngày, tiền tệ, dữ liệu mô phỏng → **Dùng kỳ này** → **Xem báo cáo**.
5. Trình bày các số liệu lượt khám, Lab, cấp thuốc, thu và hoàn tiền mà UI thực sự
   trả về. So với ba biên nhận của lượt chính khi kỳ đã bao phủ đúng lượt đó;
   không mặc định mọi KPI tăng 1 hoặc tiền tăng ngay sau mỗi lần click.
6. Giải thích nguồn: `medicalrecord.completed`, `lab.result.created`,
   `prescription.filled`, `payment.completed`, `payment.refunded` →
   `report.outpatient-v1.q` → read model/kỳ đã được chấp nhận.
7. Bấm xem báo cáo chỉ đọc dữ liệu, **không phát `report.generated`**.
   Report không đọc SQL trực tiếp từ các context nguồn.

Nếu hiện **Báo cáo ngoại trú chưa sẵn sàng**, chưa có kỳ phù hợp hoặc báo thiếu
coverage, ghi đúng trạng thái. Chọn kỳ đã nghiệm thu chuẩn bị trước để minh họa
nếu có; nói rõ đó là dữ liệu nền, không nhận là kết quả lượt vừa thao tác.
`report.q` là intake legacy khác; không dùng nó thay bằng chứng cho intake ngoại trú.

## 16. Khi không thấy nút hoặc dữ liệu như hướng dẫn

| Vướng ở đâu | Làm ngay |
|---|---|
| Không thấy “Tiếp tục đăng ký khám” | Mở **Chi tiết bệnh nhân**, cuộn xuống khối tài khoản/đăng ký; đây là liên kết chữ, không ở form tạo hồ sơ |
| Không thấy “Khám mới” | Chọn buổi trước; ở form phía dưới mở **Loại lượt khám → Khám đợt mới** |
| Không tải được buổi dù đã gõ khoa | Bấm **Tìm**, rồi **chọn kết quả có tên**; nhập chữ thôi chưa chọn khoa |
| Chỉ thấy BS Demo… chứ không có Lê Bác Sĩ Demo | Kiểm ngày D và khởi động backend đúng ngày, trong giờ khám; luồng chính dùng lịch riêng của demo.staff03@example.test |
| Chọn nhầm bác sĩ của lịch luân phiên | Chọn lại Lê Bác Sĩ Demo, không dùng tài khoản bác sĩ seed để khám thay; bác sĩ luân phiên chưa được script cấp account |
| Ngày minh họa đã qua | Thay D bằng ngày thực và khởi động phiên demo mới trong giờ khám, không giữ lịch quá khứ |
| Backend báo PARTIAL, C không có lượt nguồn | Chạy tình huống A/B; muốn đủ C, khởi động phiên mới trong 07:00–16:45 |
| Bác sĩ chưa có “Bắt đầu khám” | Kiểm đúng bác sĩ, đã thu phí, bệnh nhân đã đến, đúng ngày/giờ; nếu đặt trước thì nurse ghi nhận đã đến |
| “Bắt đầu khám” báo cần cấp số phòng | Làm mục 5b: NURSE mở **Quầy điều dưỡng khoa → Tải hàng đợi → Cấp số phòng**, rồi bác sĩ bấm lại |
| Không thấy menu **Quầy điều dưỡng khoa** | Khởi động frontend bằng **start-frontend.bat** (launcher bật cờ UI tiếp nhận khoa); không chạy `pnpm dev` trần |
| Lab chưa làm được sau thu tiền | Đúng LAB/PHIEU, tải lại để chờ xác nhận tài chính; không thu lần hai |
| Không tạo được đơn | Chọn đúng bệnh nhân và HOSO đang mở của chính lượt vừa khám |
| Chưa xuất được thuốc | Đúng DON, đã thu đủ, tải lại xác nhận tài chính, còn tồn và chưa hết hạn |
| Timeout sau ghi dữ liệu | Kiểm trạng thái hoặc gửi lại **cùng yêu cầu** theo UI; không tạo lệnh mới khi chưa rõ kết quả |
| Muốn làm lại sạch từ đầu | Kết thúc phiên rồi khởi động lại backend; không làm việc này giữa các bước của một lượt |

## 17. Hoàn tất demo và ghi nhận kết quả

- [ ] Đã chạy lượt chính A; B/C chỉ đánh dấu nếu chạy riêng, giữ đúng BN/PHIEU/HOSO/LAB/DON.
- [ ] Nurse cấp tài khoản chỉ cho bệnh nhân mới; bệnh nhân cũ dùng lại.
- [ ] Đã thu khám → cấp số phòng → khám → thu CBC → trả CBC → đọc kết quả → kê đơn → thu thuốc → cấp thuốc → hoàn tất hồ sơ.
- [ ] Patient xem đúng kết quả chính chủ; A đổi mật khẩu lần đầu.
- [ ] Notification và portal đúng bệnh nhân; Report phân biệt kỳ có dữ liệu với lượt vừa demo.
- [ ] Mỗi người chỉ đúng thao tác UI/event, không coi thao tác chỉ đọc là phát event.
- [ ] Ghi lại lỗi UI, ảnh và bước nếu có; không coi dữ liệu tự chuẩn bị là bước đã click đạt.

Kết thúc: đóng frontend bằng **Ctrl+C**, chạy **clean-docker.bat** trong thư mục **scripts/run**.

Hướng dẫn là kịch bản thao tác, chưa khẳng định toàn hành trình đã nghiệm thu.
[Biên bản lưới tuần/bổ nhiệm](../../docs/acceptance/weekly-roster-2026-11-01/README.md) ·
[Biên bản seed và UI chọn lịch ba tháng](../../docs/acceptance/demo-calendar-2026-11-01/README.md) ·
[Biên bản UI trước đó](../../docs/acceptance/ui-ux-demo-2026-10-29/README.md).

## 18. Tùy chọn sau lượt chính: lưới tuần và lịch bổ sung

### 18.1. Xem lưới đã có sẵn

1. Bổ nhiệm **Lê Bác Sĩ Demo** như mục 2, rồi đăng nhập **demo.staff03@example.test**.
2. **Sang Quản lý khoa → Lịch làm việc bác sĩ → Khoa Nội tổng quát Demo**.
3. **Từ ngày → thứ Hai của tuần chứa D** (ví dụ **09/11/2026** nếu D là 12/11) →
   **Theo tuần → Tải giờ làm việc**. Kỳ vọng tuần đó có các hàng
   **BS Demo Khoa Nội tổng quát Demo 01–06**.
4. Xem giờ/phòng/tổng giờ; cuộn ngang nếu cần. Ca đã có buổi khám sẽ bị chặn sửa/hủy.

Không cần chạy lệnh tạo ca mẫu. Nếu muốn demo thao tác tạo bằng UI trên phiên mới:
chọn **Lê Bác Sĩ Demo**, phòng **Phong kham ngoai tru demo V2**, ngày **D + 2 ngày** (ví dụ **14/11/2026** nếu D là 12/11),
giờ **08:00–12:00 → Lưu giờ làm việc**. Sau đó từ ca vừa tạo chọn
**Tạo buổi khám từ giờ làm việc**, giờ **09:00–11:00**, sức chứa **12 → Xác nhận tạo buổi khám**.
Chỉ tạo khi ngày đó chưa có ca/buổi bạn đã lập; không dùng phòng calendar đã kín.
Giữ ngày được chọn ở tương lai, không chọn ngày quá khứ.

### 18.2. Tham khảo lịch một tháng, không thay cho lượt chính

Từ 6 ngày trước đến 24 ngày sau ngày seed, mọi ngày đều có hai buổi ở phòng
**Phong kham lich day demo**, khoa **Khoa Nội tổng quát Demo**, luân phiên ba bác sĩ
**BS Demo Khoa Nội tổng quát Demo 01–03**, chưa được script cấp tài khoản.

Lịch một tháng chỉ minh họa danh mục/lịch, do launcher tự tạo lúc khởi động. Launcher
không cấp account cho ba bác sĩ luân phiên; không đăng nhập bằng username cũ hoặc dùng
bác sĩ seed để khám thay. Luồng chính dùng **Lê Bác Sĩ Demo** với account email
đã chuẩn bị. Muốn khám bằng bác sĩ khác cần cấp account/authority hợp lệ trước,
và tái khám đổi bác sĩ cần lý do, xác nhận đồng ý theo UI.

Danh sách khoa, bác sĩ luân phiên và trạng thái chưa cấp account nằm trong **DEMO-CALENDAR.local.md**
được tạo lúc khởi động; mở file này khi cần đối chiếu.
Ngày trong ô lịch theo thiết lập trình duyệt, tiêu đề thẻ buổi hiển thị ngày/tháng/năm.
