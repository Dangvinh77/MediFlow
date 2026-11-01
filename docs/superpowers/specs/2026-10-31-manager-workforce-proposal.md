# MediFlow: trưởng khoa lâm sàng và hai khu vực làm việc — thiết kế MVP

Ngày cập nhật: 31/10/2026. Thiết kế đã được người dùng duyệt triển khai; checklist dưới đây đang được thực hiện, chưa phải bằng chứng nghiệm thu. Bản này thay thế đề xuất rộng về workforce/roster trước đó.

## 1. Phạm vi đã chốt

- Một tài khoản DOCTOR và một hồ sơ nhân sự hiện có. Admin bổ nhiệm DEPARTMENT_HEAD cho khoa lâm sàng; người được bổ nhiệm giữ nguyên role DOCTOR, định danh, nghiệp vụ khám và lịch sử.
- Phương án B: dashboard Khám bệnh và dashboard Quản lý khoa tách biệt, chuyển qua lại ở header. Chuyển giao diện không đổi JWT role hoặc tạo một phiên quản lý giả.
- Một khoa có tối đa một trưởng khoa đương nhiệm; một bác sĩ có tối đa một bổ nhiệm trưởng khoa đương nhiệm trong MVP. Khoa có thể chưa có trưởng khoa; không buộc mọi khoa phải được bổ nhiệm để vận hành lịch hiện có.
- Quản lý khoa được bổ nhiệm gồm lịch buổi khám, phân công bác sĩ/phòng, theo dõi số suất và thống kê hoạt động khoa. Có lịch ca làm việc độc lập cho bác sĩ trong khoa, tách khỏi buổi nhận bệnh nhân; chưa mở lịch ca điều dưỡng/kỹ thuật viên.
- ADMIN quản trị tài khoản, cơ cấu, hồ sơ nhân sự và bổ nhiệm; trưởng khoa không được quản trị tài khoản hay tự mở rộng phạm vi.
- Khoa Nội/Ngoại/Nhi và nhân sự trong ví dụ là mô hình minh họa, chưa phải kiểm kê database. Khoa Xét nghiệm giữ luồng Lab; không cần dashboard nhân lực Lab trong MVP.
- HEAD_NURSE, LAB_LEAD, trưởng phòng Điều dưỡng, lịch ca điều dưỡng/kỹ thuật viên, yêu cầu nghỉ/đổi ca, chấm công/lương, tối ưu lịch tự động nằm ngoài đợt này. LAB_LEAD là tên quy ước tương lai của tài liệu này, thay các tên CHIEF_TECHNOLOGIST trước đây; không thêm enum/API khả dụng cho loại hoãn chỉ để dự phòng.
- Không tạo service mới; không biến quầy thu/nhà thuốc thành khoa lâm sàng. Inpatient/Surgery vẫn deferred, source được bảo toàn.

## 2. Hiện trạng đã kiểm và khoảng cách

Đã đọc source tại master d006fa64 (PR #556) và xem lại ảnh `docs/acceptance/role-ux-2026-10-31/after/11-manager-browse-first.png`. Không khởi động hoặc thử UI mới trong vòng cập nhật thiết kế. Codebase-memory không kết nối được; đã fallback đọc/rg source.

- `frontend/src/lib/session.ts` có một role; RoleGate/sidebar chưa biểu diễn quyền quản lý bổ sung cho DOCTOR.
- `OrganizationWorkspace.tsx` đưa non-ADMIN về hồ sơ cá nhân dù menu nói nhân sự/khoa.
- `ScheduleManagementWorkspace.tsx` và ConsultationSessionController đã có tạo/sửa/trạng thái buổi khám, bác sĩ, khoa, phòng, số chỗ, revision, idempotency và kiểm scope. Tái sử dụng chúng, không xây hệ thống shift mới.
- Core `Department` có departmentHeadId và DepartmentType gồm CLINICAL/PARACLINICAL/ADMINISTRATIVE. DepartmentController có đường cập nhật departmentHeadId; cần chặn đường cũ trở thành nguồn bổ nhiệm/quyền độc lập.
- SchedulingAuthority hiện trả currentRole/canManageSchedules/scopeRevision; quyền hiện tại dựa trên ADMIN hoặc MANAGER được cấp scope. Cần mở rộng policy cho DOCTOR có bổ nhiệm hợp lệ, không thay mọi DOCTOR thành manager.
- Controller báo cáo/doanh thu cho ADMIN/MANAGER và departmentId tùy chọn. Không tự mở các API này cho DOCTOR chỉ để có dashboard thống kê; đợt đầu dùng số liệu vận hành từ Clinical. Đọc tài chính cần quyền và hợp đồng riêng.

## 3. Mô hình bổ nhiệm và điều kiện

Organization trong Core sở hữu quan hệ bổ nhiệm, vòng đời, quyền hiện hành và audit. Clinical sở hữu buổi khám, số chỗ và lượt bệnh nhân; không đọc DB Core.

Mô hình logic mới dự kiến `department_management_appointments`: id, staff_id, department_id, position_type, appointed_at, ended_at, appointed_by, ended_by, lý do bổ nhiệm, lý do miễn nhiệm, revision. Lưu actor là accountId và người được bổ nhiệm là staffId; account hiện tại phải liên kết đúng staff và có DOCTOR. Nếu implementation cần bind accountId của người được bổ nhiệm, hợp đồng phải khóa rõ việc thay association vô hiệu hóa quyền; không tự chuyển quyền sang người/tài khoản khác.

- Chỉ mở position_type=DEPARTMENT_HEAD cho DepartmentType.CLINICAL trong MVP. Các loại hoãn bị server từ chối, không chỉ ẩn trên form.
- Account, staff và khoa phải active; role DOCTOR, chức danh/chuyên môn và khoa công tác phù hợp với khoa bổ nhiệm. Tiêu chí chuyên môn bổ sung chỉ được xác nhận theo dữ liệu/chính sách được duyệt; role không phải bằng chứng đủ điều kiện hành nghề.
- Hiệu lực bắt đầu ngay tại thời điểm server; ended_at=null biểu thị nhiệm kỳ chưa kết thúc. Bổ nhiệm tương lai/lịch nhiệm kỳ phức tạp ngoài MVP.
- Unique partial constraints cho nhiệm kỳ chưa kết thúc theo department_id và staff_id, cộng transaction/locking; không chỉ kiểm tra trước bằng query. Hai admin gửi đồng thời không tạo hai trưởng khoa hoặc cho bác sĩ kiêm hai khoa.
- Thay trưởng khoa: một command có revision và idempotency, khóa khoa/đối tượng liên quan, kết thúc nhiệm kỳ cũ và tạo nhiệm kỳ mới trong cùng transaction. Nếu validation/insert/audit lỗi thì rollback cả hai. Không có khoảng trống hoặc hai người cùng hiệu lực do hai request rời.
- Miễn nhiệm kết thúc bản ghi, không xóa lịch sử. Audit bổ nhiệm/miễn nhiệm/thay thế, lý do và outbox cùng transaction.
- departmentHeadId chỉ là projection tương thích từ quan hệ bổ nhiệm. Trong MVP Core đồng bộ projection trong cùng transaction; thao tác cập nhật khoa thông thường không được đặt head độc lập. Dữ liệu head cũ phải được kiểm kê, chưa xác minh không tự chuyển thành grant.
- Staff chuyển khoa, ngừng làm việc, account bị khóa/đổi role hoặc association không khớp thì authority từ chối ngay; admin phải xử lý nhiệm kỳ đang mở trước khi bổ nhiệm người thay. Không dùng token cũ để tiếp tục quản lý.

## 4. Ma trận quyền đích

| Hành động | ADMIN | DOCTOR + bổ nhiệm còn hiệu lực | DOCTOR thường | NURSE |
|---|---|---|---|---|
| Bổ nhiệm/miễn nhiệm/thay trưởng khoa | Có | Không | Không | Không |
| Tạo/sửa/dừng/mở buổi khám | Theo quyền quản trị lịch hiện có | Khoa được bổ nhiệm | Không | Không |
| Phân công bác sĩ/phòng cho buổi khám | Theo quyền hiện có | Khoa được bổ nhiệm; giữ eligibility/registration guards | Không | Không |
| Xem lịch toàn khoa và thống kê vận hành | Theo quyền hiện có | Khoa được bổ nhiệm | Không mặc định | Lịch cần cho tiếp nhận theo quyền hiện có |
| Lịch cá nhân, khám/chẩn đoán/kê đơn | Theo quyền chuyên môn thực tế | DOCTOR theo phân công hiện có | Theo phân công hiện có | Không |
| Đăng ký/tiếp nhận | Theo quyền vận hành hiện có | Không được thêm từ bổ nhiệm | Không được thêm từ bổ nhiệm | Theo quyền hiện có |
| Đọc/sửa mọi bệnh án của khoa | Không mở rộng trong đợt này | Không được thêm từ bổ nhiệm | Không được thêm | Không được thêm |
| Doanh thu/tài chính toàn khoa | Theo hợp đồng hiện có | Không được thêm từ bổ nhiệm | Không được thêm | Không được thêm |

Ma trận là quyền đích của tính năng này; không dùng nó để âm thầm gỡ hoặc thêm các quyền ADMIN/NURSE đang phục vụ luồng khác. Chức vụ không đồng nghĩa với quyền khám; không mở quyền chuyên môn cho MANAGER độc lập.

## 5. Hai dashboard và UI admin

### Khám bệnh

Mặc định sau đăng nhập DOCTOR, sau bước đổi mật khẩu bắt buộc nếu có. Có lịch cá nhân ngày/tuần, hàng khám được phân công, mở bệnh án/chỉ định/kê đơn theo quyền hiện có. Header hiển thị **Sang Quản lý khoa** khi capabilities xác minh được bổ nhiệm.

### Quản lý khoa

Header hiển thị “Trưởng khoa · [Tên khoa]” và **Về Khám bệnh**. Menu gọn gồm Tổng quan khoa, Lịch toàn khoa, Buổi khám & phòng. Có thêm Lịch làm việc bác sĩ; không có quản trị tài khoản, ca điều dưỡng, payroll hoặc phân tầng nhân sự.

- Mặc định khoa được bổ nhiệm, không bắt tìm lại khoa và không hiện “Toàn viện”. Backend vẫn kiểm khoa trên mọi request; khóa picker không phải cơ chế bảo mật.
- Dashboard hôm nay/tuần: buổi đang mở/ngừng nhận, sức chứa, giữ chỗ/xác nhận/đã phục vụ và quan sát lúc nào. Không gọi số đã đăng ký là số bệnh nhân thực tế đang đợi nếu chưa có nguồn trạng thái tương ứng; lỗi nguồn không biến thành 0.
- Lịch theo ngày/tuần, nhóm bác sĩ/phòng; mở chi tiết để tạo/sửa/trạng thái bằng hợp đồng Clinical hiện có. Số tổng phải được tổng hợp server cho đúng phạm vi, không đếm một trang pagination rồi gọi là tổng khoa.
- Giữ giới hạn ngoại trú 07:00–17:00, eligibility phòng/bác sĩ, chống trùng, capacity và các hạn chế khi đã có giữ chỗ/thu tiền/đang khám. Không tự đổi hoặc hủy bệnh nhân để đổi người khám.
- URL riêng cho khu quản lý; truy cập trực tiếp/reload/back vẫn kiểm quyền. Nút chuyển không đổi role/session; cảnh báo form chưa lưu ở cả hai phía, không tự submit.
- Khi miễn nhiệm trong phiên: BE chặn request quản lý tiếp theo theo authority hiện hành; FE cập nhật capabilities khi vào khu, focus và khi bị từ chối, xóa cache quản lý và đưa về Khám bệnh phù hợp. Không phụ thuộc JWT hết hạn; không hứa xóa dữ liệu đã render tức thời nếu không có cơ chế push.

### Bổ nhiệm trong UI admin

Trong chi tiết khoa CLINICAL: hiện trưởng khoa đang hiệu lực/lịch sử, chọn bác sĩ đủ điều kiện, nhập lý do và xem trước thay đổi. Thay thế phải hiển thị rõ nhiệm kỳ cũ sẽ kết thúc. Miễn nhiệm có xác nhận đối tượng/lý do. Không đặt nút bổ nhiệm trong dashboard trưởng khoa.

### Điều dưỡng và Lab

Giữ dashboard chuyên môn và quy trình hiện tại. Thông tin phụ trách Lab nếu hiển thị là dữ liệu tổ chức, không sinh grant quản lý hoặc quyền ký kết quả. Không có nút chuyển quản lý chỉ vì role NURSE/LAB_TECH trong MVP này.

## 6. Hợp đồng và kiểm quyền cần thay đổi

1. Core: API admin quản lý bổ nhiệm/lịch sử; API capabilities chính chủ và internal management authority. Endpoint `GET /api/v1/org/me/capabilities` mới là đề xuất, chưa tồn tại/khóa hợp đồng. Wire fields cần có khu vực hợp lệ, grant hành động theo khoa, identity, revision và observedAt; booleans chỉ phục vụ UI, không thay authority BE.
2. Gateway: cho đường quản lý lịch được tiếp cận bởi DOCTOR phù hợp policy mới; không tự tin role DOCTOR để xác nhận bổ nhiệm. Core/Clinical quyết định quyền với signed principal, không body actor hoặc header tùy ý. Capability/admin endpoints vẫn default deny.
3. Clinical: trên từng lệnh tạo/sửa/trạng thái và read toàn khoa, xác minh DOCTOR đúng actor/staff, account/staff/khoa active và bổ nhiệm hiện hành; từ chối ngoài khoa/miễn nhiệm/mismatch. Core unavailable trả unavailable và không ghi, không giả quyền rỗng hay dùng positive cache cũ cho mutation.
4. Directory/reference: endpoint chọn bác sĩ/phòng phải phục vụ được DOCTOR có bổ nhiệm đúng khoa; không mở quyền đọc hồ sơ nhạy cảm hoặc danh bạ admin cho toàn bộ DOCTOR.
5. FE: quyền chuyên môn và quyền bổ nhiệm là hai nguồn hiển thị khác nhau; cập nhật RoleGate/sidebar/landing theo capabilities, giữ session identity và theme. Cache theo session và authority revision; không dùng workspace đã chọn làm bằng chứng quyền.
6. Lab/Pharmacy/Billing/Report: không mở rộng role/capability qua chức vụ mới. Dashboard vận hành dùng projection Clinical tối thiểu, không lấy bệnh án/chẩn đoán/chi tiết thanh toán không cần thiết.

Khóa DTO/controller/serializer/migration/fixture và kiểm consumer trước thay đổi. Duy trì backward compatibility hoặc version internal authority; so currentRole=DOCTOR với signed role thật, không giả currentRole=MANAGER để vượt guard cũ.

## 7. Tương thích dữ liệu và MANAGER hiện có

Không tự đổi role tài khoản hoặc tự bổ nhiệm từ departmentHeadId/staff.departmentId. Kiểm kê trước các head, account–staff links và schedule-management-scope hiện có. Legacy MANAGER có thể giữ quyền quản lý lịch đã cấp qua nhánh authority cũ trong giai đoạn chuyển tiếp, với nhãn quản lý lịch; không được coi là trưởng khoa đương nhiệm và không tự có quyền bác sĩ. Quy tắc một trưởng khoa áp dụng cho quan hệ bổ nhiệm mới.

Migration không biến mọi legacy MANAGER thành DOCTOR, không biến quyền lịch thành quyền tài chính và không mất receipt/audit hiện có. Thay đổi hoặc thu hồi legacy grant phải là hành động admin tường minh. Feature gates cho phép triển khai/rollback UI và authority có kiểm soát; bổ nhiệm mới không được phát hành tới consumer chưa hiểu policy mới.

## 8. Phân ca bác sĩ — bổ sung được duyệt

Clinical sở hữu ca làm việc bác sĩ để giữ việc liên kết buổi khám và chống trùng trong cùng transaction/store. Mỗi ca có bác sĩ/khoa/phòng, ngày, giờ bắt đầu/kết thúc, trạng thái ACTIVE/CANCELLED, revision và audit. Giới hạn giờ 07:00–17:00, không ca qua ngày; bác sĩ/phòng đủ eligibility, không trùng doctor hoặc phòng khám độc quyền. Trưởng khoa chỉ phân công trong khoa mình; bác sĩ đọc ca của chính mình.

Từ ca ACTIVE, thao tác Tạo buổi khám tạo rõ capacity và break trong giờ ca; không tự tạo suất khi lưu ca. Link giữ identity cùng bác sĩ/khoa/phòng/ngày. Dùng một transaction Clinical và lock để chống race hủy/sửa ca với tạo buổi. Khi còn buổi khám liên kết chưa CANCELLED, từ chối sửa cấu trúc/hủy ca, hướng dẫn xử lý buổi khám bằng guards hiện có. Không tự hủy/đổi bác sĩ cho lượt đã có người bệnh hoặc thu tiền. Buổi khám cũ không có link vẫn hoạt động như trước.

Các ràng buộc chống trùng/bảo vệ liên kết có test PG thật. Không hứa ca làm việc là chấm công hoặc quyền làm nghiệp vụ khác. Nút chuyển dashboard vẫn chỉ đổi khu vực UI.

## 9. Các hạng mục triển khai

- [x] **Core:** bảng bổ nhiệm, uniqueness/transaction thay thế, eligibility, audit/outbox/idempotency, API admin + capabilities, đồng bộ head projection và chặn write cũ bỏ qua bổ nhiệm.
- [x] **Gateway + Clinical:** authority DOCTOR bổ nhiệm, read/mutation/directory theo khoa, thống kê vận hành đúng scope, regression legacy và các guards lịch/thu tiền.
- [x] **Clinical phân ca:** ca bác sĩ, lịch khoa/chính chủ, chống trùng, tạo buổi từ ca và bảo vệ liên kết khi sửa/hủy.
- [x] **Frontend:** admin bổ nhiệm/miễn nhiệm, hai dashboard, nút chuyển, khoa mặc định, guard dữ liệu chưa lưu và xử lý authority đổi trong phiên.
- [ ] **Nghiệm thu:** dữ liệu demo trước/sau bổ nhiệm/miễn nhiệm, hai admin đồng thời, vượt quyền và UI thực tế có screenshot; chạy cleanup sau demo.

Frontend Organization của Hoàng Anh, Appointment/shared của Vinh, backend toàn bộ của Vinh theo AGENTS. Nội dung này xác định việc cần làm, không đổi ownership lâu dài; việc triển khai màn Organization phải nằm trong task-scoped authorization hoặc bàn giao được xác nhận.

## 10. Điều kiện nghiệm thu

- bs.a cùng accountId/staffId trước–trong–sau bổ nhiệm; giữ DOCTOR, khám/kê đơn theo phân công; không có bổ nhiệm thì không quản lý lịch khoa.
- Bác sĩ khoa Nội được bổ nhiệm không quản lý khoa Ngoại bằng sửa URL/body; ordinary DOCTOR/NURSE/LAB_TECH không có quyền từ nút chuyển hoặc API capabilities giả.
- Hai admin bổ nhiệm đồng thời chỉ một người thắng; hai khoa cố bổ nhiệm cùng bác sĩ chỉ một bổ nhiệm hiệu lực. Request thay thế lỗi phải giữ nguyên nhiệm kỳ cũ. Double-click/retry không tạo lịch sử lặp.
- Miễn nhiệm có lịch sử và projection khớp; chỉnh departmentHeadId qua đường cũ không tạo quyền. Account khóa, staff inactive/chuyển khoa hoặc association đổi đều bị authority chặn.
- Phiên/token trước miễn nhiệm không quản lý tiếp; quyền bác sĩ không bị mất. Core timeout không cho mutation hoặc trả số 0 giả. Capability loading khác với denied.
- Trưởng khoa không tự đọc/sửa bệnh án người khác ngoài quyền chuyên môn hiện có; không tự có quyền ký Lab, thu tiền, báo cáo tài chính hay ADMIN.
- Chuyển hai dashboard, reload/back, form chưa lưu, đổi tài khoản và màn hình nhỏ hoạt động đúng. Số liệu thống kê đúng toàn scope với nhiều trang, có observedAt và trạng thái rỗng/lỗi rõ.
- Tạo/sửa buổi khám vẫn chống trùng và chặn đổi cấu hình không được phép khi đã có bệnh nhân; giữ receipt/idempotency/payment guards cũ.
- Seed minh họa khoa CLINICAL hợp lệ và hai bác sĩ, người chưa bổ nhiệm/người được bổ nhiệm/legacy manager/nurse/lab; không hard-code UUID thật từ ví dụ. Không biến khoa Xét nghiệm thành CLINICAL để dễ cấp quyền.

## 11. Những gì bị hoãn so với bản đề xuất trước

Roster điều dưỡng/kỹ thuật viên, StaffingRequirement, nghỉ/đổi ca, công bố lịch nhân sự, staffing commitment/hold xuyên Core–Clinical và staff notifications đều không cần cho MVP này. Không tạo bảng/API/UI cho các phần đó trong đợt đầu. Có thể tham khảo lại Microsoft Shifts/NHS ở giai đoạn mở rộng, nhưng các mẫu đó không xác định phạm vi hay định mức cho MVP hiện tại.

Bằng chứng triển khai và phạm vi UI thực sự đã kiểm tra: [nghiệm thu 01/11](../../acceptance/department-head-2026-11-01/README.md). Mục nghiệm thu UI giữ mở cho các thao tác ghi/hộp thoại chưa hoàn tất; triển khai và kiểm tra tự động đã xong.
