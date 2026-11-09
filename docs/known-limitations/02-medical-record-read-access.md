# 02 — Quyền đọc lịch sử khám theo khoa và toàn cơ sở

> Ngày đối chiếu: **2026-11-09** · Baseline: `3f3dcb8a` · Context: Clinical, Gateway,
> Frontend medical-record · Trạng thái: **ghi nhận source và yêu cầu; chưa sửa production**.
> Đã đọc source/test, chưa chạy thử API/UI hoặc bộ test trong lần rà soát này.

## 1. Yêu cầu người dùng và kết luận

Yêu cầu ngày 2026-11-09:

- Làm rõ mới nhất cùng ngày: bác sĩ được đọc lịch sử **cả khác khoa** để phục vụ công việc
  được giao; không giới hạn đọc tuyệt đối theo khoa hoặc bác sĩ tác giả. Yêu cầu này thay
  đề xuất trước đó chỉ đọc cùng khoa; quyền đọc không đồng nghĩa quyền sửa.
- Admin xem hồ sơ khám của bệnh nhân ở **tất cả các khoa**.
- Bác sĩ không sửa đè nội dung của bác sĩ khác. Với hồ sơ đã đóng, đề xuất cho chính tác giả
  sửa cần chuyển thành quy trình bổ sung/đính chính có kiểm soát, không mở quyền sửa trực tiếp.

| Khả năng | Source hiện tại | Đánh giá |
|---|---|---|
| Bác sĩ đọc lượt của bác sĩ khác cùng khoa | Có qua GET danh sách và chi tiết record | Đáp ứng khả năng đọc, nhưng chưa phải chính sách giới hạn theo khoa |
| Chỉ đọc record thuộc khoa bác sĩ | Chưa có ở hai API record đã kiểm tra | Bác sĩ hiện còn đọc xuyên khoa nếu biết/chọn patientId hoặc recordId |
| Admin đọc record của mọi khoa của bệnh nhân | Có qua cùng hai API | Có ở cấp hồ sơ khám; chưa chứng minh mọi tài liệu liên quan có cùng quyền |
| Điều dưỡng đọc record | Cũng được hai API cho phép, chưa lọc khoa tại use case | Phải chốt phạm vi riêng, không tự áp yêu cầu dành cho bác sĩ |
| Bác sĩ khác đọc giấy hẹn của bác sĩ nguồn | Bị chặn tại use case giấy hẹn, kể cả cùng khoa | Không đồng nhất với quyền đọc record |
| Nhóm danh sách theo khoa/quá trình điều trị | Chưa có trong MedicalRecordTable | Đang là danh sách phẳng |

Các record đã tồn tại của lần khám/tái khám đều được truy vấn theo patientId; API này không
lọc loại lượt hoặc trạng thái OPEN/COMPLETED. Lịch hẹn/đăng ký chưa bắt đầu khám và chưa có
MedicalRecord không tự trở thành mục trong danh sách hồ sơ. Không diễn giải "tất cả lượt"
thành cả lịch hẹn hủy, chưa đến hoặc chưa tạo nội dung khám.

## 2. Bằng chứng theo đường đi dữ liệu

Các đường dẫn dưới đây tính từ thư mục tài liệu này.

### 2.1 Gateway và controller: quyền theo role

- [RouteAuthorizationFilter](../../backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java):
  GET `/api/v1/records/{id}` và `/api/v1/records/patient/{patientId}` cho ADMIN, DOCTOR, NURSE.
- [route-role-contracts.csv](../../backend/gateway/src/test/resources/route-role-contracts.csv):
  khai báo cùng tập role cho hai route.
- [MedicalRecordController](../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/MedicalRecordController.java):
  cả hai GET dùng `@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")`.

Đây là chặn theo vai trò; không có điều kiện khoa/bác sĩ trong hai handler.

### 2.2 Use case và persistence: chỉ lọc bệnh nhân

- [MedicalRecordApplicationService](../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/MedicalRecordApplicationService.java):
  `getById` gọi `records.findById(id)`; `byPatient` gọi `records.findByPatient(patientId)` rồi map DTO.
  Hai hàm đọc không gọi CurrentClinicalActorPort/authority, không so khoa hoặc quan hệ điều trị.
- [MedicalRecordPersistenceAdapter](../../backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/persistence/adapter/MedicalRecordPersistenceAdapter.java):
  `findByPatient` chuyển thẳng sang `findByPatientIdOrderByExaminationDateDesc(patientId)`.
- [MedicalRecordJpaRepository](../../backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/persistence/repository/MedicalRecordJpaRepository.java):
  truy vấn danh sách không có departmentId/doctorId/status; `findAggregateById` chỉ lọc recordId.

Ví dụ bệnh nhân có lượt tim mạch do B, C và lượt tiêu hóa do E: GET danh sách của bác sĩ B/C
hiện trả cả ba record, tương tự admin. Vì thế nhận định "chỉ bác sĩ tạo mới xem được record"
không đúng cho hai API này. Thiếu CareEpisode không ngăn đọc record của bác sĩ khác.

### 2.3 Giao diện tra cứu hiện tại

- [Trang /records](../../frontend/src/app/(dashboard)/records/page.tsx) và
  [trang chi tiết](../../frontend/src/app/(dashboard)/records/[recordId]/page.tsx):
  RoleGate cho ADMIN, DOCTOR, NURSE.
- [medical-record/api.ts](../../frontend/src/features/medical-record/api.ts): `byPatient` và
  `getById` gọi đúng hai API trên qua HTTP client chung.
- [MedicalRecordTable](../../frontend/src/features/medical-record/components/MedicalRecordTable.tsx):
  chọn bệnh nhân rồi gọi byPatient; hiển thị trực tiếp `records.map(...)`, không lọc khoa/bác sĩ.
  Bảng có ngày khám, hồ sơ, bác sĩ, triệu chứng, chẩn đoán; chưa nhóm/lọc theo khoa, chưa có
  cột khoa hoặc chuỗi quá trình điều trị. Chi tiết record có hiển thị khoa và bác sĩ.

Kết luận này áp dụng cho workspace Hồ sơ khám `/records`, không khẳng định mọi trang Patient,
mobile hay mọi tài liệu con đã có đường dẫn và chính sách tương đương.

### 2.4 Ngoại lệ quan trọng: giấy hẹn tái khám

- [FollowUpRecommendationApplicationService](../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/FollowUpRecommendationApplicationService.java):
  `authorizeRead` nhánh DOCTOR yêu cầu `actor.staffId == record.doctorId` và current doctor authority.
  Nhánh ADMIN kiểm tra authority hiện hành; NURSE còn kiểm tra staff/khoa.
- [FollowUpRecommendationPanel](../../frontend/src/features/medical-record/components/FollowUpRecommendationPanel.tsx):
  khi bật panel sẽ gọi API giấy hẹn; HTTP 404 với `RESOURCE_NOT_FOUND` được hiển thị như không có
  đề xuất. Vì vậy bác sĩ C có thể đọc record của B nhưng không đọc được giấy hẹn của B, và UI
  có thể không phân biệt giấy hẹn bị giới hạn quyền với giấy hẹn chưa tồn tại.

Không suy rộng quyền đọc record thành quyền đọc toàn bộ đơn thuốc, kết quả Lab, giấy hẹn,
tệp đính kèm hoặc dữ liệu của context khác; từng hợp đồng cần được kiểm tra và nghiệm thu riêng.

### 2.5 Test đang có và giới hạn chứng cứ

[MedicalRecordControllerTest](../../backend/clinical-service/src/test/java/com/mediflow/clinical/web/MedicalRecordControllerTest.java)
có DOCTOR đọc danh sách bệnh nhân và NURSE đọc chi tiết, nhưng mock ManageRecordUseCase.
Các test này không chứng minh cách ly khoa hoặc quan hệ điều trị xuyên các lớp.

[FollowUpRegistrationAcceptanceTest](../../backend/clinical-service/src/test/java/com/mediflow/clinical/application/service/FollowUpRegistrationAcceptanceTest.java)
có trường hợp đổi bác sĩ khi tái khám, yêu cầu lý do/xác nhận và lưu cả hai. Đây là liên kết tiếp
nhận tái khám, không phải test chứng minh bác sĩ thay thế đọc được mọi tài liệu của lần trước.

## 3. Có bám sát thực tế không?

**Bác sĩ tiếp nhận đọc lịch sử do đồng nghiệp cùng khoa ghi là hướng phù hợp** để tiếp tục
điều trị và bàn giao. Hồ sơ gắn với người bệnh tại cơ sở; tác giả của từng lượt vẫn được giữ.
Nhưng "cùng khoa" là điều kiện phân vùng của phần mềm, không tự chứng minh mục đích truy cập
hợp lệ cho mọi nhân viên. Nên gắn việc xem với nhiệm vụ tiếp nhận/điều trị, rà soát chuyên môn
hoặc nhiệm vụ khác đã được cơ sở cho phép, và lưu vết truy cập.

**Admin đọc xuyên khoa có thể là lựa chọn MVP**, nếu admin đại diện người được cơ sở giao
nhiệm vụ quản lý/khai thác hồ sơ. Không nên giải thích "quản trị hệ thống đương nhiên được xem
mọi bệnh án". Admin kỹ thuật, lãnh đạo chuyên môn và nhân sự quản lý hồ sơ là các nhiệm vụ
khác nhau dù MVP hiện gom trong một role. Quyền xem không bao gồm sửa nội dung chuyên môn,
xuất/chia sẻ tùy ý hoặc tự bỏ qua thủ tục khai thác hồ sơ đã lưu trữ.

**Giới hạn bác sĩ chỉ đọc cùng khoa là đơn giản hóa MVP**, không bao phủ mọi phối hợp thực tế:
khám liên khoa, hội chẩn và kiểm tra thuốc/tiền sử cần quyền truy cập liên quan có kiểm soát.
Không bắt mọi khám phối hợp thông thường phải dùng truy cập khẩn, và không chuyển quyền đó
sang admin để xem hộ bác sĩ.

Căn cứ đối chiếu, không phải chứng nhận tuân thủ:

- [Luật 15/2023/QH15, Điều 64 và 69](https://xaydungchinhsach.chinhphu.vn/toan-van-luat-15-2023-qh15-kham-benh-chua-benh-119231127164453959.htm):
  có hội chẩn liên khoa; hồ sơ phải giữ bí mật, khai thác đúng mục đích; phân biệt hồ sơ đang
  điều trị với hồ sơ hoàn thành đã chuyển lưu trữ. Không có role phần mềm tên ADMIN tự cấp quyền.
- [Thông tư 13/2025/TT-BYT](https://thuvienphapluat.vn/van-ban/The-thao-Y-te/Thong-tu-13-2025-TT-BYT-huong-dan-trien-khai-ho-so-benh-an-dien-tu-660113.aspx),
  Điều 6 khoản 3 yêu cầu cơ sở xây dựng quy chế lập, cập nhật, quản lý, lưu trữ, sử dụng và an
  toàn thông tin hồ sơ điện tử. Điều 4 quy định TT 46/2018 hết hiệu lực.
- [Luật Bảo vệ dữ liệu cá nhân 91/2025/QH15](https://chinhphu.vn/?classid=1&docid=214590&pageid=27160&typegroupid=3)
  có hiệu lực từ 01/01/2026; cần đối chiếu khi chốt xử lý/cung cấp dữ liệu sức khỏe.

## 4. Hướng xử lý đề xuất — chưa triển khai

1. DOCTOR: cho đọc liên khoa theo nhiệm vụ được cấp, không lọc cứng theo khoa hoặc tác giả.
   GET danh sách và chi tiết áp cùng chính sách; không chỉ ẩn hàng ở frontend. Phân biệt
   khả năng đọc rộng hiện có với kiểm soát mục đích/quan hệ điều trị chưa có. Khi lookup
   authority không khả dụng, không fallback thành toàn bộ hồ sơ. Không cross-DB.
2. ADMIN: giữ khả năng đọc xuyên khoa theo yêu cầu; ghi rõ nhiệm vụ quản lý hồ sơ trong phạm
   vi MVP. Thiết kế quyền đọc chuyên môn riêng với quyền quản trị kỹ thuật khi mở rộng.
3. NURSE: chốt phạm vi nhiệm vụ/khoa riêng; không để role được đọc rộng ngoài ý muốn.
4. Chính sách đọc dùng chung cho record và tài liệu liên quan; cho bác sĩ tiếp nhận đủ quyền
   đọc giấy hẹn nguồn theo chính sách đã chốt. Giữ nguyên guard tác giả/phân công cho thao tác ghi.
5. Giao diện: bác sĩ thấy lịch sử liên khoa trong phạm vi được phép; admin thấy mọi khoa, có cột
   khoa và bộ lọc. Bộ lọc giao diện không thay thế kiểm soát dữ liệu trả từ backend.
6. Thiết kế nhật ký đọc/xuất hồ sơ theo actor, patientId/recordId, thời điểm, hành động, căn cứ
   hoặc mục đích và kết quả; không ghi toàn bộ nội dung lâm sàng/token vào log.
7. Không cần CareEpisode để sửa quyền đọc. Episode là thay đổi riêng phục vụ nhóm
   và vòng đời quá trình điều trị ở giới hạn 01; trước sửa contract phải khóa fixtures hiện hành.

## 5. Tiêu chí nghiệm thu cần bổ sung

Fixture bệnh nhân P có R1 do B và R2 do C ở khoa tim mạch, R3 do E ở tiêu hóa; R1/R2 có
liên kết tái khám hợp lệ, giấy hẹn nguồn do B lập. Có bác sĩ D khác cùng khoa và admin A.

| Kịch bản đích | Kết quả cần chứng minh |
|---|---|
| C hoặc D có nhiệm vụ phù hợp tra cứu P ở khoa tim mạch | Thấy R1 và R2 dù khác tác giả |
| C mở R1 trực tiếp | Đọc được; không được sửa nội dung của B |
| C mở giấy hẹn nguồn của R1 theo quyền tiếp nhận | Đọc được; không lẫn với tình huống chưa có giấy hẹn |
| C tra danh sách / gọi trực tiếp R3 khi không có quyền liên khoa | Danh sách không lộ R3; GET chi tiết bị từ chối theo quy ước bảo mật |
| C có nhiệm vụ tiếp nhận/hội chẩn và quyền đọc liên khoa | Đọc được R3 của E nhưng không được sửa nội dung của E |
| B sửa trực tiếp R1 đã COMPLETED, kể cả đúng tác giả | Bị chặn; đính chính chỉ qua quy trình riêng nếu được triển khai |
| A tra cứu P | Thấy R1, R2, R3; phân biệt được khoa và bác sĩ từng lượt |
| Đổi patientId/recordId trên URL | Backend vẫn áp chính sách, không chỉ RoleGate/frontend |
| Staff bị vô hiệu hóa hoặc chuyển khoa | Quyền phản ánh authority hiện hành, không dựa vào lựa chọn khoa của client |
| Đọc/xuất hợp lệ hoặc bị từ chối | Có evidence nhật ký phù hợp thiết kế đã duyệt |

Cần test use case, web/Gateway và kiểm tra persistence/UI với nhiều khoa, nhiều bác sĩ;
không dùng mock controller hoặc tài liệu này làm bằng chứng đã đạt các tiêu chí trên.

## 6. Các quyết định còn cần chốt khi triển khai

- ADMIN trong demo đại diện nhiệm vụ quản lý nào; ai rà soát nhật ký và cấp quyền khai thác?
- Căn cứ cấp quyền đọc liên khoa theo nhiệm vụ là gì; xử lý hồ sơ đã lưu trữ khác hồ sơ đang
  điều trị thế nào? Không coi khác khoa là lý do cấm đọc tuyệt đối hoặc role là quyền vô hạn.
- Cách cấp quyền khám phối hợp/hội chẩn, truy cập lịch sử sau hoàn tất và phạm vi NURSE?
- Phạm vi đọc đơn thuốc, Lab, tệp và giấy hẹn được thống nhất thế nào giữa các context?

## 7. Làm rõ hồ sơ đã đóng và nhiều ca trong ngày — 2026-11-09

### 7.1 Đóng lượt khám không đồng nghĩa đã ký và chuyển lưu trữ

[MedicalRecord](../../backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/MedicalRecord.java)
chỉ có vòng đời OPEN/COMPLETED; `requireOpen` chặn sửa COMPLETED cho mọi tác giả.
[MedicalRecordApplicationService](../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/MedicalRecordApplicationService.java)
còn kiểm tra bác sĩ được phân công và appointment IN_EXAM khi cập nhật. Source hiện tại
không hỗ trợ chính tác giả sửa hồ sơ đã hoàn tất, không có quy trình addendum/đính chính,
đa tác giả hoặc bàn giao bác sĩ của một record. Không diễn giải COMPLETED là đã có đủ
chữ ký điện tử, kiểm tra hoàn thiện và chuyển lưu trữ hợp pháp.

Hướng đề xuất: giữ bản gốc; ghi nội dung bổ sung, lý do, tác giả, thời điểm thực tế và dấu
vết phê duyệt theo quy chế cơ sở. Bác sĩ khác ghi nhận định của mình qua phiếu riêng, không
sửa đè ghi chép cũ. Khi tác giả không còn công tác, cơ sở cần cơ chế xử lý được ủy quyền,
không phụ thuộc vĩnh viễn một tài khoản. Đây là thiết kế đề xuất, chưa triển khai.

Ví dụ thực tế: [Quyết định 427 của Bệnh viện Lao và Bệnh phổi Quy Nhơn, Điều 12](https://benhvienlaophoiquynhon.com.vn/wp-content/uploads/2025/09/427_QD-BVLPQN_26092025-signed_01.pdf#page=9)
yêu cầu nhân viên đề nghị đính chính hồ sơ lưu trữ nêu sai sót/lý do/nội dung, trình phê duyệt
và lưu vết. Đây là quy chế một bệnh viện, không tự suy thành quy trình giống hệt cho mọi cơ sở.
Luật 15/2023 cấm sửa chữa nhằm làm sai lệch hồ sơ, không có nghĩa mọi đính chính hợp lệ bị cấm.

### 7.2 Một bệnh nhân, cùng khoa, nhiều bác sĩ/ca trong ngày

| Tình huống | Cách xử lý nghiệp vụ đề xuất |
|---|---|
| Sáng khám, chiều có kết quả; lượt vẫn OPEN | Tiếp tục lượt cũ; không tạo lượt/tính công khám mới chỉ vì đổi buổi |
| Bác sĩ hết ca, đồng nghiệp tiếp nhận lượt đang mở | Bàn giao rõ người chịu trách nhiệm; mỗi người chịu trách nhiệm ghi chép của mình; source chưa có cơ chế này |
| Mời bác sĩ khác cùng khoa/hội chẩn | Phiếu ý kiến riêng, giữ tác giả; không tự đổi thành lượt độc lập chỉ vì có thêm bác sĩ |
| Đã hoàn tất về nhà, quay lại do bất thường hoặc vấn đề mới | Tiếp nhận, đánh giá và ghi lần khám lại có liên kết; không mở sửa bản hoàn tất để ghi diễn biến mới; cấp cứu theo luồng cấp cứu |
| Đăng ký hai ca cho cùng vấn đề khi lượt cũ đang xử lý | Kiểm tra trùng, chọn tiếp tục/bàn giao hoặc xác nhận lý do lượt độc lập; không tự tính hai công khám |

[OutpatientRegistrationApplicationService](../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/OutpatientRegistrationApplicationService.java)
và [migration V9](../../backend/clinical-service/src/main/resources/db/migration/V9__outpatient_scheduling_v2.sql)
chỉ chặn đăng ký còn hoạt động trùng `(patient_id, session_id)`. Khác session vẫn có thể
đăng ký cùng ngày; record hoàn tất không thuộc điều kiện unique đăng ký đang hoạt động.
Đây chưa phải cơ chế phân loại khám tiếp, bàn giao, hội chẩn hoặc chống trùng liên ca.

Quy tắc canonical ở [business flows, mục 5](../plans/02-business-flows.md) đã phân biệt nhận
kết quả trong lượt mở với lượt mới sau hoàn tất; không suy phí từ việc đổi ngày/bác sĩ.
[Hướng dẫn thanh toán được Chính phủ công bố theo TT39/2024](https://baochinhphu.vn/bo-y-te-huong-dan-thanh-toan-tien-kham-benh-trong-mot-so-truong-hop-102241122160114514.htm)
cũng có tình huống quay lại ngay trong ngày và khám nhiều chuyên khoa. Số lượt ghi nhận lâm
sàng không đồng nhất số đơn vị phí; không dùng giới hạn thanh toán làm giới hạn được khám.
Chưa đối chiếu đầy đủ mọi thay đổi giá/BHYT đến thời điểm triển khai thực tế.

Task này chỉ cập nhật known-limitations; không thay canonical contracts, schema, API,
phân quyền production hoặc thiết lập một chính sách mới đã nghiệm thu.
