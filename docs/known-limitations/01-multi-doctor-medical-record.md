# 01 — Hồ sơ bệnh án nhiều bác sĩ (multi-doctor medical record)

> Trạng thái: **đề xuất, chưa triển khai** · Context: Clinical (chủ dữ liệu), ảnh hưởng Lab,
> Pharmacy, Billing, Frontend, Mobile · Ngày lập: 2026-11-08

## 1. Hiện trạng

Trong `clinical-service`, `MedicalRecord` (`domain/model/MedicalRecord.java`) là **một lượt
khám** và mang **đúng một** `doctorId` cùng `departmentId`, `appointmentId`, `examinationDate`,
`diagnoses`, `status` (`OPEN` / `COMPLETED`) và `disposition`.

Quyền thao tác dựa trên phép so sánh bằng `record.doctorId == appointment.doctorId` (hoặc
`== actor.staffId`). Phép so sánh này xuất hiện ở nhiều nơi:

- `MedicalRecordApplicationService` (≈ dòng 190) và `requireDoctorDepartment(...)` khi tạo/cập nhật
- `ClinicalLabOrderApplicationService` (≈ 100), `ClinicalLabCompletionGuard` (≈ 32, 45)
- `ClinicalCareFinanceApplicationService` (≈ 204, 249)
- `FollowUpRecommendationApplicationService` (≈ 121, 133, 147, 187)

Liên kết giữa các lần khám hiện chỉ có:

- `OutpatientRegistration.visitType` = `NEW_VISIT` / `FOLLOW_UP` — chỉ là nhãn, không trỏ về lần
  khám trước;
- `FollowUpRecommendation(sourceRecordId, sourceRegistrationId, patientId, departmentId,
  preferredDoctorId, recommendedFrom…)` — chỉ nối **một** lần khám nguồn với **một** lần hẹn sau;
- `GET /api/v1/records/patient/{patientId}` trả về danh sách phẳng mọi record của bệnh nhân.

## 2. Vấn đề

Ví dụ bệnh nhân A:

| Chuyên khoa | Khám lần đầu | Tái khám | Khám mới (đợt bệnh khác / tái phát) |
|---|---|---|---|
| Tim mạch | BS B | BS C | BS G |
| Tiêu hóa – Gan mật | BS E | BS F | BS T |

Với mô hình hiện tại:

1. **Phân mảnh theo bác sĩ.** Sáu lần khám là sáu record rời, mỗi record "thuộc về" một bác sĩ.
   BS C tái khám nhưng hệ thống không biết record của BS B là bệnh sử của chính đợt bệnh này.
2. **Không có khái niệm "đợt điều trị" theo chuyên khoa.** Không phân biệt được "tái khám tim
   mạch (cùng đợt)" với "khám mới tim mạch (đợt mới)" ngoài nhãn `visitType`.
3. **Quyền gắn với cá nhân.** Các guard `record.doctorId == …` ngầm giả định bác sĩ khám sau phải
   là bác sĩ cũ. Điều này trái thực tế bệnh viện Việt Nam: lịch trực, lịch phòng khám xoay vòng,
   bệnh nhân bốc số và được khám bởi bác sĩ đang ngồi phòng.
4. **Không có góc nhìn tổng.** Bác sĩ gan (E/F/T) cần biết bệnh nhân đang dùng thuốc tim mạch do
   B/C/G kê (tương tác thuốc, chống chỉ định) nhưng chỉ có danh sách phẳng, không theo chuyên khoa.

## 3. Đối chiếu cơ cấu bệnh viện Việt Nam

Cần đối chiếu với văn bản gốc trước khi chốt:

- Luật Khám bệnh, chữa bệnh 15/2023/QH15 và Thông tư 32/2023/TT-BYT về hồ sơ bệnh án.
- Thông tư 46/2018/TT-BYT về hồ sơ bệnh án điện tử.

| Thực tế | Ý nghĩa cho thiết kế |
|---|---|
| Mỗi người bệnh có **một mã người bệnh / mã y tế** duy nhất tại cơ sở; mọi lần khám gắn vào đó. | Bệnh nhân là gốc của toàn bộ hồ sơ (Patient context đã có). |
| Khám ngoại trú: mỗi lần khám có **phiếu khám / sổ khám bệnh**, ghi bác sĩ khám, chẩn đoán (ICD-10), chỉ định, đơn thuốc, hẹn tái khám. | Một **lượt khám (encounter)** có đúng một bác sĩ chịu trách nhiệm — đúng như record hiện nay. |
| Bệnh mạn tính / điều trị kéo dài (tăng huyết áp, đái tháo đường, viêm gan B…) được lập **bệnh án ngoại trú** theo chuyên khoa, nhiều lần khám ghi nối tiếp, do **khoa** quản lý. | Cần cấp **đợt điều trị (care episode)** thuộc **khoa**, không thuộc bác sĩ. |
| **Trưởng khoa** chịu trách nhiệm chuyên môn hồ sơ của khoa; bác sĩ điều trị ký phần mình khám. | Quyền đọc theo khoa; quyền ghi theo lượt khám; trưởng khoa có quyền rà soát. |
| **Tái khám** theo giấy hẹn: bệnh nhân về đúng chuyên khoa, có thể gặp bác sĩ khác theo lịch. | "Bác sĩ ưu tiên" chỉ là gợi ý; tái khám nối vào đợt, không vào bác sĩ. |
| Bệnh viện lưu trữ hồ sơ tập trung (phòng KHTH / lưu trữ); bác sĩ khoa khác xem khi cần cho điều trị. | Cần góc nhìn **hồ sơ tổng** có kiểm soát truy cập và nhật ký. |

## 4. Mô hình đề xuất — ba tầng

```
Patient (Patient context — đã có)
└── PatientChart  "Hồ sơ sức khỏe / bệnh án tổng"      (khung nhìn, không phải bảng mới bắt buộc)
    ├── CareEpisode  "Đợt điều trị"  — Tim mạch, mở bởi BS B, khoa Tim mạch quản lý
    │   ├── Encounter  lượt khám #1  NEW_VISIT  BS B   (= MedicalRecord hiện nay)
    │   └── Encounter  lượt khám #2  FOLLOW_UP  BS C
    ├── CareEpisode  Tim mạch đợt 2 (tái phát)
    │   └── Encounter  NEW_VISIT  BS G
    └── CareEpisode  Tiêu hóa – Gan mật
        ├── Encounter  NEW_VISIT  BS E
        ├── Encounter  FOLLOW_UP  BS F
        └── Encounter  …          BS T
```

### 4.1 `CareEpisode` (mới, Clinical sở hữu)

| Trường | Ghi chú |
|---|---|
| `episodeId` UUID | |
| `patientId` UUID | tham chiếu trần sang Patient |
| `departmentId` UUID | **khoa quản lý** đợt — chủ sở hữu, thay cho bác sĩ |
| `specialtyLabel` / `primaryDiagnosisCode` | ICD-10 chính của đợt (có thể cập nhật khi chẩn đoán xác định) |
| `openedByDoctorId`, `openedAt` | chỉ để lưu vết, **không** dùng để phân quyền |
| `responsibleDoctorId` (tùy chọn) | "bác sĩ phụ trách" — hiển thị và gợi ý tái khám, không độc quyền |
| `status` | `ACTIVE` → `CLOSED` (khỏi / kết thúc điều trị) / `TRANSFERRED` (chuyển khoa, nhập viện) |
| `closedAt`, `closeReason`, `revision` | |

Bất biến:
- Mỗi encounter thuộc **đúng một** episode; episode và encounter phải cùng `patientId` và cùng
  `departmentId`. Hội chẩn liên khoa không chuyển encounter sang khoa khác.
- Một bệnh nhân có thể có nhiều episode `ACTIVE` ở các khoa khác nhau, và nhiều episode trong cùng
  một khoa nếu là các vấn đề khác nhau (khoa quyết định, không ép duy nhất).
- Episode `CLOSED` không nhận encounter mới; "khám mới" sau đó mở episode mới (trường hợp BS G, BS T).

### 4.2 `Encounter` = `MedicalRecord` hiện tại (giữ, bổ sung)

- Thêm `episodeId` (bắt buộc sau khi chuyển đổi).
- Đổi ngữ nghĩa `doctorId` thành **bác sĩ khám của lượt này** (attending of encounter) — vẫn một
  người, vẫn chịu trách nhiệm pháp lý phần mình ghi.
- Sau `COMPLETED`: không sửa nội dung; chỉ thêm **bổ sung (addendum)** có người ký và thời điểm.
  Điều này cho phép BS C ghi nhận xét về lượt khám của BS B mà không ghi đè.

### 4.3 Quy tắc tạo encounter

| Tình huống | Hành vi |
|---|---|
| Tiếp đón `NEW_VISIT` | Khi bác sĩ mở lượt khám, hệ thống liệt kê episode `ACTIVE` cùng khoa của bệnh nhân. Bác sĩ **chọn nối vào đợt** hoặc **mở đợt mới**. Mặc định là mở đợt mới nếu không có đợt đang mở. |
| Tiếp đón `FOLLOW_UP` từ giấy hẹn | `FollowUpRecommendation` thêm `episodeId`; lượt khám tự nối vào episode đó dù bác sĩ khám là ai. `preferredDoctorId` chỉ là gợi ý xếp lịch. |
| `FOLLOW_UP` không có giấy hẹn | Như `NEW_VISIT` nhưng bắt buộc chọn một episode đang mở cùng khoa. |
| Chuyển khoa / nhập viện | Đóng episode với `TRANSFERRED` và tham chiếu tới episode/đợt nội trú mới (nội trú vẫn hoãn trong MVP). |

### 4.4 Phân quyền mới — theo khoa và mối quan hệ điều trị

Thay các guard `record.doctorId == actor`:

| Hành động | Ai được phép |
|---|---|
| Ghi / sửa encounter đang `OPEN` | Bác sĩ khám của chính encounter đó (giữ nguyên quy tắc hiện tại). |
| Chỉ định xét nghiệm, kê đơn, hẹn tái khám | Bác sĩ khám của encounter đang mở; điều kiện phiên khám/lịch giữ nguyên. |
| Đọc toàn bộ episode (mọi encounter, mọi bác sĩ) | Mọi bác sĩ/điều dưỡng **thuộc khoa** của episode. Trưởng khoa luôn được đọc. |
| Đọc tóm tắt chéo chuyên khoa (chẩn đoán, đơn thuốc đang dùng, dị ứng, kết quả XN) | Bác sĩ đang có encounter `OPEN` với bệnh nhân (đang điều trị), mọi khoa. |
| Đọc chi tiết episode khoa khác | "Truy cập khẩn" có lý do bắt buộc và ghi audit (break-the-glass); ADMIN không xem nội dung lâm sàng mặc định. |
| Đóng / mở lại episode | Bác sĩ thuộc khoa; mở lại cần trưởng khoa (tùy chọn chốt ở mục 7). |

Ghi chú bắt buộc theo `docs/ai/07`: `@PreAuthorize` chỉ chặn vai trò. Điều kiện khoa/mối quan hệ
nằm ở application service. Quyền thuộc khoa lấy từ Organization qua REST
(`current-doctor-authority` đã có) — không truy vấn DB Organization.

## 5. Hợp đồng (contract) dự kiến

| Thay đổi | Loại |
|---|---|
| `GET /api/v1/patients/{patientId}/chart` → danh sách episode theo khoa, mỗi episode có tóm tắt các encounter (ngày, bác sĩ, loại, chẩn đoán chính) | mới |
| `GET /api/v1/episodes/{episodeId}` — chi tiết episode, encounter đầy đủ | mới |
| `POST /api/v1/episodes/{episodeId}/close` / `reopen` | mới |
| `POST /api/v1/records` nhận thêm `episodeId` (tùy chọn: không có thì mở episode mới) | mở rộng tương thích |
| `MedicalRecordResponse` thêm `episodeId`, `episodeStatus` | mở rộng tương thích |
| `POST /api/v1/records/{id}/addenda` | mới |
| `FollowUpRecommendation` thêm `episodeId` | mở rộng tương thích |
| Sự kiện `clinical.episode.opened` / `closed`; `record.completed` mang thêm `episodeId` | mở rộng tương thích |
| Gateway: thêm luật vào `RouteAuthorizationFilter` và `route-role-contracts.csv` | |

Lab, Pharmacy, Billing tiếp tục tham chiếu `recordId` (encounter). Không bắt buộc đổi phía consumer.
Họ có thể bỏ qua `episodeId` mới. Billing **không** nhận dữ liệu lâm sàng của episode.

Theo AGENTS.md, phải khóa fixture DTO/serializer/migration hiện có (M6-02) trước khi đổi hợp đồng.

## 6. Kế hoạch chuyển đổi (theo pha)

1. **Pha 0 — Chốt thiết kế.** Trả lời mục 7; cập nhật `docs/plans/01`, `02`, `03`, `04`.
2. **Pha 1 — Schema (Clinical Flyway `V13__care_episode.sql`).**
   - Tạo `care_episode`; thêm `medical_record.episode_id` (nullable), `follow_up_recommendation.episode_id`.
   - Backfill:
     - Nhóm record theo `(patient_id, department_id)`.
     - Nối chuỗi qua `follow_up_recommendation.source_record_id`: các record trong một chuỗi hẹn tái khám vào cùng một episode.
     - Record `NEW_VISIT` không thuộc chuỗi nào mở episode riêng.
     - Episode có record cuối đã `COMPLETED` quá N ngày (đề xuất 180) thì đặt `CLOSED`.
   - Sau backfill, đặt `episode_id NOT NULL` + FK + index `(patient_id, department_id, status)`.
   - Cập nhật script seed demo cho schema-aware như bài học V19 của Organization.
3. **Pha 2 — Domain & quyền.**
   - Thêm `CareEpisode` aggregate, port, adapter.
   - Thay các guard `doctorId ==` ở mục 1 bằng chính sách mới (một `ClinicalAccessPolicy` dùng chung).
   - Viết test cho từng quy tắc ở 4.1–4.4 theo `docs/ai/09`, gồm kịch bản 6 bác sĩ của mục 2.
4. **Pha 3 — API & gateway.** Endpoint mục 5, security test cho từng route, cập nhật contract CSV.
5. **Pha 4 — Frontend** (Vinh: Clinical).
   - Trang hồ sơ bệnh nhân chuyển sang dạng "Hồ sơ tổng": nhóm theo khoa → đợt điều trị → dòng thời gian lượt khám, ghi rõ bác sĩ từng lượt.
   - Khi mở lượt khám có hộp chọn "Nối vào đợt …" / "Mở đợt mới".
   - Giữ quy tắc không hiển thị giá tiền trong hồ sơ.
6. **Pha 5 — Mobile.** Màn hồ sơ bệnh nhân đọc `/chart` (chỉ đọc).
7. **Pha 6 — Nghiệm thu.** Kịch bản acceptance trong `docs/plans/06`. Bệnh nhân A, 2 chuyên khoa, 6 bác sĩ, 3 episode:
   - BS C thấy toàn bộ lượt của BS B;
   - BS F không sửa được lượt của BS E;
   - BS T thấy tóm tắt thuốc tim mạch;
   - Bác sĩ khoa khác không có encounter mở thì không đọc được chi tiết.

## 7. Câu hỏi còn mở

1. Khoa có cho nhiều episode `ACTIVE` song song (ví dụ hai vấn đề khác nhau cùng khoa) hay chỉ một?
2. Thời hạn tự đóng episode không hoạt động (180 ngày?), hay chỉ bác sĩ đóng thủ công?
3. Mở lại episode `CLOSED` cần trưởng khoa duyệt hay bác sĩ trong khoa tự làm?
4. Có cần "bác sĩ phụ trách" cố định cho bệnh mạn tính (quản lý bệnh nhân theo bác sĩ) không?
5. Điều dưỡng được đọc tóm tắt chéo khoa hay chỉ trong khoa?
6. Phạm vi "truy cập khẩn" và ai rà soát nhật ký audit?

## 8. Ngoài phạm vi

- Nội trú / phẫu thuật (vẫn hoãn trong MVP) — mô hình episode được thiết kế để mở rộng sau.
- Chữ ký số / ký điện tử bệnh án theo TT 46/2018 — chỉ lưu người ký và thời điểm ở MVP.
- Liên thông hồ sơ sức khỏe điện tử quốc gia.
