# Demo theo thứ tự người trình bày: Hoàng Anh → Vinh → Huy → Lộc → luồng xuyên suốt

Cập nhật **12/11/2026**. File này trả lời câu hỏi: *mỗi người dùng tài khoản nào, mở bệnh nhân nào,
để demo phần của mình mà không phải chờ hay mở màn hình của người khác.*

- **Phần I** — mỗi người demo độc lập trên bệnh nhân đã được seed sẵn đúng trạng thái.
- **Phần II** — một bệnh nhân đi trọn luồng từ tiếp nhận đến nhận thuốc, khi giám khảo yêu cầu demo thật.

Thao tác chi tiết từng nút (tên ô, giá trị nhập) nằm ở [DEMO_UI_TUNG_BUOC.md](DEMO_UI_TUNG_BUOC.md);
file này chỉ ra **điểm bắt đầu** và **mục cần làm** trong hướng dẫn đó.

---

## 0. Chuẩn bị một lần trước buổi demo

1. `scripts/run/start-backend.bat` (nên dùng `-Fast` để giữ dữ liệu) → backend **tự seed toàn bộ**
   bệnh nhân demo cho cả 4 người, chờ lần lượt các dòng
   **FOLLOW-UP SOURCE READY**, **CASHIER DEMO READY**, **STAGED DEMO READY**, **CALENDAR READY**, **READY**.
   Seed lượt khám chỉ tạo được **trong giờ khám 07:00–16:45 (giờ Việt Nam)**. Nếu bật backend ngoài giờ,
   **không cần khởi động lại**: khi phòng khám mở, nhấp đúp **`scripts/run/seed-demo.bat`** để seed bổ sung.
2. `scripts/run/start-frontend.bat` → mở **http://127.0.0.1:3000/login**.
3. Mở file **`DEMO-STAGED.local.md`** trong thư mục evidence mà backend in ra (dòng `STAGED DEMO READY: …`).
   File liệt kê đúng tên, CCCD và mã phiếu/hồ sơ/xét nghiệm/đơn của ngày hôm đó.

| Dòng trên cửa sổ backend | Ý nghĩa |
|---|---|
| `STAGED DEMO READY` | Đủ 5 bệnh nhân seed theo điểm bàn giao (bảng mục I.0) |
| `STAGED DEMO PARTIAL: …` | Có ca chưa chuẩn bị được; `DEMO-STAGED.local.md` ghi **LỖI** và lý do ở đúng dòng đó. Các ca khác vẫn dùng được |
| `PARTIAL: Run again 07:00-16:45…` | Khởi động ngoài giờ khám: chỉ có lịch, **chưa có** bệnh nhân seed. Trong giờ khám chạy `scripts/run/seed-demo.bat` (backend vẫn chạy) |

> **Không khởi động lại backend giữa các phần.** Chế độ thường sẽ phục hồi lại dữ liệu và mất
> những gì vừa thao tác. Với `-Fast`, chạy lại cùng ngày sẽ đọc lại đúng các ca đã seed, không tạo trùng.

**Mật khẩu mọi tài khoản demo:** `MediFlowDemo@2026`. Đăng nhập bằng **email**.

### 0.1. Tài khoản nhân viên

| Vai | Email | Người dùng chính |
|---|---|---|
| ADMIN | `demo.staff01@example.test` | Hoàng Anh, Huy (Report) |
| NURSE (tiếp nhận, quầy khoa) | `demo.staff04@example.test` | Hoàng Anh, Vinh |
| DOCTOR — Lê Bác Sĩ Demo, Khoa Nội tổng quát Demo | `demo.staff03@example.test` | Vinh, Huy |
| LAB_TECH nhập kết quả | `demo.staff15@example.test` | Vinh |
| LAB_TECH thứ hai, phát hành | `demo.staff16@example.test` | Vinh |
| PHARMACIST | `demo.staff17@example.test` | Huy |
| CASHIER | `demo.staff19@example.test` | Lộc |

### 0.2. Bệnh nhân và việc của từng người

```mermaid
flowchart LR
  subgraph HA["Hoàng Anh · Gateway · Organization · Patient"]
    P10["patient10 Ngô Thảo Vy<br/>chưa có tài khoản"]
  end
  subgraph V["Vinh · Clinical · Lab"]
    P03["patient03 Lê Minh Châu<br/>đã thu phí khám"]
    P04["patient04 Phạm Ngọc Dung<br/>CBC đã thu tiền"]
    P05["patient05 Võ Quốc Huy<br/>CBC đã phát hành"]
  end
  subgraph H["Huy · Pharmacy · Report"]
    P06["patient06 Đặng Thanh Mai<br/>đang khám, có chẩn đoán"]
    P07["patient07 Bùi Gia Nam<br/>đơn đã thu tiền"]
  end
  subgraph L["Lộc · Billing · Notification"]
    P08["patient08 Đỗ Thị Phương<br/>phí khám chờ thu · tạo ngay trước lúc demo"]
    C30["case 882.30<br/>Lab + thuốc chờ thu"]
    C83["case 883.30<br/>lượt đã hoàn tất"]
  end
  subgraph E["Luồng xuyên suốt"]
    P01["patient01 Nguyễn Văn An<br/>dự phòng patient09"]
  end
```

| Bệnh nhân | CCCD | Ngày sinh | Trạng thái lúc khởi động | Dành cho |
|---|---|---|---|---|
| Lê Minh Châu Demo (patient03) | `999881000003` | 12/04/1988 | Đã thu phí khám, phiếu **đã xác nhận**, chưa có số phòng | Vinh — I.2a |
| Phạm Ngọc Dung Demo (patient04) | `999881000004` | 12/04/1989 | Đang khám, CBC **đã thu tiền**, Lab ở trạng thái sẵn sàng | Vinh — I.2b |
| Võ Quốc Huy Demo (patient05) | `999881000005` | 12/04/1990 | Đang khám, CBC **đã phát hành** | Vinh — I.2c |
| Đặng Thanh Mai Demo (patient06) | `999881000006` | 12/04/1991 | Đang khám, đã có chẩn đoán, chưa có đơn | Huy — I.3a |
| Bùi Gia Nam Demo (patient07) | `999881000007` | — | Đơn thuốc **đã thu tiền**, chưa xuất | Huy — I.3b |
| Đỗ Thị Phương Demo (patient08) | `999881000008` | — | Không seed sẵn; Lộc tạo khoản phí khám ngay trước lúc demo | Lộc — I.4a |
| Demo LIVE_PHARMACY_BILLING_DEMO | `999882000030` | — | Lab 36.000 + thuốc 20.000 **chờ thu** | Lộc — I.4b |
| Demo HIST_2025_COMPLETED_AND_REFUNDS | `999883000030` | — | Lượt đã thu đủ, có kết quả Lab, đã nhận thuốc, đã hoàn tất | Lộc — I.4c, I.4d |
| Ngô Thảo Vy Demo (patient10) | `999881000010` | — | Có hồ sơ, **chưa có tài khoản** | Hoàng Anh — I.1c |
| Nguyễn Văn An Demo (patient01) | `999881000001` | 12/04/1986 | Không động tới | Phần II |
| Hồ Minh Quân Demo (patient09) | `999881000009` | — | Không động tới | Dự phòng phần II |

Mỗi bệnh nhân chỉ có **một lượt khám trong ngày**. Không dùng bệnh nhân của người khác để demo phần
của mình, nếu không người sau sẽ không còn đúng trạng thái.

---

## Phần I — Demo theo từng người

### I.1. HOÀNG ANH — Gateway, Organization, Patient (8–10 phút)

Không phụ thuộc ai. Slide: Functional Requirements → Use case → Kiến trúc → Gateway.

**a. Gateway** (tài khoản `demo.staff01@example.test`)

1. Mở **http://127.0.0.1:28761** (Eureka): chỉ các service đã đăng ký —
   GATEWAY, CORE-SERVICE, CLINICAL-SERVICE, LAB-SERVICE, PHARMACY-SERVICE, BILLING-SERVICE, SUPPORT-SERVICE.
2. Mở thẳng **http://127.0.0.1:28080/api/v1/patients** trên thanh địa chỉ (trình duyệt không gửi token) →
   **401** `AUTH_UNAUTHORIZED`, envelope `ApiResponse` có `correlationId`: Gateway chặn trước khi tới Core.
3. Đăng nhập ADMIN, bật **DevTools → Network**, mở **Bệnh nhân**:
   - request đi `/api/v1/patients` cùng origin, không gọi cổng 8089;
   - response header **`X-Correlation-Id`** — cùng mã xuất hiện trong log Core.
4. Đăng xuất, đăng nhập **CASHIER** `demo.staff19@example.test` trên **http://127.0.0.1:3000**,
   mở DevTools → **Console**, dán:

   ```js
   fetch('/api/v1/patients', {headers: {Authorization: 'Bearer ' + localStorage.getItem('mediflow.accessToken')}})
     .then(r => r.json()).then(console.log)
   ```

   → **403** `AUTH_FORBIDDEN`: token hợp lệ nhưng CASHIER không nằm trong ma trận role của route này
   (chỉ ADMIN/DOCTOR/NURSE). Gateway mặc định chặn; service phía sau vẫn kiểm `@PreAuthorize` lần nữa.
5. (Tùy chọn) Trang **Quên mật khẩu** gửi quá 5 lần trong 15 phút từ cùng máy → **429**:
   giới hạn tần suất cho endpoint công khai.

**b. Organization** (ADMIN `demo.staff01`): **Tổ chức → Khoa Nội tổng quát Demo**, nhân viên
**Lê Bác Sĩ Demo**, tài khoản. Tùy chọn: bổ nhiệm/miễn nhiệm trưởng khoa —
[DEMO_UI_TUNG_BUOC.md mục 2.2](DEMO_UI_TUNG_BUOC.md#22-tùy-chọn-bổ-nhiệmmiễn-nhiệm-trên-ui).

**c. Patient và tài khoản** (NURSE `demo.staff04`)

1. **Bệnh nhân → tìm `999881000001`**: hồ sơ cũ, đã có tài khoản; khối tài khoản hiện nút
   **Cấp lại mật khẩu tạm qua email** (đã cấp lần đầu). **Không bấm**, vì phần II dùng mật khẩu này.
2. **Tìm `999881000010` → Ngô Thảo Vy Demo**: chưa có tài khoản → **Cấp tài khoản bệnh nhân**.
   Hệ thống tạo tài khoản bằng email hồ sơ và gửi mật khẩu tạm qua thư.
3. Ngay sau đó, nút đổi thành **Cấp lại mật khẩu tạm qua email** và hiện dòng **Thư mật khẩu tạm**:
   *Đang gửi…* rồi tự chuyển sang **Gửi thành công** (xanh) hoặc **Gửi thất bại** (đỏ, kèm nút
   **Gửi lại thư**), không cần bấm kiểm tra. Nêu: lần đầu là *cấp tài khoản*, các lần sau là *cấp lại*;
   mật khẩu không bao giờ hiện trên UI, chỉ đi qua email. Mở `/demo-mailbox` để cho xem thư vừa tới.
4. (Tùy chọn) **Thêm bệnh nhân** mới theo [mục 3A](DEMO_UI_TUNG_BUOC.md#a-bệnh-nhân-mới-tạo-hồ-sơ-và-cấp-tài-khoản)
   → event `patient.created`.

> patient10 chỉ "chưa có tài khoản" ở lần chạy đầu. Nếu đã cấp ở buổi trước với `-Fast`, nút hiện
> ngay **Cấp lại…**; khi đó demo cấp lần đầu bằng một bệnh nhân vừa tạo ở bước 4.

### I.2. VINH — Clinical, Lab (10–12 phút)

Ba bệnh nhân, ba điểm vào. Không cần chờ Lộc thu tiền.

**a. Đã thu phí khám → vào khám** — Lê Minh Châu Demo `999881000003`

1. (Tùy chọn, minh họa đăng ký) Mở **/appointments/reception**, chọn buổi
   **Lê Bác Sĩ Demo / 07:00–17:00**, bấm **Tải phiếu trong buổi**: phiếu của Lê Minh Châu đã ở
   trạng thái **đã xác nhận**, tức là thu ngân đã thu phí khám. Đây là chỗ thay cho bước chờ Lộc.
2. NURSE `demo.staff04` → **Quầy điều dưỡng khoa** → **Tải hàng đợi** → Lê Minh Châu ở nhóm
   **Chờ cấp số phòng** → **Cấp số phòng** (tùy chọn **Ghi sinh hiệu**) — [mục 5b](DEMO_UI_TUNG_BUOC.md#5b-vinh--nurse--clinical-quầy-điều-dưỡng-khoa-cấp-số-phòng).
3. DOCTOR `demo.staff03` → **Lịch khám của tôi** → **Xem phiếu** → Lê Minh Châu →
   **Bắt đầu khám → Mở hồ sơ khám** → thêm chẩn đoán → **Chỉ định xét nghiệm** CBC →
   **Lập chỉ định → Tạo yêu cầu từ chỉ định** — [mục 6](DEMO_UI_TUNG_BUOC.md#6-vinh--doctor--clinical-khám-và-lập-yêu-cầu-lab).
   Khoản Lab của ca này để chờ thu; Vinh **không** đợi, chuyển sang ca b.

**b. CBC đã thu tiền → nhập và duyệt kết quả** — Phạm Ngọc Dung Demo `999881000004`

1. LAB_TECH `demo.staff15` → **/lab** → **Công thức máu · Phạm Ngọc Dung Demo**
   (hoặc mã `testId` trong `DEMO-STAGED.local.md`) → đã có xác nhận thu → **Bắt đầu xét nghiệm**.
2. **Thu nhận bệnh phẩm** `WHOLE_BLOOD_EDTA` → **Tiếp nhận** → nhập WBC 7.0, RBC 4.8, HGB 140,
   HCT 0.42, PLT 250 → **Lưu nháp → Gửi duyệt**.
3. Bấm **Phát hành** bằng chính `demo.staff15` → bị từ chối (một người không tự duyệt).
4. Đăng xuất → `demo.staff16` → cùng URL → **Phát hành** → `lab.result.created` —
   [mục 8](DEMO_UI_TUNG_BUOC.md#8-vinh--lab_tech--lab-bệnh-phẩm-nhập-kết-quả-và-duyệt-hai-người).

**c. Đã có kết quả → kê đơn và hoàn tất hồ sơ** — Võ Quốc Huy Demo `999881000005`

1. DOCTOR `demo.staff03` → **Lịch khám của tôi** → Võ Quốc Huy → **Mở hồ sơ khám**
   (hoặc `/records/<recordId>` trong `DEMO-STAGED.local.md`).
2. **Xem xét nghiệm của lượt khám** → CBC đã phát hành của đúng lượt.
3. **/pharmacy/prescriptions/new** → bệnh nhân `999881000005`, hồ sơ đang mở hôm nay,
   **Thuốc minh họa A** × 2 → **Tạo đơn thuốc**.
4. Quay lại hồ sơ → **Hoàn tất khám**: **Theo dõi ngoại trú**, tích xác nhận →
   **Hoàn tất hồ sơ khám** → `medicalrecord.completed`.
   Nêu: bác sĩ hoàn tất ngay sau khi kê đơn; Pharmacy vẫn thu tiền và cấp thuốc sau đó,
   không cần chờ bệnh nhân thanh toán. Chỉ Lab chưa xong mới chặn hoàn tất.

### I.3. HUY — Pharmacy, Report (6–8 phút)

**a. Kê đơn** — Đặng Thanh Mai Demo `999881000006` (vai DOCTOR `demo.staff03`)

1. **/pharmacy/prescriptions/new** → bệnh nhân `999881000006` → hồ sơ đang mở hôm nay.
2. Tìm **DEMO-HIST-2025** → chọn **Thuốc minh họa A** (không chọn thuốc *hết tồn*/*cảnh báo tồn thấp*)
   → số lượng 2 → **Tạo đơn thuốc** → `prescription.created`, Billing tạo khoản PRESCRIPTION.
   Đơn này để chờ thu; Huy không đợi, chuyển sang ca b — [mục 10](DEMO_UI_TUNG_BUOC.md#10-huy--doctor--pharmacy-tạo-đơn-thuốc).

**b. Đơn đã thu tiền → xuất thuốc** — Bùi Gia Nam Demo `999881000007` (PHARMACIST `demo.staff17`)

1. **/pharmacy/prescriptions** → đơn của Bùi Gia Nam (mã `prescriptionId` trong `DEMO-STAGED.local.md`).
2. **Tải lại trạng thái**: đơn đã thu đủ → **Xuất thuốc → Xác nhận xuất thuốc** → `prescription.filled`;
   tồn Thuốc minh họa A giảm 2 — [mục 12](DEMO_UI_TUNG_BUOC.md#12-huy--pharmacist--pharmacy-cấp-phát-thuốc).

**c. Report** (ADMIN `demo.staff01`): **/reports/outpatient** → **Theo ngày** →
**Tìm kỳ có dữ liệu → Dùng kỳ này → Xem báo cáo**. Kỳ đối soát đã được seed lúc khởi động —
[mục 15](DEMO_UI_TUNG_BUOC.md#15-huy--admin-hoặc-trưởng-khoa--report-cuối-luồng).

### I.4. LỘC — Billing, Notification (8–10 phút)

**a. Thu phí khám.** Phiếu chưa thu chỉ **giữ suất 15 phút**, nên khoản này được tạo **ngay trước lúc Lộc lên**:

```powershell
.\scripts\run\seed-demo.bat -ExamChargeNow
```

Kỳ vọng: `EXAM CHARGE READY: Đỗ Thị Phương Demo (CCCD 999881000008) …`. Sau đó CASHIER `demo.staff19` →
**Thu phí lịch khám** → chọn Đỗ Thị Phương → **Tiền mặt → Thu phí lịch khám** (50.000 VND) →
`payment.completed` → `outpatient.financial.eligibility.granted` — [mục 5](DEMO_UI_TUNG_BUOC.md#5-lộc--cashier--billing-thu-phí-khám).
Chạy lại lệnh trong cùng ngày không tạo phiếu thứ hai.

**b. Thu Lab và thuốc** — Demo LIVE_PHARMACY_BILLING_DEMO `999882000030`

1. **Thu phí xét nghiệm & thuốc** → **Tìm bệnh nhân chờ thu** `999882000030`.
2. **Xét nghiệm** → **Chọn khoản** → 36.000 VND → **Tiền mặt → Xác nhận thu theo báo giá** → `financial.clearance.granted`.
3. **Đơn thuốc** → **Chọn khoản** → 20.000 VND → **Xác nhận thanh toán**.

**c. Lịch sử thu**: tìm `999883000030` → một lượt đã thu đủ cả ba khoản (khám, Lab, thuốc).

**d. Notification** — đăng xuất, đăng nhập bệnh nhân `demo.case.883.30@example.test`:
**/notifications** (kết quả Lab, hồ sơ hoàn tất), **/records/mine**, **/lab/mine**.
Nêu chuỗi `lab.result.created` / `medicalrecord.completed` → `notification.q` → lưu IN_APP.

---

## Phần II — Một bệnh nhân xuyên suốt (khi giám khảo yêu cầu demo thật)

**Bệnh nhân:** Nguyễn Văn An Demo, CCCD `999881000001`, tài khoản `demo.patient01@example.test`.
Dùng người này vì đã có tài khoản và mật khẩu biết trước, nên cuối luồng đăng nhập được bằng vai bệnh nhân.
Nếu patient01 đã có lượt hôm nay, dùng **Hồ Minh Quân Demo `999881000009`** (bỏ bước đăng nhập bệnh nhân,
hoặc cấp tài khoản cho người này như I.1c).

| # | Người | Đăng nhập | Việc | Hướng dẫn chi tiết |
|---|---|---|---|---|
| 1 | Hoàng Anh | `demo.staff04` | Tìm `999881000001` → **Tiếp tục đăng ký khám** | [3B](DEMO_UI_TUNG_BUOC.md#b-bệnh-nhân-cũ-đến-khám-đợt-mới) |
| 2 | Vinh | `demo.staff04` | Chọn buổi Lê Bác Sĩ Demo, **Khám đợt mới**, **Đăng ký và giữ chỗ** | [4](DEMO_UI_TUNG_BUOC.md#4-vinh--nurse--clinical-chọn-buổi-và-đăng-ký-khám) |
| 3 | Lộc | `demo.staff19` | Thu phí khám 50.000 (trong 15 phút) | [5](DEMO_UI_TUNG_BUOC.md#5-lộc--cashier--billing-thu-phí-khám) |
| 4 | Vinh | `demo.staff04` | Cấp số phòng, ghi sinh hiệu | [5b](DEMO_UI_TUNG_BUOC.md#5b-vinh--nurse--clinical-quầy-điều-dưỡng-khoa-cấp-số-phòng) |
| 5 | Vinh | `demo.staff03` | Bắt đầu khám, chẩn đoán Z00, chỉ định CBC | [6](DEMO_UI_TUNG_BUOC.md#6-vinh--doctor--clinical-khám-và-lập-yêu-cầu-lab) |
| 6 | Lộc | `demo.staff19` | Thu CBC 36.000 | [7](DEMO_UI_TUNG_BUOC.md#7-lộc--cashier--billing-thu-phí-cbc) |
| 7 | Vinh | `demo.staff15` → `demo.staff16` | Bệnh phẩm, nhập, gửi duyệt → phát hành | [8](DEMO_UI_TUNG_BUOC.md#8-vinh--lab_tech--lab-bệnh-phẩm-nhập-kết-quả-và-duyệt-hai-người) |
| 8 | Vinh | `demo.staff03` | Đọc CBC trong hồ sơ | [9](DEMO_UI_TUNG_BUOC.md#9-vinh--doctor--clinical-đọc-lại-kết-quả-trong-cùng-lượt) |
| 9 | Huy | `demo.staff03` | Kê đơn Thuốc minh họa A × 2 | [10](DEMO_UI_TUNG_BUOC.md#10-huy--doctor--pharmacy-tạo-đơn-thuốc) |
| 10 | Vinh | `demo.staff03` | Hoàn tất hồ sơ (không cần chờ thu tiền thuốc) | [13](DEMO_UI_TUNG_BUOC.md#13-vinh--doctor--clinical-hoàn-tất-hồ-sơ-hướng-dẫn-ra-về) |
| 11 | Lộc | `demo.staff19` | Thu tiền thuốc 20.000 | [11](DEMO_UI_TUNG_BUOC.md#11-lộc--cashier--billing-thu-tiền-thuốc) |
| 12 | Huy | `demo.staff17` | Xuất thuốc | [12](DEMO_UI_TUNG_BUOC.md#12-huy--pharmacist--pharmacy-cấp-phát-thuốc) |
| 13 | Lộc | `demo.patient01` | Thông báo, kết quả khám, kết quả Lab | [14](DEMO_UI_TUNG_BUOC.md#14-lộc--patient--notification-và-kết-quả-cá-nhân) |
| 14 | Huy | `demo.staff01` | Report ngoại trú | [15](DEMO_UI_TUNG_BUOC.md#15-huy--admin-hoặc-trưởng-khoa--report-cuối-luồng) |

Tổng thu của lượt: 50.000 + 36.000 + 20.000 = **106.000 VND**, ba biên nhận riêng.
Bước 10 có thể đặt sau bước 12 nếu muốn khớp thứ tự slide; hệ thống cho phép cả hai.

```mermaid
sequenceDiagram
  autonumber
  participant N as Nurse (Hoàng Anh/Vinh)
  participant C as Cashier (Lộc)
  participant D as Doctor (Vinh/Huy)
  participant L as Lab tech (Vinh)
  participant P as Pharmacist (Huy)
  participant B as Patient (Lộc)
  N->>N: Tìm hồ sơ, đăng ký, giữ suất 15'
  N->>C: Phiếu chờ thu
  C->>N: Đã thu phí khám, phiếu xác nhận
  N->>D: Cấp số phòng
  D->>C: Chỉ định CBC
  C->>L: Đã thu CBC
  L->>D: KTV 2 phát hành kết quả
  D->>C: Kê đơn, hoàn tất hồ sơ
  C->>P: Đã thu tiền thuốc
  P->>B: Xuất thuốc
  B->>B: Xem thông báo, kết quả
```

---

## Xử lý nhanh khi kẹt

| Hiện tượng | Cách xử lý |
|---|---|
| Không có dòng `STAGED DEMO READY` | Backend khởi động ngoài 07:00–16:45, hoặc một ca lỗi: đọc `DEMO-STAGED.local.md`. Trong giờ khám, chạy `scripts/run/seed-demo.bat` (backend vẫn chạy) để seed lại phần còn thiếu |
| Ca của mình đã bị người khác thao tác | Chuyển sang phần II với patient09, hoặc demo trên ca kế tiếp cùng người (ví dụ Vinh dùng ca c thay ca b) |
| `--exam-charge-now` báo lỗi phiếu đã có | Phiếu hôm nay đã tạo; nếu đã quá 15 phút thì hết hạn giữ suất, demo thu phí bằng phần II bước 2–3 |
| Lab ca b không hiện xác nhận thu | **Tải lại trạng thái**; xác nhận thu đến qua event, thường vài giây |
| Không chạy `lab_v2_walkthrough.py` trong ngày demo | Script đó tự chọn bệnh nhân "Demo" và có thể lấy trùng bệnh nhân seed ở trên |

Seed do `seed_demo.py` (gọi `prepare_ui_demo` → `prepare_cashier_demo` → `prepare_staged_demo`) thực hiện qua Gateway bằng đúng các lệnh UI gửi, không ghi SQL nghiệp vụ.
Nhật ký ở `evidence/…/staged-journal.local.json`; chạy lại cùng ngày dùng lại cùng khóa chống trùng.
