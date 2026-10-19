"""Author Review 2 role use cases; render/publish with the existing Word toolchain.

Run once to regenerate the role sources and report sections after editing DATA.
Labels are paired (English, Vietnamese); graph IDs and edge direction are shared.
"""
from pathlib import Path
import copy
import json
import re

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'word-compact'

# node id: (English label, Vietnamese label). Edges: source, target, kind, condition.
# Main tasks are directly available to the actor; includes model required behavior,
# while extension arrows point FROM the optional task TO its base task.
DATA = [
    dict(number=29, role='ADMIN', title=('Administrator tasks', 'Tác vụ quản trị viên'),
         nodes={
             'A1': ('Create account', 'Tạo tài khoản'),
             'A2': ('Set role and identity', 'Gán vai trò và định danh'),
             'A3': ('Change account status', 'Đổi trạng thái tài khoản'),
             'A4': ('Check target account', 'Kiểm tra tài khoản đích'),
             'A5': ('Create staff member', 'Tạo hồ sơ nhân viên'),
             'A6': ('Record staff details', 'Ghi thông tin nhân viên'),
             'A7': ('Maintain staff profile', 'Cập nhật hồ sơ nhân viên'),
             'A8': ('View staff profile', 'Xem hồ sơ nhân viên'),
             'A9': ('Transfer department', 'Chuyển khoa công tác'),
             'A10': ('Validate target department', 'Kiểm tra khoa tiếp nhận'),
             'A11': ('Maintain departments', 'Cập nhật khoa'),
             'A12': ('View department', 'Xem thông tin khoa'),
             'A13': ('Create department', 'Tạo khoa'),
             'A14': ('Enter code and name', 'Nhập mã và tên khoa'),
             'A15': ('View management reports', 'Xem báo cáo quản lý'),
             'A16': ('Choose period and scope', 'Chọn kỳ và phạm vi'),
             'A17': ('Send notification', 'Gửi thông báo'),
             'A18': ('Choose recipient and channel', 'Chọn người nhận và kênh'),
         }, main=['A1','A3','A5','A7','A11','A13','A15','A17'],
         edges=[('A1','A2','include',None),('A3','A4','include',None),
                ('A5','A6','include',None),('A7','A8','include',None),
                ('A9','A7','extend',('transfer requested','cần chuyển khoa')),
                ('A9','A10','include',None),('A11','A12','include',None),
                ('A13','A14','include',None),('A15','A16','include',None),('A17','A18','include',None)],
         sources=['backend/core-service/src/main/java/com/mediflow/organization/web/controller/AccountController.java',
                  'backend/core-service/src/main/java/com/mediflow/organization/web/controller/StaffController.java',
                  'backend/core-service/src/main/java/com/mediflow/organization/web/controller/DepartmentController.java',
                  'backend/report-service/src/main/java/com/mediflow/report/web/ReportController.java',
                  'backend/notification-service/src/main/java/com/mediflow/notification/web/NotificationController.java',
                  'backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java'],
         note=('This diagram focuses on organization/account administration. ADMIN also has explicit permissions in the other seven context diagrams; it does not bypass business guards. No account deletion or arbitrary permission editor is invented.',
               'Hình tập trung quản trị tổ chức/tài khoản. ADMIN còn có các quyền cụ thể tại những nghiệp vụ khác theo ma trận nguồn; không bỏ qua business guards. Không thêm xóa tài khoản hay màn hình chỉnh quyền tùy ý.')),
    dict(number=30, role='MANAGER', title=('Manager tasks', 'Tác vụ quản lý'),
         nodes={
             'M1': ('View outpatient report', 'Xem báo cáo ngoại trú'),
             'M2': ('Select reporting period', 'Chọn kỳ báo cáo'),
             'M3': ('Inspect source coverage', 'Xem độ phủ nguồn dữ liệu'),
             'M4': ('View financial report', 'Xem báo cáo tài chính'),
             'M5': ('Select day or month', 'Chọn ngày hoặc tháng'),
             'M6': ('View medicine statistics', 'Xem thống kê thuốc'),
             'M7': ('Select period and limit', 'Chọn kỳ và số lượng'),
             'M8': ('View appointments', 'Xem danh sách lịch hẹn'),
             'M9': ('Read appointment page', 'Đọc trang lịch hẹn'),
             'M10': ('Filter by department', 'Lọc theo khoa'),
             'M11': ('View staff directory', 'Xem danh sách nhân viên'),
             'M12': ('Read staff page', 'Đọc trang nhân viên'),
         }, main=['M1','M4','M6','M8','M11'],
         edges=[('M1','M2','include',None),('M1','M3','include',None),
                ('M4','M5','include',None),('M6','M7','include',None),
                ('M8','M9','include',None),('M10','M8','extend',('department selected','đã chọn khoa')),
                ('M11','M12','include',None)],
         sources=['backend/report-service/src/main/java/com/mediflow/report/web/OutpatientReportController.java',
                  'backend/report-service/src/main/java/com/mediflow/report/web/FinancialSnapshotReportController.java',
                  'backend/report-service/src/main/java/com/mediflow/report/web/ReportController.java',
                  'backend/billing-service/src/main/java/com/mediflow/billing/web/RevenueController.java',
                  'backend/clinical-service/src/main/java/com/mediflow/clinical/web/AppointmentController.java',
                  'backend/core-service/src/main/java/com/mediflow/organization/web/controller/StaffController.java'],
         note=('Read-only management tasks: daily/monthly reports, top medicines, department revenue, appointment/lab lists, staff and department directories. MANAGER cannot create accounts, update staff or record clinical/financial commands. Snapshot reports remain subject to their runtime availability and coverage.',
               'Nghiệp vụ quản lý chỉ đọc: báo cáo ngày/tháng, top thuốc, doanh thu khoa, danh sách lịch hẹn/xét nghiệm, nhân viên và khoa. MANAGER không tạo tài khoản, sửa nhân viên hay thực hiện lệnh khám/thu tiền. Báo cáo snapshot phụ thuộc availability và coverage tại runtime.')),
    dict(number=31, role='DOCTOR', title=('Doctor tasks', 'Tác vụ bác sĩ'),
         nodes={
             'D1': ('Review clinical history', 'Tra cứu hồ sơ khám'),
             'D2': ('View patient records', 'Xem các lần khám'),
             'D3': ('Start examination [V1]', 'Bắt đầu khám [V1]'),
             'D4': ('Check exam clearance', 'Kiểm tra quyền khám'),
             'D5': ('Record consultation', 'Ghi nhận khám bệnh'),
             'D6': ('Record clinical findings', 'Ghi nội dung thăm khám'),
             'D7': ('Add diagnosis', 'Thêm chẩn đoán'),
             'D8': ('Order lab test', 'Chỉ định xét nghiệm'),
             'D9': ('Specify test and patient', 'Chọn xét nghiệm và bệnh nhân'),
             'D10': ('Review lab results', 'Xem kết quả xét nghiệm'),
             'D11': ('Issue prescription', 'Kê đơn thuốc'),
             'D12': ('Select medicines and dose', 'Chọn thuốc và liều dùng'),
             'D13': ('Complete visit [V1]', 'Hoàn tất lượt khám [V1]'),
             'D14': ('Confirm diagnosis / disposition', 'Xác nhận chẩn đoán / hướng xử trí'),
             'D15': ('Cancel prescription', 'Hủy đơn thuốc'),
             'D16': ('Check cancellation eligibility', 'Kiểm tra điều kiện hủy'),
         }, main=['D1','D3','D5','D10','D11','D13'],
         edges=[('D1','D2','include',None),('D3','D4','include',None),
                ('D5','D6','include',None),('D7','D5','extend',('diagnosis added','bổ sung chẩn đoán')),
                ('D8','D5','extend',('test indicated','cần xét nghiệm')),
                ('D8','D9','include',None),('D11','D12','include',None),
                ('D13','D14','include',None),('D15','D11','extend',('cancellation requested','yêu cầu hủy đơn')),
                ('D15','D16','include',None)],
         sources=['backend/clinical-service/src/main/java/com/mediflow/clinical/web/AppointmentController.java',
                  'backend/clinical-service/src/main/java/com/mediflow/clinical/web/MedicalRecordController.java',
                  'backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalCareFinanceController.java',
                  'backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalLabOrderController.java',
                  'backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java',
                  'backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/PrescriptionController.java'],
         note=('Clinical authority and patient/context checks still apply. [V1] requires care-finance-v2 enabled; start-exam needs exact EXAM clearance or the supported audited override. AppointmentController additionally authorizes appointment reads/updates/status; operational report reads and lab cancellation are also source-authorized. A doctor cannot perform nurse check-in, pharmacy dispensing or cashier payment.',
               'Vẫn kiểm tra clinical authority, bệnh nhân và context. [V1] cần bật care-finance-v2; start-exam cần exact EXAM clearance hoặc audited override đã hỗ trợ. AppointmentController còn cấp quyền xem/sửa lịch/trạng thái; source cũng cho phép xem báo cáo vận hành và hủy xét nghiệm. Bác sĩ không check-in thay điều dưỡng, cấp thuốc thay dược sĩ hay thu tiền thay thu ngân.')),
    dict(number=32, role='NURSE', title=('Nurse tasks', 'Tác vụ điều dưỡng'),
         nodes={
             'N1': ('Register patient', 'Tạo hồ sơ bệnh nhân'),
             'N2': ('Record patient details', 'Nhập thông tin bệnh nhân'),
             'N3': ('Update patient', 'Cập nhật bệnh nhân'),
             'N4': ('Find patient record', 'Tra cứu bệnh nhân'),
             'N5': ('Schedule appointment', 'Tạo lịch hẹn'),
             'N6': ('Choose patient / doctor / date', 'Chọn bệnh nhân / bác sĩ / ngày'),
             'N7': ('Review appointment', 'Xem lịch hẹn'),
             'N8': ('Read appointment details', 'Đọc chi tiết lịch'),
             'N9': ('Reschedule appointment', 'Điều chỉnh lịch hẹn'),
             'N10': ('Validate new schedule', 'Kiểm tra lịch mới'),
             'N11': ('Check in [V1]', 'Tiếp nhận check-in [V1]'),
             'N12': ('Check appointment state', 'Kiểm tra trạng thái lịch'),
             'N13': ('Read care information', 'Tra cứu thông tin chăm sóc'),
             'N14': ('View medical record / lab', 'Xem hồ sơ khám / xét nghiệm'),
             'N15': ('View patient notifications', 'Xem thông báo bệnh nhân'),
         }, main=['N1','N3','N5','N7','N11','N13','N15'],
         edges=[('N1','N2','include',None),('N3','N4','include',None),
                ('N5','N6','include',None),('N7','N8','include',None),
                ('N9','N7','extend',('schedule change requested','cần đổi lịch')),
                ('N9','N10','include',None),('N11','N12','include',None),
                ('N13','N14','include',None)],
         sources=['backend/core-service/src/main/java/com/mediflow/patient/web/PatientController.java',
                  'backend/clinical-service/src/main/java/com/mediflow/clinical/web/AppointmentController.java',
                  'backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalCareFinanceController.java',
                  'backend/clinical-service/src/main/java/com/mediflow/clinical/web/MedicalRecordController.java',
                  'backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java',
                  'backend/notification-service/src/main/java/com/mediflow/notification/web/NotificationController.java'],
         note=('[V1] check-in requires care-finance-v2 enabled and does not start an examination. NURSE can query lab list/detail and notification lists by patient, but cannot write diagnoses, lab results, prescriptions or payments; individual notification detail is not granted to NURSE. There is no separate RECEPTIONIST role.',
               '[V1] check-in cần bật care-finance-v2 và không đồng nghĩa bắt đầu khám. NURSE xem danh sách/chi tiết xét nghiệm và danh sách thông báo theo bệnh nhân; không ghi chẩn đoán, kết quả xét nghiệm, đơn thuốc hay thanh toán; không có quyền mở endpoint chi tiết thông báo. Không tạo role RECEPTIONIST.')),
    dict(number=33, role='LAB_TECH', title=('Laboratory technician tasks', 'Tác vụ kỹ thuật viên xét nghiệm'),
         nodes={
             'L1': ('Review test worklist', 'Xem danh sách xét nghiệm'),
             'L2': ('Read test page', 'Đọc trang xét nghiệm'),
             'L3': ('Filter pending tests', 'Lọc xét nghiệm chờ'),
             'L4': ('Start test [V1 gate]', 'Bắt đầu xét nghiệm [V1 gate]'),
             'L5': ('Check test state', 'Kiểm tra trạng thái xét nghiệm'),
             'L6': ('Check LAB_TEST clearance [V1]', 'Kiểm tra LAB_TEST clearance [V1]'),
             'L7': ('Record test results', 'Ghi kết quả xét nghiệm'),
             'L8': ('Enter result values', 'Nhập các giá trị kết quả'),
             'L9': ('Validate completion time', 'Kiểm tra thời gian hoàn tất'),
             'L10': ('Cancel test', 'Hủy xét nghiệm'),
             'L11': ('Record cancellation reason', 'Ghi lý do hủy'),
             'L12': ('Update allowed status', 'Cập nhật trạng thái hợp lệ'),
             'L13': ('Validate state transition', 'Kiểm tra chuyển trạng thái'),
         }, main=['L1','L4','L7','L10','L12'],
         edges=[('L1','L2','include',None),('L3','L1','extend',('status filter selected','đã chọn bộ lọc')),
                ('L4','L5','include',None),('L4','L6','include',None),
                ('L7','L8','include',None),('L7','L9','include',None),
                ('L10','L11','include',None),('L12','L13','include',None)],
         sources=['backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java'],
         note=('Start/cancel remain subject to lifecycle and feature availability; exact LAB_TEST clearance applies to the V1 protected action. LAB_TECH can search/read tests and write results/status, but cannot create an indication, prescribe or collect payment. Filtering is an optional extension of the worklist.',
               'Start/cancel tuân theo lifecycle và feature availability; exact LAB_TEST clearance áp dụng cho protected action V1. LAB_TECH tra cứu xét nghiệm, ghi kết quả/trạng thái; không tạo chỉ định, kê đơn hay thu tiền. Lọc trạng thái là nhánh mở rộng tùy chọn của danh sách.')),
    dict(number=34, role='PHARMACIST', title=('Pharmacist tasks', 'Tác vụ dược sĩ'),
         nodes={
             'P1': ('Find medicine', 'Tra cứu thuốc'),
             'P2': ('Read medicine catalogue', 'Đọc danh mục thuốc'),
             'P3': ('Filter by keyword', 'Lọc theo từ khóa'),
             'P4': ('Create medicine', 'Thêm thuốc'),
             'P5': ('Enter medicine information', 'Nhập thông tin thuốc'),
             'P6': ('Adjust stock', 'Điều chỉnh tồn kho'),
             'P7': ('Record quantity and reason', 'Ghi số lượng và lý do'),
             'P8': ('Review prescription', 'Tra cứu đơn thuốc'),
             'P9': ('Read prescription lines', 'Xem các dòng thuốc'),
             'P10': ('Dispense prescription [V1]', 'Cấp thuốc theo đơn [V1]'),
             'P11': ('Check exact clearance', 'Kiểm tra clearance đúng đơn'),
             'P12': ('Validate stock / reservation', 'Kiểm tra tồn / giữ chỗ'),
             'P13': ('Record dispense and stock change', 'Ghi cấp thuốc và biến động tồn'),
         }, main=['P1','P4','P6','P8','P10'],
         edges=[('P1','P2','include',None),('P3','P1','extend',('keyword supplied','có từ khóa')),
                ('P4','P5','include',None),('P6','P7','include',None),('P8','P9','include',None),
                ('P10','P11','include',None),('P10','P12','include',None),('P10','P13','include',None)],
         sources=['backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/DrugController.java',
                  'backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/PrescriptionController.java'],
         note=('The public outpatient dispense action resolves the signed actor and V1 lifecycle; exact PRESCRIPTION clearance, stock and reservation guards apply. It is explicit pharmacist action, not automatic dispensing on payment. PHARMACIST cannot create/cancel prescriptions; expiration is system lifecycle behavior rather than a human task.',
               'Lệnh cấp thuốc ngoại trú lấy actor đã ký và lifecycle V1; kiểm tra exact PRESCRIPTION clearance, tồn và reservation. Dược sĩ chủ động cấp thuốc, không tự cấp khi nhận sự kiện thanh toán. PHARMACIST không tạo/hủy đơn; hết hạn là xử lý lifecycle của hệ thống, không phải tác vụ người dùng.')),
    dict(number=35, role='CASHIER', title=('Cashier tasks', 'Tác vụ thu ngân'),
         nodes={
             'C1': ('Find invoice', 'Tra cứu hóa đơn'),
             'C2': ('View patient invoices', 'Xem hóa đơn bệnh nhân'),
             'C3': ('Create invoice', 'Tạo hóa đơn'),
             'C4': ('Validate invoice items', 'Kiểm tra các khoản thu'),
             'C5': ('Record invoice payment', 'Ghi thanh toán hóa đơn'),
             'C6': ('Validate payment command', 'Kiểm tra lệnh thanh toán'),
             'C7': ('Find payment request [V1]', 'Tra cứu yêu cầu thu [V1]'),
             'C8': ('Identify source and target', 'Xác định nguồn và đối tượng'),
             'C9': ('Record ledger payment [V1]', 'Ghi thanh toán ledger [V1]'),
             'C10': ('Check amount and replay key', 'Kiểm tra số tiền và khóa lặp'),
             'C11': ('Return payment receipt', 'Trả biên nhận thanh toán'),
             'C12': ('Refund payment [V1]', 'Hoàn tiền giao dịch [V1]'),
             'C13': ('Check refundable balance', 'Kiểm tra số dư được hoàn'),
             'C14': ('Record amount and reason', 'Ghi số tiền và lý do'),
         }, main=['C1','C3','C5','C7','C9'],
         edges=[('C1','C2','include',None),('C3','C4','include',None),
                ('C5','C6','include',None),('C7','C8','include',None),
                ('C9','C10','include',None),('C9','C11','include',None),
                ('C12','C9','extend',('eligible refund requested','yêu cầu hoàn hợp lệ')),
                ('C12','C13','include',None),('C12','C14','include',None)],
         sources=['backend/billing-service/src/main/java/com/mediflow/billing/web/InvoiceController.java',
                  'backend/billing-service/src/main/java/com/mediflow/billing/web/PaymentRequestDiscoveryController.java',
                  'backend/billing-service/src/main/java/com/mediflow/billing/web/LedgerPaymentController.java',
                  'backend/billing-service/src/main/java/com/mediflow/billing/web/LedgerRefundController.java',
                  'backend/billing-service/src/main/java/com/mediflow/billing/application/service/LedgerRefundService.java'],
         note=('Invoice compatibility and V1 ledger are separate paths, not mandatory consecutive steps. [V1] depends on feature availability. Refund is a subsequent conditional operation on an eligible original payment; its own workflow includes balance checks and amount/reason. Same-key retries must preserve intent. No inpatient/surgery settlement task is activated.',
               'Hóa đơn compatibility và ledger V1 là hai đường xử lý riêng, không phải các bước bắt buộc nối tiếp. [V1] phụ thuộc feature availability. Hoàn tiền là tác vụ mở rộng sau thanh toán gốc đủ điều kiện; có luồng con kiểm tra số dư, số tiền/lý do. Retry cùng khóa phải giữ intent. Không kích hoạt quyết toán nội trú/phẫu thuật.')),
    dict(number=36, role='PATIENT', title=('Patient tasks', 'Tác vụ bệnh nhân'),
         nodes={
             'T1': ('Sign in', 'Đăng nhập'),
             'T2': ('Submit username / password', 'Nhập tên đăng nhập / mật khẩu'),
             'T3': ('Receive access / refresh tokens', 'Nhận access / refresh token'),
             'T4': ('Renew session', 'Gia hạn phiên truy cập'),
             'T5': ('Validate refresh identity', 'Kiểm tra định danh refresh'),
             'T6': ('Read own notifications', 'Xem thông báo của mình'),
             'T7': ('Check patient ownership', 'Kiểm tra đúng bệnh nhân'),
             'T8': ('Read notification page', 'Xem danh sách thông báo'),
             'T9': ('Open notification', 'Mở chi tiết thông báo'),
             'T10': ('Read notification content', 'Đọc nội dung thông báo'),
         }, main=['T1','T4','T6'],
         edges=[('T1','T2','include',None),('T1','T3','include',None),
                ('T4','T5','include',None),('T6','T7','include',None),('T6','T8','include',None),
                ('T9','T6','extend',('notification selected','đã chọn thông báo')),
                ('T9','T7','include',None),('T9','T10','include',None)],
         sources=['backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java',
                  'backend/notification-service/src/main/java/com/mediflow/notification/web/NotificationController.java',
                  'backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java'],
         note=('This intentionally reflects the narrow current source permission surface. PATIENT cannot directly create/read appointments, retrieve patient records, lab results, prescriptions or invoices through the current Gateway/controller matrix. Self-registration and profile editing are not exposed. Those are future gaps, not implemented use cases. Notification detail checks ownership independently.',
               'Hình phản ánh đúng phạm vi quyền hiện có. PATIENT chưa trực tiếp tạo/xem lịch hẹn, lấy hồ sơ bệnh nhân, kết quả xét nghiệm, đơn thuốc hay hóa đơn qua ma trận Gateway/controller hiện tại. Chưa có tự đăng ký hoặc sửa hồ sơ. Đây là khoảng trống tương lai, không vẽ thành chức năng đã có. Mở chi tiết thông báo vẫn kiểm tra ownership độc lập.')),
]


def make_source(item, lang, orientation):
    li = 0 if lang == 'en' else 1
    seed = (OUT / orientation / 'mermaid' / f'04-{lang}.mmd').read_text(encoding='utf8')
    config = json.loads(seed.splitlines()[0][len('%%{init: '):-len('}%%')])
    config = copy.deepcopy(config)
    config['themeVariables']['fontSize'] = '18px'
    config['flowchart'].update(curve='linear', nodeSpacing=22 if orientation == 'portrait' else 30,
                               rankSpacing=44, padding=24, wrappingWidth=165)
    config['themeCSS'] = ".nodeLabel p {margin:0;} .edgeLabel {font-size:15px;} .nodeLabel img {display:inline-block!important;width:36px!important;height:48px!important;}"
    actor = re.search(r"data:image/svg\+xml;base64,[^'\"]+", seed).group()
    direction = 'LR' if orientation == 'portrait' else 'TB'
    lines = ['%%{init: ' + json.dumps(config, ensure_ascii=False) + '}%%',
             '%% Role use case: one actor; solid primary task; dashed include/extend.',
             'flowchart ' + direction,
             f' ACTOR["<img src=\'{actor}\' width=\'36\' height=\'48\'/><br/>{item["role"]}"]',
             f' subgraph SYS["MediFlow — {item["role"]}"]']
    for key, label in item['nodes'].items():
        # Mermaid stadium is the portable rounded use-case shape; render.mjs
        # replaces its boundary with an ellipse for these specific figures.
        lines.append(f' {key}(["{label[li]}"])')
    lines.append(' end')
    for key in item['main']:
        lines.append(f' ACTOR --> {key}')
    extension_index = 0
    for src, dst, kind, condition in item['edges']:
        if condition:
            extension_index += 1
        label = f'«{kind}»' + (f' [E{extension_index}]' if condition else '')
        lines.append(f' {src} -.->|"{label}"| {dst}')
    lines += [' classDef actor fill:transparent,stroke:transparent,color:#0f172a;',
              ' classDef main fill:#e0f2fe,stroke:#0284c7,color:#0f172a;',
              ' classDef sub fill:#f0fdfa,stroke:#0f766e,color:#0f172a;',
              ' class ACTOR actor;', ' class ' + ','.join(item['main']) + ' main;']
    supporting=[k for k in item['nodes'] if k not in item['main']]
    if supporting:
        lines.append(' class ' + ','.join(supporting) + ' sub;')
    return '\n'.join(lines) + '\n'


def report_section(lang):
    li = 0 if lang == 'en' else 1
    en = lang == 'en'
    lines = ['<!-- role-usecases:start -->',
             '### Detailed use cases — one diagram per role' if en else '### Use case chi tiết — một sơ đồ cho mỗi vai trò', '',
             ('Figures 29–36 follow the supervisor’s presentation: one human actor per diagram, solid arrows to primary tasks, dashed `«include»` arrows for required supporting behavior, and dashed `«extend»` arrows from a conditional extension to its base task. An extension may have its own included subflow. Arrow direction is relational, not chronological. Staff authentication is a precondition; it is not repeated beside every task. Blue ovals are primary tasks; teal ovals are supporting/extension tasks. These are source capabilities, not proof of UI completeness or production enablement.' if en else
              'Hình 29–36 theo mẫu của giảng viên: mỗi hình có một actor người dùng, mũi tên liền đến tác vụ chính, mũi tên đứt `«include»` cho hành vi con bắt buộc và mũi tên đứt `«extend»` từ nhánh mở rộng có điều kiện về tác vụ gốc. Nhánh mở rộng có thể có luồng con `include` riêng. Mũi tên thể hiện quan hệ, không phải thứ tự thực hiện. Nhân viên đã đăng nhập là tiền điều kiện, không lặp lại ở mọi tác vụ. Oval xanh dương là tác vụ chính; xanh teal là tác vụ con/mở rộng. Hình mô tả khả năng trong source, không khẳng định mọi UI đã hoàn thiện hoặc mọi feature đã bật.'), '',
             ('**Scope correction:** PATIENT currently has authentication/refresh and own-notification access. Overview 04 no longer grants appointment or lab self-service. SYSTEM is a machine identity and has no human-role diagram. All role permissions below are intersected with the Gateway route matrix and downstream guards.' if en else
              '**Hiệu chỉnh phạm vi:** PATIENT hiện có đăng nhập/refresh và truy cập thông báo của mình. Hình tổng quan 04 đã bỏ quyền tự thao tác lịch hẹn/xét nghiệm cho PATIENT. SYSTEM là định danh máy, không tạo sơ đồ role người dùng. Quyền từng role lấy giao của ma trận Gateway và guards downstream.'), '']
    for item in DATA:
        stem = f'{item["number"]:02}-{lang}'
        title = item['role'] + ' — ' + item['title'][li]
        lines += ['#### ' + title, '', f'![{title}](assets/svg/{stem}.svg)', '',
                  '<details>', f'<summary>Mermaid · {stem}</summary>', '', '```mermaid',
                  make_source(item, lang, 'landscape').rstrip(), '```', '', '</details>', '',
                  item['note'][li], '',
                  ('Extension conditions: ' if en else 'Điều kiện mở rộng: ') + '; '.join(
                      f'E{i}: {item["nodes"][src][li]} — {condition[li]}'
                      for i,(src,dst,kind,condition) in enumerate([e for e in item['edges'] if e[3]],1)), '',
                  ('Source evidence: ' if en else 'Đối chiếu source: ') + ' · '.join(
                      f'[{Path(p).stem}](../../../../{p})' for p in item['sources']), '']
    lines += ['<!-- role-usecases:end -->', '']
    return '\n'.join(lines)


def main():
    for item in DATA:
        for lang in ['en', 'vi']:
            stem = f'{item["number"]:02}-{lang}'
            for orientation in ['portrait', 'landscape']:
                (OUT/orientation/'mermaid'/f'{stem}.mmd').write_text(make_source(item,lang,orientation),encoding='utf8')
            (ROOT/'assets/mermaid'/f'{stem}.mmd').write_text(make_source(item,lang,'landscape'),encoding='utf8')
            # Optional Word detail panels retain the same actor and all edges.
            # Each panel is a readable extract; the full diagram remains above.
            for part, start in enumerate(range(0,len(item['main']),2),1):
                panel=copy.deepcopy(item)
                panel['main']=item['main'][start:start+2]
                visible=set(panel['main'])
                while True:
                    expanded=visible | {dst for src,dst,kind,_ in item['edges'] if kind=='include' and src in visible} | {src for src,dst,kind,_ in item['edges'] if kind=='extend' and dst in visible}
                    if expanded==visible:break
                    visible=expanded
                panel['nodes']={k:v for k,v in item['nodes'].items() if k in visible}
                panel['edges']=[e for e in item['edges'] if e[0] in visible and e[1] in visible]
                source=make_source(panel,lang,'portrait')
                # Preserve full-diagram extension IDs when an earlier extension
                # falls in another panel, so captions use the same condition key.
                full_conditions=[e for e in item['edges'] if e[3]]
                panel_conditions=[e for e in panel['edges'] if e[3]]
                source=re.sub(r'\[E(\d+)\]',lambda m:f'[E{full_conditions.index(panel_conditions[int(m[1])-1])+1}]',source)
                (OUT/'panels/mermaid'/f'{stem}-part-{part:02}.mmd').write_text(source,encoding='utf8')
    for lang, name in [('en','01-review-2-standard-en.md'),('vi','02-review-2-standard-vi.md')]:
        p = ROOT/name
        text = p.read_text(encoding='utf8')
        section = report_section(lang)
        if '<!-- role-usecases:start -->' in text:
            text = re.sub(r'<!-- role-usecases:start -->[\s\S]*?<!-- role-usecases:end -->\n?',section,text)
        else:
            marker = '### 1.3. Sequences Diagrams'
            assert marker in text
            text = text.replace(marker, section+'\n'+marker, 1)
        # Keep the embedded overview source identical to its corrected .mmd.
        for number in [4,5]:
            stem = f'{number:02}-{lang}'
            source = (ROOT/'assets/mermaid'/f'{stem}.mmd').read_text(encoding='utf8').rstrip()
            pattern = rf'(<summary>Mermaid · {stem}</summary>\s*```mermaid\n)[\s\S]*?(\n```)'
            text, count = re.subn(pattern,lambda m:m[1]+source+m[2],text)
            assert count == 1
        p.write_text(text,encoding='utf8')
    # Machine-readable evidence supports checking bilingual parity and role coverage.
    (OUT/'role-usecases.json').write_text(json.dumps(DATA,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print('Authored 8 role diagrams × 2 languages × 2 orientations and report sections')


if __name__ == '__main__':
    main()
