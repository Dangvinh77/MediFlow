# Backend closeout — 27/10/2026

Source base `38f226e1`; thay đổi trong branch `codex/backend-acceptance-closeout`.
[Plan và các điều kiện đóng](../../plans/18-backend-closeout-2026-10-27.md).
Đây là hồ sơ kiểm thử backend tự động, không là chữ ký UX hoặc approval dữ liệu Report demo.
Tiếp tục ngày 28/10: cập nhật master `c9954c7c` (PR #549 chỉ thay mobile); backend base
và các JAR đã kiểm không đổi. Mỗi phase giữ thời điểm/source base riêng trong manifest.

## Lỗi và phạm vi sửa

Clinical tạo timestamp nanosecond nhưng PostgreSQL trả lại microsecond. Test PG với
clock `.123456789Z` quan sát được hai failure riêng: acquire replay khác lease và
close replay khác `updatedAt`. Chuẩn hóa clock ở acquire/resolve về microsecond;
ID/fence/status/wire/migration và TTL không thay đổi. Hai ca đều phải chạy lại qua.

CI baseline [40502812871](https://github.com/Dangvinh77/MediFlow/actions/runs/40502812871)
ban đầu lỗi Maven HTTP 502; rerun tới được six-service IT và fail `AUTH_SESSION_REVOKED`.
Fixture ký ADMIN ngẫu nhiên đã lỗi thời sau kiểm tra phiên của Gateway. Ba packaged IT
Clinical/Lab/Support dùng bootstrap local và login thật trên DB disposable, tắt demo seed
để tránh tài khoản mẫu làm bootstrap bỏ qua. Không tắt auth/session filter.

PharmacyAuthorityRuntimeAcceptanceIT còn thiếu `identityNumber` khi tạo staff, bị Core
trả 400. Fixture bổ sung field theo request hiện hành, giữ nguyên validation production.
Lượt distributed đầu có 12/12 distributed cases qua, một authority failure này và một
outpatient case bỏ qua vì thiếu opt-in. Hai suite authority/outpatient được chạy lại
với `mediflow.acceptance.billing-v1-published=true` trên container mới; giữ nguyên kết
quả lỗi ban đầu trong manifest và đối chiếu suite cuối, không ghi exit 1 thành exit 0.
Flag chỉ cho bài producer acceptance cách ly, không release hàng HELD hay bật demo gate.

Report source acceptance có cùng staff fixture thiếu identity và đã cập nhật theo API.
Packaging acceptance còn hard-code 25 migrations trước V29; thay count bằng exact inventory
cho cả hai migration namespaces trong library JAR so với source, giữ kiểm byte-for-byte
và yêu cầu V29. Không sửa migration đã phát hành hoặc artifact production để chiều test.

Clinical packaged fixture cũng cần staff identity, cổng Billing clearance lookup và
registry refresh 1 giây cho các JVM trong bài. Log đỏ ghi rõ Clinical chưa thấy
`billing-service` trước lượt fetch mặc định; bổ sung cấu hình test theo six-service
harness, giữ auth và Billing execution authority thật. Không đổi discovery production.

## Kiểm thử bổ sung có assertion nghiệp vụ

| Matrix | Kiểm thử / bằng chứng | Điều đã chứng minh |
|---|---|---|
| SC-04 | ClinicalIntegrationTransactionTest: overlappingSessionRace, stoppedSession | Hai transaction tranh doctor/room chỉ một session + audit; STOPPED chặn overlap; liền nhau được tạo |
| SC-06/07 | ClinicalIntegrationTransactionTest: last seat và concurrentSameRegistrationIntent | Suất cuối không overbook; cùng intent chỉ một registration/outbox và exact replay; changed input conflict |
| SC-10/15 | concurrentAcquireAndLateEligibility | Cùng operation trả lease giống nhau; resolve + replay + eligibility trễ chỉ một queue number/counter/effect |
| SC-11 | LedgerPaymentIntegrationTest: killedPaymentProcess (3 cases) | JVM bị kill trước acquire hoặc sau cash commit; JVM mới đọc operation/store thật; đúng một cash/clearance/outbox hoặc CLOSED không thu |
| SC-12 | LedgerPaymentIntegrationTest: closeRacingCashCommit | Close và commit serialize trên cùng operation; không CLOSED rồi tạo cash |
| SC-13 | missingBillingAcrossWorkerProcesses | Hai Clinical JVM liên tiếp dùng cursor PG đã commit; missing giữ PAYMENT_LOCKED và retry; kết quả đóng xác định mới giải phóng |
| SC-14 | closedPaymentOperationRejectsLateAcquireAndOldFenceAfterNewLease; closeAfterLostAcquireReceiptCanFenceLateBillingRetry | Tombstone chặn acquire muộn; operation mới có fence tăng; outcome cũ không chiếm/cấp thêm suất |
| SC-16 | completedLeaseAfterHoldExpiry (2 cases); persistedLeaseAllowsOneCashCommitAfterOriginalRequestExpiry | Lease đã cấp cho phép ghi outcome sau TTL; hết buổi vào đối soát, không cấp queue sai |

Các PG tests dùng store/transaction/outbox thật. Clinical fixture thay Core/Billing ở
biên authority; Billing crash fixture thay Clinical ở biên HTTP port. Không gọi những
fixture đó là bằng chứng hai service giao tiếp mạng thật. Process-recovery profiles
kiểm broker/outbox riêng; packaged/Gateway acceptance có phạm vi riêng bên dưới.

## Đối chiếu phần matrix đã có source

Không tạo lại test chỉ để đánh dấu checklist. Những nhóm sau có assertion đã chạy
trong reactor; phạm vi unit, HTTP slice và PG được phân biệt với network acceptance:

| Matrix / nghiệp vụ | Suite đã chạy | Phạm vi |
|---|---|---|
| SC-01/02 | ScheduleManagementScopeServiceTest, ScheduleManagementScopeIntegrationTest | Grant/revoke, current staff/department authority, receipt/audit/outbox rollback; Core PG thật |
| SC-03/05/17 | ConsultationSessionTest và Clinical scheduling tests trong manifest | Eligibility, time/break/revision, occupied-session guards; không thay bằng chứng Core timeout qua mạng |
| SC-08/09/20 | ClinicalCareFinanceApplicationServiceTest, OutpatientRegistrationTest, ScheduledPaymentCoordinatorTest, LedgerPaymentIntegrationTest | Arrival/paid/start-exam, request binding/amount, expired hold, V1/V2 guards; Billing PG thật ở integration suite |
| SC-18/19 | ConsultationSessionCalendarTest, PatientResultsServiceTest, PatientResultsControllerTest | Verified own identity, Việt Nam Monday–Sunday, date selector, scope injection; unit/HTTP slice |
| WF tái khám | FollowUpRegistrationAcceptanceTest (14), FollowUpRecommendationApplicationServiceTest (5) | Completed source cùng patient/khoa, recommendation binding, tự quay lại có reason, đổi doctor có consent; không ký thay UX quay lại ngày khác |
| WF quote/zero/Lab | OutpatientQuoteIntegrationTest, LabQuotedPaymentIntegrationTest, LabQuoteZeroLiabilityIntegrationTest, ClinicalLabCompletionGuardTest | Quote/support/zero authority, đúng order/episode, unfinished Lab chặn complete; không suy completeness Report |
| Routes/session | Gateway route/security suites, Core JWT/account suites, packaged login | Default deny, human/service separation và current session; token tự ký chỉ giữ cho negative credential cases |

Các tên method đầy đủ và SHA-256 XML được giữ trong snapshot local; manifest công bố
suite/count/hash để đối chiếu. Đây là mapping assertion đã thực thi, không tuyên bố
toàn SC-01–20 đã được chạy lại qua trình duyệt hoặc cùng một distributed fixture.

## Kết quả

### H-02 — đã đóng

Core regression **395/395** gồm PatientServiceIntegrationTest **10/10** và Organization
integration **27/27** trên PostgreSQL/Rabbit. Đã kiểm create/update khi broker outage,
rollback khi outbox lỗi, worker reconstruction, lỗi sau publish confirm trước mark,
giữ nguyên event ID/body và bounded retry/returns/quarantine. Head/title và hai thứ tự
deactivate/create, deactivate/transfer giữ bất biến khoa/nhân viên.

Ngày 28/10, lượt `six-service-welcome` bắt nguyên `patient.created` do public Core
command phát qua Rabbit, xác nhận welcome và processed marker rồi restart Support
JVM trên store hiện có. Replay nguyên body/properties đã được ACK: vẫn một welcome,
marker không đổi, không thêm `notification.sent` và không thêm dead letter. Toàn workflow
paid EXAM/Rx/refund/publication/calendar trong cùng lượt đạt exit 0, không skip.
[patient-welcome-replay.json](patient-welcome-replay.json) giữ event ID/hash và counts;
không công bố payload có PII. Handoff/entry được xóa sau khi đủ hai phía.

Producer recovery ở đây dùng lỗi tại DB mark và khởi tạo lại worker; không tuyên bố
kill Core JVM. Consumer restart là Support JVM thật. Legacy welcome dùng MockEmailSender
trong môi trường test; không ký gửi SMTP thật hoặc chuyển nó thành outbox Notification.

### H-03 — đã đóng

Gateway/Core không thay source kể từ base đã merge. Full reactor có Core **395** và
Gateway **1.649** tests qua với PG/Rabbit thật. CoreCompositionSmokeTest **6/6** kiểm
expiration ở cả Organization/Patient, đúng service scope, Gateway đúng caller và
Pharmacy sai caller dù giả header; Organization JwtAuthFilterTest **17/17** và
GatewayWebTest **27/27** bổ sung negative/positive token cases. Actual packaged
bootstrap/login qua Gateway/Core và refund acceptance trên JAR mới đã qua; explicit
refund suite **22/22**. Đủ HA-AUDIT-01/02, chuyển lasting rules vào 03 và xóa handoff.

HA-AUDIT-03 là cùng race ở H-02, không mở lại task JWT: Organization PG suite **27/27**
đã chạy hai thứ tự deactivate/create và deactivate/transfer, cùng head/title race.
Patient consumer/restart được kiểm riêng và đóng H-02 ngày 28/10 ở mục trên;
bằng chứng JWT không thay bằng chứng replay và không mở quyền sửa frontend Hoàng Anh.

### H-04 — đã đóng

Hai exact routes prescription refunds-due và transaction refunds có ADMIN/CASHIER
qua Gateway tới Billing thật. Six-service acceptance kiểm wrong role/type/token,
missing token/ID, wrong method/nested path, signed actor/correlation, concurrent refunds
chỉ một 201/một 422, exact replay, changed input/actor conflict và over-refund. Tổng hoàn
30.000 VND chỉ hai cash refund records, obligation hết và stock không bị cấp thêm.
Publication opt-in kiểm hai refund facts đến Report/Notification. Bộ explicit
GatewayRefundRoutesAcceptanceIT chạy thêm **22/22**, 0 failures/errors/skipped; toàn
Gateway và Billing đã có regression trong reactor/lượt Billing cuối. Đủ producer và
verification acceptance của H-04; xóa handoff/entry, giữ quy tắc lâu dài trong 03.
Phần UX hoàn tiền vẫn thuộc runbook ba developer, không là task route backend còn mở.

Clean reactor trước production fix: **7.721 tests**, 0 failures/errors/skipped, gồm
Support packaged IT. Lượt này có ba Billing crash cases đã compile trong reactor;
không phải checkout sạch chỉ chứa source của base commit. Sau fix, toàn Clinical
**447**, toàn Billing **1.182** tests đều qua; lớp Clinical PG có thêm assertion
terminal replay được chạy lại riêng **11** tests. Không cộng các lượt lặp thành test duy nhất.

V2 smoke qua Gateway trên snapshot cách ly đã đạt đăng ký PREBOOKED, exact replay,
own/other-patient scope, quote gross 100.000/support 50.000/payable 50.000, thu tiền
và trạng thái CONFIRMED chưa đủ điều kiện khám khi chưa arrival. Xem
[booking-payment.json](booking-payment.json): chạy `accept_scheduling.py --booking-only`,
không chạy phần khám/Lab/tái khám của runner sau 17:00. Stack snapshot riêng đã được dừng.
Schema checker đạt **87 cases / 50 definitions**; ClinicalLabWireCheck đạt **5 JSON
contract checks** trên classpath các module đã build. Hai kiểm tra này không thay runtime.

[report-calendar-summary.json](report-calendar-summary.json) giữ scope, source witnesses
và hashes của journal nguồn cách ly. CLINICAL/LAB rỗng đã đối chiếu cho scope fixture,
không suy nguồn thật rỗng; cash 160.000/refund 30.000/net 130.000 VND. Clock calendar
chỉ thay trong Report test, không thay system/JWT và không ký dataset demo đang vận hành.

Manifest kết quả cuối ghi từng command, exit code, suite counts và XML/JAR/source SHA-256.
Raw XML/logs giữ local dưới `scripts/demo_25_10/evidence/backend-closeout.local/`;
không commit token/password/logs hoặc toàn bộ disposable database.

Kết quả profile cuối, không cộng trùng với full reactor:

| Profile | Cases | Failures / errors / skipped |
|---|---:|---|
| Six packaged deployments + welcome replay/restart + refund/publication/calendar | 1 workflow | 0 / 0 / 0 |
| Clinical/Lab process recovery, Billing publication, Notification identity CLI | 11 | 0 / 0 / 0 |
| Pharmacy runtime, lấy kết quả cuối từng suite | 17 | 0 / 0 / 0 |
| Report actual Clinical source | 1 workflow | 0 / 0 / 0 |
| Report packaging/migrations | 5 | 0 / 0 / 0 |
| Clinical packaged EXAM | 1 workflow | 0 / 0 / 0 |
| Lab packaged payment/result + consumer recovery | 1 workflow | 0 / 0 / 0 |

Clinical/Lab cuối chạy ngày 28/10; Lab invocation truyền rõ absolute Billing JAR theo
prerequisite của profile. [verification.json](verification.json) giữ thời điểm, source
base từng lượt và hash các backend files thay đổi. Tất cả packaged JAR vẫn khớp hashes
của lượt `package-final`; cập nhật master sau đó chỉ thay mobile.

Self-review theo `.agents/skills/review-pr/SKILL.md`: không tìm thấy blocker trong diff.
Production chỉ đổi độ chính xác clock của hai receipt; không thêm framework/cross-store
coupling, không đổi wire/roles/events/migrations. Regression mới có case fail trước fix,
PG/Rabbit/process thật và assertions receipt/counts/outbox sau replay. Đủ điều kiện mở
PR; kết quả CI của PR và chữ ký demo là gate riêng, chưa được suy từ kiểm tra local.

## Giới hạn phải giữ khi bàn giao

- Runbook [16](../../plans/16-three-developer-demo-acceptance-2026-10-27.md) vẫn cần Vinh,
  Huy, Hoàng Anh thử UX chung, quay lại ngày khác và quy trình xác minh danh tính thật.
- Report calendar acceptance chỉ dùng clock test của Report trong container/JVM riêng;
  không thay giờ máy/JWT. Năm nguồn là journal public commands hữu hạn của môi trường mới,
  không là export production hay bằng chứng completeness của dataset demo khác.
- Duyệt Report demo thật phải có ngày kết thúc và năm nguồn đã đối soát theo
  [operator workflow](../../../backend/report-service/operations/outpatient-coverage-acceptance.md).
  Không zero-fill dữ liệu thiếu hoặc ký coverage bằng preview.
- Không đổi feature frontend, scope mobile hoặc bật Inpatient/Surgery. Handoff chỉ xóa khi đủ
  tiêu chí cả hai phía; backend tests không ký thay consumer frontend hoặc quy trình con người.
