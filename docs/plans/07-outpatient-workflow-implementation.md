# Triển khai điều chỉnh nghiệp vụ ngoại trú — 25/10/2026

Đây là kế hoạch phái sinh từ [luồng nghiệp vụ](02-business-flows.md), không là nguồn
contract thứ hai. Ngày 26/10 người dùng giao triển khai source, migration, cấu hình và UI trên toàn bộ phạm vi
liên quan của Hoàng Anh/Vinh/Lộc. Source đang triển khai trên nhánh PR; không coi có code là đã
nghiệm thu hết các phần việc bên dưới.
Không cộng các task này vào tỷ lệ 18 task/72 subtask của baseline triển khai 14/10.

## Các quyết định đã chốt

- Tiếp nhận tại quầy gộp đăng ký và xác nhận có mặt; lịch đặt trước vẫn cần check-in khi đến.
- Chọn lịch khám còn suất trước thu tiền; chỉ xác nhận phiếu và vào hàng chờ sau đủ nghĩa vụ tài chính.
- Tái khám là lượt mới liên kết lần trước; đọc kết quả trong lượt đang mở là tiếp tục khám.
- BHYT là hỗ trợ mô phỏng theo danh mục, mặc định mọi người đủ điều kiện/thẻ còn hạn;
  tỷ lệ do dự án tự quy định, không tra cứu hoặc quyết toán với cơ quan BHXH.
- Clinical quản lý lịch khám ngoại trú và giữ suất. Organization cung cấp staff/khoa/đủ điều kiện.
- Quản lý lịch theo buổi/sức chứa; DOCTOR xem lịch cá nhân ngày/tuần, không tự sửa/xóa ca.
  TTL giữ chỗ 15 phút không là thời lượng khám; không mở chấm công hoặc tự động phân ca.
- Không tự sửa ý nghĩa wire V1 hoặc ghi đè snapshot tiền cũ để áp dụng chính sách mới.
- Chốt 27/10: NURSE tiếp nhận phiếu giấy, cấp tài khoản PATIENT cho người bệnh mới lần đầu;
  dùng web là tùy chọn. Đổi mật khẩu ban đầu khi lần đầu dùng tài khoản, không chặn đăng ký/
  khám hoặc yêu cầu đổi lại theo mỗi lượt. Chi tiết ở [02 mục 4.1](02-business-flows.md#41-tiếp-nhận-lần-đầu-và-tài-khoản-bệnh-nhân--chốt-27102026).

## Work packages theo thứ tự phụ thuộc

| ID | Phần việc, owner backend | Tiêu chí đóng |
|---|---|---|
| WF-01 | Khóa baseline và fixtures, Vinh điều phối + các owner | Manifest route/DTO/event/flags hiện tại; từng gap có fixture dự kiến, owner và test |
| WF-02 | Lịch khám theo buổi và giữ suất, Vinh/Clinical; phối hợp Hoàng Anh/Core | Nhân sự/khoa/phòng hợp lệ, phạm vi MANAGER rõ; lịch không chồng; tranh suất cuối chỉ một người giữ; hủy/đổi có audit |
| WF-03 | Tiếp nhận và xác nhận phiếu, Vinh/Clinical + Billing | Một màn hình tại quầy; đăng ký trước thanh toán; timeout/late payment không mất tiền hay vượt sức chứa |
| WF-04 | Khám mới/tái khám/hẹn tái khám, Vinh/Clinical | Liên kết lượt trước đúng bệnh nhân; bác sĩ cũ có thể thay hợp lệ; không copy đơn/kết quả/diagnosis thành chỉ định mới |
| WF-05 | Chính sách hỗ trợ và quote bất biến, Vinh/Billing | Tính theo từng dòng, version/rounding rõ; không tự tính BHYT từ frontend hoặc role PATIENT |
| WF-06 | Thu phí/miễn phần người bệnh/hoàn, Vinh/Billing | Zero payable có chứng từ eligibility riêng, không fake cash; refund không quá patient paid và không double refund |
| WF-07 | Lab và vòng quay lại đọc kết quả, Vinh/Lab + Clinical | Không hoàn tất lượt khi chỉ định bắt buộc còn pending; hủy có lý do; không sinh EXAM charge lần hai |
| WF-08 | Giá thuốc/điều kiện cấp phát, Huy/Pharmacy + Vinh/Billing | Giữ snapshot catalogue và stock guards; hỗ trợ thuốc chỉ khi có item policy, không client price |
| WF-09 | Report và Notification, Huy/Report + Vinh/Notification | Không tính hỗ trợ thành cash; đăng ký chưa thu không thông báo xác nhận; replay không double count/send |
| WF-10 | Tích hợp frontend theo owner, Gateway Hoàng Anh | Quản lý buổi khám và DOCTOR xem lịch ngày/tuần theo SCH-01–06; màn hình cùng lượt/đúng role; lỗi dependency không dùng danh sách cũ để tạo mới; human/internal routes đúng quyền |
| WF-11 | Nghiệm thu và chuyển hướng dẫn demo, toàn nhóm | Ma trận WF trong 06 đạt qua API/UI, real PG/Rabbit, source commit mới và evidence; chỉ sau đó thay nhãn baseline |

Frontend giữ ownership ở [15-frontend-ownership](../ai/15-frontend-ownership.md), không suy
quyền sửa Billing/Notification frontend từ ownership backend. Tài liệu này không giao quyền
production cho owner khác; gap producer được bàn giao theo root AGENTS.

## Slice ONB — tiếp nhận và cổng bệnh nhân, kế hoạch 27/10/2026

**Goal:** NURSE tiếp nhận người mới từ phiếu giấy và cấp tài khoản PATIENT; bệnh nhân dùng
web theo nhu cầu mà không làm chậm luồng khám. Nghiệp vụ authoritative ở 02 mục 4.1.
**Architecture:** Patient giữ hồ sơ; Organization giữ tài khoản/credential; Clinical giữ lượt
khám; Lab giữ kết quả; Notification giữ in-app history. Gateway xác minh quyền và phiên.
**Tech Stack:** Spring Boot/Maven, PostgreSQL/RabbitMQ, Next.js/TypeScript/pnpm hiện có.
Cập nhật 27/10: ONB-01–05 đã được triển khai và merge qua PR #544; cleanup source
qua PR #545. Các bước triển khai đã hoàn thành được rút thành bảng dưới; không giữ
checklist viết code như backlog mới. Chi tiết source, fixtures và module/frontend tests tại
[biên bản source ONB](11-patient-onboarding-source-acceptance-2026-10-27.md).
User đã giao triển khai các module/path nêu trong slice này và tạo PR; ownership dài hạn không đổi.

| Slice | Trạng thái source | Phần cần nghiệm thu tiếp |
|---|---|---|
| ONB-01 | Đã khóa command/DTO/fixtures cấp PATIENT riêng và patient-existence port | Đối chiếu runtime candidate cuối với 03; không mở endpoint ADMIN tổng quát cho NURSE |
| ONB-02 | Command idempotent, một account/patient, credential version và Gateway password gate; replay/quyền/phiên cũ, hai NURSE concurrent và mất phản hồi sau commit/retry qua Gateway đã kiểm | Phục hồi gián đoạn trên UI; không suy mất kết nối là đã kiểm mọi timeout nội bộ |
| ONB-03 | Tìm/tạo patient → cấp account → tiếp nhận; giữ patient/key khi phục hồi; login không chặn khám | Tổng duyệt trình duyệt đa vai trò và phục hồi gián đoạn |
| ONB-04 | Clinical/Lab own-result APIs và UI; own-resource, API ẩn kết quả chưa hoàn tất và đọc Lab khi lượt còn mở đã kiểm | Draft/empty/unavailable UI; vòng đọc kết quả qua ngày khác |
| ONB-05 | Ba fact kết quả khám/Lab/lời hẹn → in-app history; trước login và đọc lại sau login muộn đã kiểm | Broker replay effect counts trên packaged candidate; không suy đã đọc hoặc có SMS/email/push |

Bàn giao liên owner vẫn theo [H-11](../handoffs/HANDOFF-NURSE-PATIENT-ONBOARDING-2026-10-27.md).
Chỉ đóng sau nghiệm thu producer/consumer trong 06; có source và test không tự đóng handoff.

### ONB-06 — nghiệm thu trên candidate cuối, Vinh verification + các owner

- [ ] Chạy ONB-A01–12 trong 06 qua Gateway và UI, PostgreSQL/RabbitMQ thật cho race/replay.
- [x] Từ `frontend/`, chạy `pnpm exec vitest run src/features/patient/components/PatientForm.test.tsx src/features/patient/usePatientMutation.test.ts src/features/appointment/components/ReceptionWorkspace.test.tsx src/features/organization/components/PasswordForm.test.tsx src/app/login/page.test.tsx`;
  tiếp theo `pnpm typecheck`, `pnpm lint`, `pnpm build`.
  Chạy toàn bộ `pnpm test` khi tích hợp candidate cuối; ghi số run/fail/skipped thực tế.
- [ ] Hồi quy WF-T01/04/12/16: khám mới người cũ, đăng ký có mặt, tái khám linked source,
  đọc kết quả cùng lượt; không tạo lại hồ sơ/account/EXAM charge vì chưa dùng web.
- [ ] Lưu commit/flags, request/response đã che dữ liệu nhạy cảm, ảnh UI và effect counts;
  phân biệt đã có code, tests đạt và packaged/API/UI đạt. Chỉ đóng H-11 khi cả producer và
  consumer đạt ONB-A01–12; kết quả slice này không tự đóng toàn bộ WF-11.

Checkpoint [Gateway/UI 27/10](../acceptance/onboarding-2026-10-27/README.md): full frontend
119 files/1.097 tests, typecheck/lint/build đạt; 87 HTTP checks mới → khám → Lab → login muộn →
tái khám/recovery và 20 checks khám mới người cũ đạt. Đã sửa Billing source/settled TTL và
Clinical V2 diagnosis/Lab-order guards bằng regression. Ảnh UI và giới hạn từng ca ở biên bản;
Phạm vi trên là checkpoint lịch sử. [Checkpoint tiếp theo](../acceptance/onboarding-recovery-2026-10-27/README.md)
đạt 107 observations cho race/mất phản hồi/tiếp nhận lần hai trước login và 99 checks cho Lab
trong lượt mở. Vẫn chưa tick toàn ma trận: UI gián đoạn/lỗi nguồn, broker replay và xác minh
danh tính recovery còn mở; ca hai lần tiếp nhận đã chạy cùng ngày trên dữ liệu giả lập.

## Source lock trước triển khai

Đã bổ sung [source audit 26/10](09-outpatient-source-lock-2026-10-26.md), manifest fingerprints và
[handoff liên owner](../handoffs/HANDOFF-WF01-OUTPATIENT-CONTRACT-GAPS-2026-10-26.md).
Baseline đã được ghi; WF-01 vẫn mở vì wire mới và golden fixture acceptance chưa hoàn tất.

Cập nhật thiết kế tiếp ngày 26/10: [03 phụ lục A](03-contracts.md#opd-scheduling-v2)
đã đặc tả TARGET `OPD-SCHED-2026-10-26` revision 2 cho schedule, Core department scope,
NEW_VISIT registration và payment lease/recovery. [Ma trận SC-01–20](10-scheduling-contract-acceptance.md)
ghi thứ tự nghiệm thu và giới hạn của schema examples. Đây là phần thiết kế của G-01–04 và
một phần G-09/G-11. Đã có producer/consumer, migration, Gateway và UI có gate;
ma trận nghiệm thu runtime/UI vẫn là điều kiện đóng WF-11.

| Nguồn đã đối chiếu trong lượt tài liệu | Hiện trạng quan sát | Gap với nghiệp vụ mới |
|---|---|---|
| `AppointmentApplicationService.create()` | Kiểm patient, bác sĩ/khoa và pending cùng ngày | Chưa thấy kiểm lịch khám/capacity trong đường tạo này |
| `Appointment.validateSchedule()` | Kiểm ngày không quá khứ và khung 07:00–17:00 | Không thay kiểm ca, khoảng nghỉ hoặc suất cuối |
| `Appointment.checkIn()` | Chuyển pending sang awaiting payment và mở nguồn EXAM | UI tại quầy cần orchestration, không đổi nghĩa live command |
| `Appointment.recordExamClearance()` | Cho phép ready theo exact grant | Chưa chứng minh hold/confirmation và zero-pay policy mới |
| Baseline contract manifest 03 | Có exact EXAM/LAB_TEST/PRESCRIPTION và event/idempotency | Chưa khóa wire cho schedule/revisit/support breakdown; phải audit cả producer/consumer |

Đây là nhận xét source giới hạn, không là kết quả chạy test hoặc kết luận mọi đường đi trong
service đều thiếu tính năng. WF-01 phải mở rộng source lock trước khi sửa production.

Tối thiểu đọc/khóa `CreateAppointmentRequest`, `Appointment`, `AppointmentApplicationService`,
`ClinicalCareFinanceController`, Clinical order/context DTO, Lab request/clearance/receipt DTO,
Billing quote/payment/allocation/clearance DTO và fixtures `lab.request.created`,
`financial.clearance.granted`, `prescription.created`, `payment.completed`.
Các tên khái niệm trong 02/03 chưa là field/route Java thực tế.

Không dùng kernel bảo hiểm admission/deferred làm bằng chứng đã có BHYT ngoại trú.
Không bật ADMISSION/SURGERY, sửa migration đã phát hành hoặc sửa snapshot SQL để demo đạt.

## Cập nhật tài liệu và xử lý tài liệu cũ

| Nhóm | Xử lý ngày 25/10 | Lý do |
|---|---|---|
| 01–06 và chỉ mục docs | Cập nhật phạm vi, luồng, contract gap, reliability, acceptance | Một nguồn nghiệp vụ hiện hành |
| Context guides backend và frontend Clinical/Lab/Billing/Core/Report/Notification/Pharmacy | Cập nhật trách nhiệm và chỉ dẫn tới 02/03/06 | Không tạo một đặc tả song song |
| Ba hướng dẫn demo chi tiết + README demo | Gắn rõ baseline mã nguồn, giới hạn với thiết kế mới; bỏ hướng dẫn checkout branch cũ trong README | Tránh mô tả tính năng mới là đã chạy |
| `2026-10-14-ten-to-six-service-consolidation-plan.md` | Xóa pointer dư thừa không có inbound Markdown link trong repo | Nội dung đã thay bằng 05; không còn giá trị riêng |
| Evidence, review theo ngày, plan có kết quả lịch sử | Giữ, đánh dấu vai trò lịch sử tại chỉ mục tương ứng | Không xóa bằng chứng đã kiểm thử hoặc ghi lại lịch sử thành thiết kế mới |
| Compatibility pointers được AGENTS/skills tham chiếu | Giữ đường dẫn; trỏ về nguồn canonical | Tránh làm hỏng entry point của công cụ/teammate |

Không xóa source, SQL snapshot, dữ liệu, migration, handoff chưa đạt acceptance hoặc evidence
chỉ vì có ngày cũ. Tài liệu từng đúng với binary cũ là baseline lịch sử, không phải đặc tả mới.

Đợt chuẩn hóa tiếp theo được ghi tại [biên bản 26/10/2026](08-documentation-cleanup-2026-10-26.md);
trạng thái bàn giao được quản lý tại [registry hiện hành](../handoffs/README.md).


## Trạng thái source ngày 26/10 trong PR triển khai

| Phạm vi | Đã triển khai | Điều kiện còn phải xác nhận |
|---|---|---|
| WF-02/03 | Core authority; buổi/sức chứa; giữ suất; PREPARING/lease/fence; recovery | Tổng duyệt API/UI trên packaged JAR với dữ liệu hôm demo |
| WF-04 | Liên kết lượt completed; lời hẹn; đổi bác sĩ có lý do/đồng ý; PATIENT own lời hẹn | Tổng duyệt vòng hai và các quyền sai vai trò |
| WF-05/06 | Quote bất biến, policy version, hỗ trợ theo dòng; EXAM/LAB zero; LAB positive quote-bound | Dataset 100% riêng và kiểm Report cash không tăng do zero |
| WF-07 | Lab lifecycle lookup; chặn complete khi chỉ định chưa xong; giữ cùng lượt đọc kết quả | Tổng duyệt vòng Lab qua broker/clearance |
| WF-08 | Giữ catalogue/stock guards outpatient hiện hành | Không có policy hỗ trợ thuốc mới; hồi quy luồng cấp phát |
| WF-09/10 | Notification theo revision, Gateway quyền, các workspace UI và command recovery | Report coverage và UI đa vai trò trên stack mới |
| WF-01/11 | Manifest lịch sử giữ nguyên; wire revision 2 + schema checker + runbook V2 | Chỉ đóng sau khi ma trận và evidence khớp commit cuối |

[Hướng dẫn chạy V2](../../scripts/demo_25_10/MediFlow_Ngoai_Tru_V2_Test_2026-10-26.md)
dùng overlay opt-in; không đổi nhãn snapshot/baseline 25/10 thành kết quả mới.
Chưa có command renew quote; GET không gia hạn. Giới hạn này phải được giải thích khi demo.

[Bằng chứng 26/10](../acceptance/outpatient-2026-10-26/README.md): module tests,
frontend sau merge master, JAR topology và API booking/payment đã đạt theo phạm vi ghi trong biên bản.
UI PATIENT/DOCTOR đã xem trực tiếp; full exam–Lab–follow-up và toàn ma trận WF-11 còn mở.

## Bàn giao vòng thử trực tiếp 27/10

[Plan 15](15-demo-readiness-closeout-2026-10-27.md) đã chốt diff UX và phần kỹ thuật bổ sung:
frontend 1.108 tests/build/typecheck/lint; UI phục hồi account cùng key; ba fact replay
trên broker riêng không nhân thông báo. [Bằng chứng có phạm vi và fingerprints](../acceptance/demo-readiness-2026-10-27/README.md).
[Runbook ba developer](16-three-developer-demo-acceptance-2026-10-27.md) là lượt thử
thực tế tiếp theo. Quay lại ngày khác, xác minh danh tính recovery và tổng duyệt toàn
ma trận vẫn cần evidence; chưa tự đóng WF-11 hoặc H-11.
