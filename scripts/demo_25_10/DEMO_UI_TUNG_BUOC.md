# Demo UI từng bước: tiếp nhận → khám → xét nghiệm → nhận thuốc

Cập nhật 01/11/2026. Chỉ dùng dữ liệu mẫu. Hướng dẫn này dùng seed tự chạy bởi
`start-backend.bat`; **không cần chạy thêm SQL, Python hoặc tạo lịch trước khi demo**.

## 1. Chuẩn bị một lần và chọn đúng ngày

### 1.1. Khởi động

1. Mở Docker Desktop, chờ Engine hoạt động.
2. Nhấp đúp **start-backend.bat** tại thư mục gốc. Chờ **CALENDAR READY** rồi **READY**.
   Lần đầu cần thêm vài phút để chuẩn bị nhân sự và lịch. Giữ cửa sổ backend mở.
3. Muốn demo đủ cả tái khám, khởi động backend **trong 07:00–16:45 giờ Việt Nam** và
   kiểm dòng **FOLLOW-UP SOURCE READY**. Nếu hiện **PARTIAL**, vẫn demo tạo hồ sơ,
   chọn lịch được nhưng chưa có lượt nguồn tái khám.
4. Nhấp đúp **start-frontend.bat**. Mở **http://127.0.0.1:3000/login**.

Không chạy lại backend giữa các quầy: một lần khởi động mới phục hồi lại dữ liệu,
làm mất lượt bạn vừa thao tác. Phiên demo tự dừng sau hai giờ.

### 1.2. Ngày và lịch dùng cho luồng chính

**Lịch minh họa chốt: ngày 02/11/2026, bắt đầu demo khoảng 08:00, hoàn tất trước 16:00.**

| Thông tin | Chọn/nhập chính xác |
|---|---|
| Ngày demo, viết tắt **D** trong các bước dưới | **02/11/2026** |
| Khoa | **Khoa Nội tổng quát Demo (DEMO-NOI)** |
| Bác sĩ | **Lê Bác Sĩ Demo** |
| Tài khoản bác sĩ | **demo.doctor** |
| Phòng | **Phong kham ngoai tru demo V2** |
| Buổi khám | **07:00–17:00**, đúng ngày D |
| Hình thức tiếp nhận khi demo đúng ngày, bệnh nhân có mặt | **Đang có mặt tại quầy** |

**Nếu bạn demo ngày khác:** D phải là **ngày thực tế bạn đang thao tác**, không giữ
02/11 khi ngày đó đã qua. Khởi động backend vào buổi sáng ngày demo; lịch riêng
của `demo.doctor` được chuẩn bị cho ngày chạy. Tất cả ô ngày khám, ngày yêu cầu Lab,
ngày thực hiện Lab và ngày kê đơn trong luồng chính đều dùng **cùng D**.

Nếu đang thao tác tối 01/11: chọn **02/11/2026 → Đăng ký trước, sẽ đến sau** chỉ để
demo đặt trước. Chưa thể đi hết bước bác sĩ khám ngay tối đó. Muốn demo liên tục
đến nhận thuốc, bắt đầu một phiên mới trong giờ khám ngày D; không đổi giờ máy.

### 1.3. Tài khoản sẽ dùng

Mật khẩu của tất cả tài khoản mẫu trong bảng: **`MediFlowDemo@2026`**.

| Lượt thao tác | Đăng nhập |
|---|---|
| Tạo hồ sơ, cấp tài khoản, đăng ký khám | `demo.nurse` |
| Thu phí khám, xét nghiệm và thuốc | `demo.cashier` |
| Khám, chỉ định, đọc kết quả, kê đơn | `demo.doctor` |
| Thực hiện xét nghiệm, nhập kết quả | `demo.lab` |
| Xuất thuốc | `demo.pharmacist` |
| Bệnh nhân cũ Nguyễn Văn An Demo xem kết quả | `demo.patient` |
| Bệnh nhân tái khám Trần Thị Bình Demo xem kết quả | `demo.patient2` |
| Bổ nhiệm trưởng khoa, phần tùy chọn ở cuối | `demo.admin` |

Mỗi lần đổi người: **Đăng xuất → nhập tài khoản trong bảng → Đăng nhập**.
Các tab cùng trình duyệt dùng chung phiên; không giữ nhiều vai trò bằng nhiều tab.
Trên cửa sổ hẹp, bấm nút ba gạch **Mở điều hướng** để thấy menu.

### 1.4. Chọn một bệnh nhân cho mỗi lần chạy

| Tình huống | Tên cần tìm/nhập | CCCD | Ngày sinh | Tài khoản bệnh nhân |
|---|---|---|---|---|
| A — mới đến, bạn tự tạo | **Demo Nguyễn Lan 01** | `990000000001` | **01/01/1995** | Cấp sau khi tạo, tên đăng nhập theo thông báo |
| B — bệnh nhân cũ khám đợt mới | **Nguyễn Văn An Demo** | `999881000001` | **12/04/1986** | `demo.patient` |
| C — tái khám | **Trần Thị Bình Demo** | `999881000002` | **12/04/1987** | `demo.patient2` |

**Lần đầu nên chạy A đến hết mục 10.** Sau đó chạy B và C, mỗi trường hợp một lượt
riêng. Không chạy cả ba đăng ký trước rồi để phiếu chờ thu quá lâu.

### 1.5. Các mã cần giữ trong lúc đổi quầy

Mở một ghi chú, ghi một dòng riêng cho A/B/C. **Các mã này do UI sinh trong phiên,
không dùng UUID trong ảnh cũ hoặc tự gõ một mã mẫu.**

| Ghi chú của bạn | Lấy ở đâu | Dùng ở đâu |
|---|---|---|
| **BN** — mã bệnh nhân | URL trang Chi tiết bệnh nhân: phần sau `/patients/`; Lab cũng có thông tin bàn giao | Đối chiếu đúng người; ô chọn bệnh nhân vẫn tìm được bằng CCCD |
| **PHIEU** — mã phiếu đăng ký | Sau đăng ký, ô **Mã phiếu bàn giao thu ngân** | Thu khám; **Mã đợt điều trị** khi thu Lab/thuốc |
| **HOSO** — mã hồ sơ khám | Bác sĩ bấm **Mở hồ sơ khám**; phần sau `/records/` trên URL | Quay lại đọc kết quả, hoàn tất khám và chọn đúng hồ sơ kê đơn |
| **LAB** — mã xét nghiệm | Sau **Mở xét nghiệm**, phần sau `/lab/`; khối **Thông tin bàn giao thu ngân** | Thu Lab và mở lại đúng xét nghiệm |
| **DON** — mã đơn thuốc | Sau **Tạo đơn thuốc**, phần sau `/pharmacy/prescriptions/` | Thu thuốc và mở đơn để cấp phát |

Ví dụ ghi: `A / Demo Nguyễn Lan 01 / BN=… / PHIEU=… / HOSO=… / LAB=… / DON=…`.
Giữ **PHIEU**, **HOSO**, **LAB**, **DON** riêng; chúng không phải cùng một mã.

## 2. NURSE — chọn điểm bắt đầu A, B hoặc C

### A. Bệnh nhân mới: tạo hồ sơ và cấp tài khoản

1. Đăng nhập **demo.nurse**, mật khẩu **MediFlowDemo@2026**.
2. Menu **Bệnh nhân → Thêm bệnh nhân**, hoặc mở **http://127.0.0.1:3000/patients/new**.
3. Điền bảng dưới:

   | Ô trên UI | Giá trị nhập |
   |---|---|
   | Họ tên | `Demo Nguyễn Lan 01` |
   | Ngày sinh | **01/01/1995**; ô dạng tháng/ngày/năm thì nhập **01/01/1995** |
   | Giới tính | **Nữ** |
   | Số CMND/CCCD | `990000000001` |
   | Số điện thoại | `0900000001` |
   | Email | `demo.nguyenlan01@example.test` |
   | Số BHYT | **Để trống** trong lần demo đầu |
   | Địa chỉ | `Dữ liệu demo - TP. Hồ Chí Minh` |

   Nếu muốn thử BHYT, mẫu hợp lệ là `01-12345678-9` (2 số-8 số-1 số).
   Nếu CCCD đã tồn tại trong phiên, tìm lại hồ sơ đó; không tạo trùng.
4. Bấm **Tạo bệnh nhân**. Kỳ vọng tới **Chi tiết bệnh nhân**, tên **Demo Nguyễn Lan 01**.
   Ghi **BN** theo mục 1.5.
5. Cuộn xuống **Tài khoản bệnh nhân và đăng ký khám → Cấp tài khoản bệnh nhân**.
   Ghi tên đăng nhập UI trả về; với CCCD trên là **990000000001**, mật khẩu ban đầu **1**.
6. **Không đăng nhập bệnh nhân lúc này.** Ngay trong khối đó, dưới thông báo tài khoản,
   bấm liên kết chữ gạch chân **Tiếp tục đăng ký khám**.
7. Trang **Tiếp nhận lịch khám** mở ra, đường dẫn chứa `/appointments/reception?patientId=…`.
   Bệnh nhân được truyền sẵn. Đi tới **mục 3**, chọn **Khám đợt mới**.

Nếu đã rời trang chi tiết: **Bệnh nhân → tìm 990000000001 → mở Demo Nguyễn Lan 01**,
rồi thực hiện bước 5–6. Không cần tạo hồ sơ lần nữa.

### B. Bệnh nhân cũ đến khám đợt mới

1. Đăng nhập **demo.nurse** → **Bệnh nhân**, tìm CCCD **999881000001**.
2. Mở **Nguyễn Văn An Demo**, ngày sinh **12/04/1986**; ghi **BN**.
3. Bấm **Tiếp tục đăng ký khám** ở khối tài khoản/đăng ký khám.
   **Không cấp lại tài khoản, không đặt lại mật khẩu.**
4. Đi tới **mục 3**, chọn **Khám đợt mới**. Đây là lượt khám mới của người đã có hồ sơ.

### C. Bệnh nhân tái khám

1. Chỉ thực hiện khi cửa sổ backend đã báo **FOLLOW-UP SOURCE READY**.
2. Đăng nhập **demo.nurse** → **Bệnh nhân**, tìm **999881000002**.
3. Mở **Trần Thị Bình Demo**, ngày sinh **12/04/1987**; ghi **BN**.
   Không tạo hồ sơ hoặc cấp lại tài khoản.
4. Bấm **Tiếp tục đăng ký khám**, thực hiện bước chọn buổi ở **mục 3**.
5. Tại **Loại lượt khám**, chọn **Tái khám từ lượt đã hoàn tất**.
6. Bấm **Tìm lượt khám trước**. Trên thẻ lượt đã hoàn tất của **Trần Thị Bình Demo**,
   kiểm ngày hoàn tất **D**, **Khoa Nội tổng quát Demo**, bác sĩ **Lê Bác Sĩ Demo** →
   **Chọn lượt khám trước**. Thẻ không hiển thị chẩn đoán; không cần tìm chữ Z09 ở đây.
   Lượt nguồn được chuẩn bị vào ngày D để làm dữ liệu nền, không phải lượt bạn đang đăng ký.
7. Giữ tích **Dùng đề xuất của bác sĩ**. Giữ cùng bác sĩ **Lê Bác Sĩ Demo**.
   Tiếp tục bước **Đăng ký và giữ chỗ** ở mục 3.

Nếu không thấy lượt nguồn, dừng tình huống C, không chọn một hồ sơ lịch sử bất kỳ để
thay thế. Phiên khởi động ngoài giờ có thể chưa chuẩn bị được lượt nguồn.

## 3. NURSE — chọn buổi và đăng ký khám

Trang: **http://127.0.0.1:3000/appointments/reception**.

1. Tại **Chọn buổi khám**, nhập **Nội tổng quát** vào ô **Khoa** → **Tìm** →
   bấm kết quả **Khoa Nội tổng quát Demo (DEMO-NOI)**.
2. **Từ ngày = D**, **Đến ngày = D**; ví dụ cả hai **02/11/2026**.
   Nếu trình duyệt hiển thị tháng/ngày/năm thì chọn **11/02/2026**, không phải 01/11.
3. Bấm **Tải buổi khám**. Trong nhiều thẻ lịch, chọn đúng bộ ba:
   **Lê Bác Sĩ Demo / 07:00–17:00 / Phong kham ngoai tru demo V2** → **Chọn buổi**.
   Luồng chính không chọn thẻ bác sĩ có tên bắt đầu bằng **BS Demo…**.
4. Cuộn xuống form mới xuất hiện: **Tiếp nhận vào buổi [D] · 07:00**.
5. Ô **Bệnh nhân**:
   - A: **Demo Nguyễn Lan 01**, CCCD **990000000001**.
   - B: **Nguyễn Văn An Demo**, CCCD **999881000001**.
   - C: **Trần Thị Bình Demo**, CCCD **999881000002**.
   Nếu ô chưa có người: nhập CCCD → **Tìm** → **bấm chọn kết quả có tên**.
6. **Loại lượt khám**: A/B chọn **Khám đợt mới**; C chọn
   **Tái khám từ lượt đã hoàn tất**, chọn nguồn theo mục 2C.
   Đây là danh sách thả xuống, **không có nút riêng “Khám mới”**.
7. **Hình thức tiếp nhận → Đang có mặt tại quầy**.
   **Ghi chú tùy chọn**: `Demo A - tiếp nhận khám mới` (đổi A thành B/C tương ứng).
8. Bấm **Đăng ký và giữ chỗ**. Kỳ vọng **Đang chờ thu tiền**, chưa có số thứ tự.
9. Sao chép **Mã phiếu bàn giao thu ngân** vào **PHIEU**. Có thể bấm
   **Mở phiếu khám vừa đăng ký** để kiểm đúng tên. Đổi sang cashier ngay.

Nếu đang đăng ký cho ngày sau, bước 7 phải chọn **Đăng ký trước, sẽ đến sau**.
Khi bệnh nhân tới đúng ngày, nurse cần **Ghi nhận đã đến** sau thanh toán.
Luồng chính đã chọn có mặt tại quầy nên không bấm tiếp nhận theo lịch cũ.

## 4. CASHIER — thu phí khám

1. **Đăng xuất → demo.cashier / MediFlowDemo@2026**.
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
   khi cần. Kỳ vọng thanh toán hoàn tất, phiếu được xác nhận và có số thứ tự.
7. Ghi số thứ tự bên cạnh **PHIEU**, chuyển sang bác sĩ.

Các số trên là policy mô phỏng của bộ demo. Nếu báo giá hiện tại khác, dùng số tiền
UI xác nhận và ghi lại khác biệt; không tự sửa số để khớp bảng hoặc thu lần hai.

## 5. DOCTOR — bắt đầu khám và lập chỉ định xét nghiệm

1. **Đăng xuất → demo.doctor / MediFlowDemo@2026**.
2. **Lịch khám của tôi**: **http://127.0.0.1:3000/appointments/my-calendar**.
3. **Chế độ lịch → Theo ngày**, **Ngày tham chiếu → D**, ví dụ **02/11/2026** →
   **Tải lịch của tôi**.
4. Ở buổi **07:00–17:00 / Khoa Nội tổng quát Demo / Phong kham ngoai tru demo V2**,
   bấm **Xem phiếu**.
5. Chọn đúng tên bệnh nhân và số thứ tự ghi ở mục 4 → **Xem phiếu khám**.
   Nếu chưa thấy trạng thái mới, bấm **Cập nhật trạng thái**.
6. **Bắt đầu khám → xác nhận trong khối xác nhận**. Chờ trạng thái đang khám →
   **Mở hồ sơ khám**. Lưu URL này, ghi **HOSO**.
7. **Sửa triệu chứng**: nhập `Khám sức khỏe định kỳ - dữ liệu demo` → lưu thay đổi.
8. Tại **Chẩn đoán**, nhập:
   - Tên chẩn đoán: `Theo dõi sức khỏe - dữ liệu demo`.
   - Mã ICD: `Z00`.
   - Bấm thêm chẩn đoán.
9. Bấm **Chỉ định xét nghiệm**. Điền:

   | Ô | Giá trị |
   |---|---|
   | Loại xét nghiệm | `CBC` |
   | Mã giá | `LAB_CBC` |
   | Ngày yêu cầu | **D**, ví dụ **02/11/2026** |

10. **Lập chỉ định** → tại khối tiếp theo, **Tạo yêu cầu từ chỉ định → Mở xét nghiệm**.
    Không bỏ qua bước tạo yêu cầu thứ hai.
11. Ghi **LAB**. Mở **Thông tin bàn giao thu ngân**, ghi/đối chiếu mã bệnh nhân,
    mã xét nghiệm, mã đợt điều trị. Mã đợt là **PHIEU**, không phải **HOSO**.
12. **Chưa hoàn tất hồ sơ khám**. Chuyển cashier thu xét nghiệm.

## 6. CASHIER — thu phí CBC

1. **Đăng xuất → demo.cashier / MediFlowDemo@2026**.
2. Menu **Thu phí xét nghiệm & thuốc**:
   **http://127.0.0.1:3000/billing/payment-requests**.
3. Điền:

   | Ô trên UI | Giá trị |
   |---|---|
   | Loại khoản phí | **Xét nghiệm** |
   | Mã xét nghiệm | Dán **LAB** vừa ghi ở mục 5 |
   | Mã bệnh nhân | Nhập CCCD của A/B/C → **Tìm** → chọn đúng tên |
   | Mã đợt điều trị | Dán **PHIEU** từ mục 3, không dán HOSO/LAB |

4. **Tra cứu khoản phí**. Nếu khoản phí chưa tới, chờ vài giây rồi tra cứu lại cùng mã.
5. Kiểm CBC: giá mẫu **120.000**, hỗ trợ **84.000**, phải trả **36.000 VND**.
6. Chọn **Tiền mặt → Xác nhận thu theo báo giá**.
7. Chờ **Đã thanh toán**, số còn phải thu **0 VND**, rồi chuyển Lab.
   Nếu kết quả giao dịch chưa rõ, kiểm trạng thái; không thu lại.

## 7. LAB_TECH — làm xét nghiệm và trả kết quả

1. **Đăng xuất → demo.lab / MediFlowDemo@2026**.
2. Mở **http://127.0.0.1:3000/lab** → chọn CBC đúng tên bệnh nhân.
   Cách nhanh: mở lại URL **/lab/[LAB]** đã lưu ở mục 5.
3. Tải lại trạng thái để nhận xác nhận đã thu → **Bắt đầu xét nghiệm**.
   Nếu chưa thu thì bước này phải bị chặn; không dùng chế độ cấp cứu để vượt qua.
4. **Nhập kết quả**. Nhập đúng một dòng:

   | Chỉ số | Giá trị | Đơn vị | Khoảng tham chiếu |
   |---|---|---|---|
   | `WBC` | `7.0` | `G/L` | `4-10` |

5. **Ngày thực hiện → D**, ví dụ **02/11/2026**.
   **Kết luận**: `Kết quả mô phỏng phục vụ demo`.
6. **Lưu kết quả**. Kỳ vọng xét nghiệm hoàn tất và có bảng WBC.
7. Giữ URL Lab, chuyển lại bác sĩ.

## 8. DOCTOR — đọc kết quả, kê thuốc và hoàn tất hồ sơ

1. **Đăng xuất → demo.doctor / MediFlowDemo@2026**.
2. Mở lại URL **/records/[HOSO]** đã lưu, **Cập nhật hồ sơ**.
3. **Xem xét nghiệm của lượt khám** → mở CBC của đúng người/đúng lượt, đọc bảng
   **WBC = 7.0 G/L, khoảng tham chiếu 4-10**.
4. Mở **http://127.0.0.1:3000/pharmacy/prescriptions/new** để tạo đơn, điền:

   | Ô | Chọn/nhập |
   |---|---|
   | Bệnh nhân | CCCD của A/B/C → tìm → chọn đúng tên |
   | Hồ sơ khám | Hồ sơ **ngày D**, đang mở, đúng **HOSO** ở mục 5 |
   | Bác sĩ kê đơn nếu cần chọn | **Lê Bác Sĩ Demo** |
   | Khoa khám | Hệ thống lấy **Khoa Nội tổng quát Demo**, không chọn khoa khác |
   | Ngày kê đơn | **D**, ví dụ **02/11/2026** |
   | Nhóm thu tiền thuốc nếu xuất hiện | **RX-DEMO**; nếu chỉ một nhóm và không hiện ô chọn, hệ thống đã dùng nhóm này |
   | Thuốc | Tìm **DEMO-HIST-2025**, chọn **Thuốc minh họa A** |
   | Số lượng | `2` |
   | Cách dùng | `Thuốc mô phỏng phục vụ demo, không dùng điều trị` |

5. Kiểm còn tồn, hạn dùng 2030, đơn giá mẫu **10.000/viên**, tổng **20.000 VND** →
   **Tạo đơn thuốc**. Ghi **DON** và lưu URL chi tiết.
6. Quay lại URL **/records/[HOSO]**. Tại **Hoàn tất khám**:
   - **Hướng xử trí → Theo dõi ngoại trú**.
   - **Ghi chú xử trí**: `Đã xem CBC và kê đơn minh họa; kết thúc lượt khám demo.`
   - Tích **Tôi đã kiểm tra chẩn đoán, chỉ định và xác nhận hoàn tất lần khám.**
   - **Hoàn tất hồ sơ khám**.
7. Kỳ vọng hồ sơ hoàn tất. Nếu demo thêm đề xuất tái khám, nhập:
   - Từ ngày **03/11/2026** và đến ngày **09/11/2026** nếu D là 02/11.
   - Nếu D khác: từ **D + 1 ngày** đến **D + 7 ngày**.
   - Lý do: `Kiểm tra lại kết quả - dữ liệu demo`.
   - Hướng dẫn chuẩn bị: `Mang kết quả CBC và đơn thuốc lần trước`.
   - **Tạo đề xuất tái khám**. Đề xuất này **chưa tự đặt một buổi khám mới**.

Không chọn hồ sơ lịch sử của bệnh nhân để thay cho HOSO hiện tại khi kê đơn bị lỗi.

## 9. CASHIER rồi PHARMACIST — thu tiền thuốc và cấp phát

### 9.1. Thu tiền thuốc

1. **Đăng xuất → demo.cashier / MediFlowDemo@2026**.
2. Mở **http://127.0.0.1:3000/billing/payment-requests**.
3. **Loại khoản phí → Đơn thuốc**.
   **Mã đơn thuốc → DON**. **Mã bệnh nhân → CCCD A/B/C → tìm → chọn đúng tên**.
   **Mã đợt điều trị → PHIEU**.
4. **Tra cứu khoản phí**. Kiểm đúng DON, bệnh nhân và PHIEU; còn phải thu **20.000 VND**.
5. Trong form thu tiền: **Số tiền → 20000**, **Mã tiền → VND** (giữ mặc định),
   **Hình thức → Tiền mặt** → **Xác nhận thanh toán**.
6. Kỳ vọng giao dịch thành công và còn phải thu **0 VND**.

### 9.2. Cấp phát thuốc

1. **Đăng xuất → demo.pharmacist / MediFlowDemo@2026**.
2. Mở **http://127.0.0.1:3000/pharmacy/prescriptions**, tìm đúng bệnh nhân và **DON**.
   Có thể dùng ngay URL chi tiết đơn đã lưu ở mục 8.
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

## 10. PATIENT — tùy chọn đăng nhập xem kết quả

**Phần này thực hiện sau cùng; bệnh nhân không cần đăng nhập để nurse/bác sĩ tiếp tục khám.**

1. Đăng xuất tài khoản nhân viên.
2. Đăng nhập đúng người vừa hoàn thành:

   | Tình huống | Tài khoản | Mật khẩu |
   |---|---|---|
   | A — Demo Nguyễn Lan 01 | `990000000001`, kiểm lại thông báo cấp tài khoản | Ban đầu `1` |
   | B — Nguyễn Văn An Demo | `demo.patient` | `MediFlowDemo@2026` |
   | C — Trần Thị Bình Demo | `demo.patient2` | `MediFlowDemo@2026` |

3. A: tự nhập/nhập lại mật khẩu mới trên màn hình bắt buộc đổi mật khẩu, ví dụ
   mật khẩu mẫu demo **LanDemo@2026**; đăng nhập lại nếu UI yêu cầu.
   Những lần sau dùng mật khẩu mới, không tiếp tục dùng `1`.
4. **Kết quả khám của tôi**: **http://127.0.0.1:3000/records/mine** →
   hồ sơ ngày **D**, bác sĩ **Lê Bác Sĩ Demo**, chẩn đoán **Z00**, đã hoàn tất.
5. **Kết quả xét nghiệm**: **http://127.0.0.1:3000/lab/mine** →
   CBC ngày **D**, WBC **7.0 G/L**, kết luận mô phỏng.
6. **Thông báo**: **http://127.0.0.1:3000/notifications** → kiểm thông báo kết quả/lượt khám.
   **Phiếu khám của tôi** → xem phiếu và đề xuất tái khám. Nếu cần đăng ký lại, nurse
   dùng hồ sơ/tài khoản cũ, không tạo mới.

## 11. Khi không thấy nút hoặc dữ liệu như hướng dẫn

| Vướng ở đâu | Làm ngay |
|---|---|
| Không thấy “Tiếp tục đăng ký khám” | Mở **Chi tiết bệnh nhân**, cuộn xuống khối tài khoản/đăng ký; đây là liên kết chữ, không ở form tạo hồ sơ |
| Không thấy “Khám mới” | Chọn buổi trước; ở form phía dưới mở **Loại lượt khám → Khám đợt mới** |
| Không tải được buổi dù đã gõ khoa | Bấm **Tìm**, rồi **chọn kết quả có tên**; nhập chữ thôi chưa chọn khoa |
| Chỉ thấy BS Demo… chứ không có Lê Bác Sĩ Demo | Kiểm ngày D và khởi động backend đúng ngày, trong giờ khám; luồng chính dùng lịch riêng của demo.doctor |
| Chọn nhầm bác sĩ của lịch ba tháng | Chọn lại Lê Bác Sĩ Demo, hoặc đăng nhập tài khoản tương ứng theo bảng phụ ở cuối; không dùng demo.doctor để khám thay |
| Ngày 02/11 đã qua | Thay D bằng ngày thực và khởi động phiên demo mới trong giờ khám, không giữ lịch quá khứ |
| Backend báo PARTIAL, C không có lượt nguồn | Chạy tình huống A/B; muốn đủ C, khởi động phiên mới trong 07:00–16:45 |
| Bác sĩ chưa có “Bắt đầu khám” | Kiểm đúng bác sĩ, đã thu phí, bệnh nhân đã đến, đúng ngày/giờ; nếu đặt trước thì nurse ghi nhận đã đến |
| Lab chưa làm được sau thu tiền | Đúng LAB/PHIEU, tải lại để chờ xác nhận tài chính; không thu lần hai |
| Không tạo được đơn | Chọn đúng bệnh nhân và HOSO đang mở của chính lượt vừa khám |
| Chưa xuất được thuốc | Đúng DON, đã thu đủ, tải lại xác nhận tài chính, còn tồn và chưa hết hạn |
| Timeout sau ghi dữ liệu | Kiểm trạng thái hoặc gửi lại **cùng yêu cầu** theo UI; không tạo lệnh mới khi chưa rõ kết quả |
| Muốn làm lại sạch từ đầu | Kết thúc phiên rồi khởi động lại backend; không làm việc này giữa các bước của một lượt |

## 12. Hoàn tất demo và ghi nhận kết quả

- [ ] Đã chạy A, B, C riêng; mỗi lượt giữ đúng BN/PHIEU/HOSO/LAB/DON.
- [ ] Nurse cấp tài khoản chỉ cho bệnh nhân mới; bệnh nhân cũ dùng lại.
- [ ] Đã thu khám → khám → thu CBC → trả CBC → kê/hoàn tất → thu/cấp thuốc.
- [ ] Patient xem đúng kết quả chính chủ; A đổi mật khẩu lần đầu.
- [ ] Ghi lại lỗi UI, ảnh và bước nếu có; không coi dữ liệu tự chuẩn bị là bước đã click đạt.

Kết thúc: đóng frontend bằng **Ctrl+C**, chạy **clean-docker.bat** tại thư mục gốc.

Hướng dẫn là kịch bản thao tác, chưa khẳng định toàn hành trình đã nghiệm thu.
[Biên bản lưới tuần/bổ nhiệm](../../docs/acceptance/weekly-roster-2026-11-01/README.md) ·
[Biên bản seed và UI chọn lịch ba tháng](../../docs/acceptance/demo-calendar-2026-11-01/README.md) ·
[Biên bản UI trước đó](../../docs/acceptance/ui-ux-demo-2026-10-29/README.md).

## 13. Tùy chọn sau luồng khám: ADMIN bổ nhiệm/miễn nhiệm

Phần này không phải bước bắt buộc để tiếp nhận bệnh nhân.

1. **demo.admin / MediFlowDemo@2026 → Tổ chức → Khoa Nội tổng quát Demo**.
2. Tại **Bổ nhiệm trưởng khoa**, chọn **Lê Bác Sĩ Demo · Bác sĩ · Khoa Nội tổng quát Demo**.
3. **Lý do**: `Demo bổ nhiệm trưởng khoa Nội` →
   **Xem trước bổ nhiệm → Xác nhận bổ nhiệm**.
4. Kiểm thông báo thành công, khối **Trưởng khoa** cũng hiện **Lê Bác Sĩ Demo**,
   mở **Lịch sử bổ nhiệm** để xem giờ Việt Nam.
5. Đăng xuất → **demo.doctor / MediFlowDemo@2026 → Sang Quản lý khoa**.
   **Về Khám bệnh** quay lại lịch cá nhân, cùng tài khoản.
6. Muốn miễn nhiệm: quay lại **demo.admin**, mở cùng khoa, nhập lý do
   `Demo kết thúc nhiệm kỳ, vẫn giữ chuyên môn bác sĩ` →
   **Miễn nhiệm → Xác nhận miễn nhiệm**.
7. Đăng nhập **demo.doctor**: còn chức năng bác sĩ, không còn nút quản lý khoa.
   Bổ nhiệm lại nếu muốn thử mục 14.

Nếu muốn thử thay bằng người khác: chọn **BS Demo Khoa Nội tổng quát Demo 01**;
tài khoản bác sĩ này là **demo.roster.532087c9b7.bs01**,
mật khẩu **MediFlowDemo@2026**. Kiểm cả người kết thúc và người nhận nhiệm kỳ trước
xác nhận. Đây là kịch bản thử bổ sung, chưa đánh dấu nghiệm thu UI thay thế.

## 14. Tùy chọn: lưới tuần và chọn bác sĩ bổ sung

### 14.1. Xem lưới đã có sẵn

1. Bổ nhiệm **Lê Bác Sĩ Demo** như mục 13, rồi đăng nhập **demo.doctor**.
2. **Sang Quản lý khoa → Lịch làm việc bác sĩ → Khoa Nội tổng quát Demo**.
3. **Từ ngày → 02/11/2026 → Theo tuần → Tải giờ làm việc**.
   Kỳ vọng tuần **02–08/11/2026** có các hàng **BS Demo Khoa Nội tổng quát Demo 01–06**.
4. Xem giờ/phòng/tổng giờ; cuộn ngang nếu cần. Ca đã có buổi khám sẽ bị chặn sửa/hủy.

Không cần chạy lệnh tạo ca mẫu. Nếu muốn demo thao tác tạo bằng UI trên phiên mới:
chọn **Lê Bác Sĩ Demo**, phòng **Phong kham ngoai tru demo V2**, ngày **04/11/2026**,
giờ **08:00–12:00 → Lưu giờ làm việc**. Sau đó từ ca vừa tạo chọn
**Tạo buổi khám từ giờ làm việc**, giờ **09:00–11:00**, sức chứa **12 → Xác nhận tạo buổi khám**.
Chỉ tạo khi ngày đó chưa có ca/buổi bạn đã lập; không dùng phòng calendar đã kín.
Nếu D khác, dùng **D + 2 ngày**, không chọn ngày quá khứ.

### 14.2. Nếu muốn dùng lịch ba tháng thay lịch luồng chính

Trong khoảng 01/11/2026–31/01/2027, mọi ngày đều có hai buổi ở phòng
**Phong kham lich day demo**, khoa **Khoa Nội tổng quát Demo**. Ví dụ cụ thể:

| Ngày | Buổi | Bác sĩ chọn trên UI | Tài khoản bác sĩ cần đăng nhập |
|---|---|---|---|
| **02/11/2026** | **07:00–12:00** | BS Demo Khoa Nội tổng quát Demo 03 | `demo.roster.532087c9b7.bs03` |
| **02/11/2026** | **12:00–17:00** | BS Demo Khoa Nội tổng quát Demo 04 | `demo.roster.532087c9b7.bs04` |
| **03/11/2026** | **07:00–12:00** | BS Demo Khoa Nội tổng quát Demo 05 | `demo.roster.532087c9b7.bs05` |
| **03/11/2026** | **12:00–17:00** | BS Demo Khoa Nội tổng quát Demo 06 | `demo.roster.532087c9b7.bs06` |
| **04/11/2026** | **07:00–12:00** | BS Demo Khoa Nội tổng quát Demo 01 | `demo.roster.532087c9b7.bs01` |
| **04/11/2026** | **12:00–17:00** | BS Demo Khoa Nội tổng quát Demo 02 | `demo.roster.532087c9b7.bs02` |

Mật khẩu cả sáu tài khoản: **MediFlowDemo@2026**. Chu kỳ tên/buổi lặp sau ba ngày.
Đăng nhập đúng bác sĩ của buổi ở các bước 5 và 8; các tài khoản nurse/cashier/lab/
pharmacist giữ nguyên. Tái khám đổi bác sĩ cần lý do và xác nhận đồng ý của bệnh nhân.

Danh sách các khoa khác và tài khoản đầy đủ nằm trong **DEMO-CALENDAR.local.md**
được tạo lúc khởi động; chỉ cần mở file này khi muốn đổi khỏi ví dụ cố định ở trên.
Ngày trong ô lịch theo thiết lập trình duyệt, tiêu đề thẻ buổi hiển thị ngày/tháng/năm.
