"""Regenerate the approved bilingual Review 2 model; run before role_usecases.py.

[[English||Vietnamese]] labels share graph IDs, ordering and branch structure.
This edits only Review 2 documentation; production behavior is not changed.
"""
from pathlib import Path
import copy
import json
import re

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'word-compact'
FIGURES = {}


def figure(number, title, body, note, kind='sequence'):
    FIGURES[number] = dict(title=title, body=body.strip(), note=note, kind=kind)


def localize(text, lang):
    return re.sub(r'\[\[([^\[\]]*?)\|\|([^\[\]]*?)\]\]',
                  lambda m: m[1 if lang == 'en' else 2], text)


def icon(key):
    return f"<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/{key}/{key}-original.svg' width='28' height='28'/>"


# Reuse the existing locally embedded client/server assets and pinned logos.
icons = json.loads((ROOT / 'assets/logos/client-server-icons.json').read_text(encoding='utf8'))
def pictogram(node):
    return icons[{'WEB':'web','MOB':'mobile','CORE':'server'}[node]]


figure(1, '[[System architecture — one backend boundary, six business services||Kiến trúc hệ thống — một vùng backend, sáu service nghiệp vụ]]', '''
flowchart TB
 subgraph CLIENT["Client side"]
  WEB["CLIENT_WEB<br/>NEXT Next.js / React / TypeScript / Tailwind"]
  MOBILE["CLIENT_MOBILE<br/>FLUTTER Flutter / Dart"]
 end
 subgraph SERVER["SERVER_ICON Server side — MediFlow backend"]
  GW["SPRING API Gateway<br/>JWT / [[route authorization||kiểm quyền route]]"]
  subgraph SERVICES["[[Independently deployed business applications||Các ứng dụng nghiệp vụ triển khai độc lập]]"]
   CORE["Core<br/>Organization + Patient"]
   CL["Clinical<br/>[[Appointments / consultation||Lịch hẹn / khám bệnh]]"]
   LAB["Lab<br/>[[Tests / results||Xét nghiệm / kết quả]]"]
   PH["Pharmacy<br/>[[Prescriptions / stock / dispensing||Đơn thuốc / tồn kho / cấp thuốc]]"]
   BI["Billing<br/>[[Charges / payment / clearance||Khoản thu / thanh toán / quyền dịch vụ]]"]
   SU["Support<br/>Report + Notification"]
  end
  MQ["RABBIT RabbitMQ<br/>[[Domain events / retry / DLQ||Sự kiện nghiệp vụ / retry / DLQ]]"]
  EU["SPRING Eureka<br/>[[Service registry||Đăng ký / khám phá service]]"]
  subgraph STORES["POSTGRES [[Context-owned data — no cross-service SQL||Dữ liệu thuộc context — không truy cập SQL xuyên service]]"]
   DCORE[(Organization DB + Patient DB)]
   DCL[(Clinical DB)]
   DLAB[(Lab DB)]
   DPH[(Pharmacy DB)]
   DBI[(Billing DB)]
   DSU[(Report DB + Notification DB)]
  end
 end
 WEB <-->|HTTP request /api / JSON response| GW
 MOBILE <-->|HTTP request / JSON response| GW
 GW <-->|HTTP request / response| CORE & CL & LAB & PH & BI & SU
 CL <-->|[[Authorized identity lookup||Tra cứu định danh có kiểm quyền]]| CORE
 LAB <-->|[[Clinical order context via REST||Context chỉ định qua REST]]| CL
 PH <-->|[[Clinical prescription context via REST||Context kê đơn qua REST]]| CL
 CORE --- DCORE
 CL --- DCL
 LAB --- DLAB
 PH --- DPH
 BI --- DBI
 SU --- DSU
 CORE & CL & LAB & PH & BI -.->|[[Publish committed facts||Phát sự kiện đã commit]]| MQ
 MQ -.->|[[Deliver to subscribed consumers||Gửi đến consumer đã đăng ký]]| CL & LAB & PH & BI & SU
 SERVICES -.->|[[Register / discover||Đăng ký / tra cứu]]| EU
''', '[[One Server side boundary groups six business applications; it does not mean one backend process. Core and Support each compose two contexts with separate persistence authorities. Broker edges show configured producer/consumer capabilities, not all-to-all delivery. Next.js web hosting and same-origin /api rewrite are deployment details: frontend/next.config.ts still configures the rewrite and production standalone output. They are not a second business backend. Docker Compose orchestrates local infrastructure; no service ports or deferred Inpatient/Surgery are shown.||Một vùng Server side chứa sáu ứng dụng nghiệp vụ, không có nghĩa backend chỉ có một tiến trình. Core và Support mỗi ứng dụng ghép hai context nhưng giữ quyền sở hữu dữ liệu riêng. Cạnh broker mô tả khả năng producer/consumer đã cấu hình, không phải phát mọi sự kiện cho mọi service. Host web Next.js và rewrite /api cùng origin là chi tiết triển khai: frontend/next.config.ts vẫn cấu hình rewrite và standalone production. Đây không phải backend nghiệp vụ thứ hai. Docker Compose điều phối hạ tầng local; hình không ghi port hoặc bật Inpatient/Surgery.]]', 'flowchart')

figure(3, '[[Application architecture — request path and inward dependencies||Kiến trúc ứng dụng — đường request và phụ thuộc hướng vào trong]]', '''
flowchart TB
 subgraph CLIENT["CLIENT_WEB Client side"]
  WP["Web: App Router / feature UI"]
  API["Web: lib/api.ts<br/>[[JWT / result and error mapping||JWT / ánh xạ kết quả và lỗi]]"]
  MP["Flutter: presentation → domain ← data<br/>Dio ApiClient"]
  WP <-->|[[UI intent / state||Thao tác / trạng thái UI]]| API
 end
 subgraph SERVER["SERVER_ICON Server side — MediFlow backend"]
  GW["SPRING API Gateway<br/>[[Authentication / route policy||Xác thực / chính sách route]]"]
  subgraph CONTEXT["[[Inside each business service/context||Bên trong mỗi service/context nghiệp vụ]]"]
   WEB["Controller / input adapter<br/>[[DTO validation / role and actor scope||Kiểm tra DTO / role / phạm vi actor]]"]
   APP["Application service / use case<br/>[[Orchestration / local transaction||Điều phối / transaction cục bộ]]"]
   DOMAIN["Domain model<br/>[[Lifecycle / business invariants||Vòng đời / ràng buộc nghiệp vụ]]"]
   PORT["Application output ports<br/>[[Repository / peer lookup / event contracts||Hợp đồng lưu trữ / tra cứu / sự kiện]]"]
   PERSIST["Persistence adapter / JPA / Flyway"]
   REST["REST adapter<br/>[[Resilient authorized lookup||Tra cứu có kiểm quyền và phục hồi lỗi]]"]
   OUTBOX["Outbox publisher / inbox consumer<br/>[[Atomic local effects / deduplication||Hiệu ứng cục bộ atomic / chống trùng]]"]
   WEB <-->|[[Command or query / result||Lệnh hoặc truy vấn / kết quả]]| APP
   APP -->|[[Apply rules||Áp dụng quy tắc]]| DOMAIN
   APP -->|[[Use contract||Dùng hợp đồng]]| PORT
   PERSIST -.->|[[Implements||Hiện thực]]| PORT
   REST -.->|[[Implements||Hiện thực]]| PORT
   OUTBOX -.->|[[Implements||Hiện thực]]| PORT
  end
  DB[(POSTGRES [[Owned context database||Database thuộc context]])]
  PEER["[[Other service API||API service khác]]"]
  MQ["RABBIT RabbitMQ"]
  GW -->|[[Authorized request||Request đã kiểm quyền]]| WEB
  WEB -->|[[Response / error status||Response / trạng thái lỗi]]| GW
  PERSIST <-->|SQL / rows| DB
  OUTBOX <-->|[[Local outbox / inbox transaction||Transaction outbox / inbox cục bộ]]| DB
  REST <-->|HTTP request / response| PEER
  OUTBOX <-->|[[Publish / consume events||Phát / nhận sự kiện]]| MQ
  DEP["[[Static imports: infrastructure → application → domain.<br/>Adapters implement application ports.<br/>Domain has no framework or service database dependency.||Import tĩnh: infrastructure → application → domain.<br/>Adapter hiện thực port của application.<br/>Domain không phụ thuộc framework hoặc database service khác.]]"]
 end
 API & MP -->|HTTP request / JWT| GW
 GW -->|JSON response / error| API & MP
''', '[[Solid edges name runtime requests/results or rule evaluation; dotted implements edges name static interface realization, not HTTP. The import rule is explicit in the bottom note. The same pattern applies within each context; Core/Support composition does not merge the context databases. A local commit is not a distributed transaction. The diagram abstracts web hosting while retaining the configured same-origin gateway route.||Cạnh liền ghi rõ request/kết quả lúc chạy hoặc đánh giá quy tắc; cạnh chấm implements là hiện thực interface tĩnh, không phải HTTP. Chiều import được ghi rõ ở chú thích cuối. Mẫu này áp dụng trong từng context; đóng gói Core/Support không gộp database các context. Commit cục bộ không phải transaction phân tán. Hình lược chi tiết host web nhưng giữ tuyến Gateway qua /api cùng origin.]]', 'flowchart')

# Sequence bodies are added below. Branches enclose all side effects so failure
# paths never fall through into the success path.

figure(6, '[[Sign in — validation, rejected credentials and recovery||Đăng nhập — kiểm tra dữ liệu, từ chối và thử lại]]', '''
sequenceDiagram
 autonumber
 actor User as [[User||Người dùng]]
 participant UI as Web / Flutter
 participant G as Gateway Auth
 participant C as Core Organization
 User->>UI: [[Submit credentials||Gửi thông tin đăng nhập]]
 UI->>G: POST /auth/login
 alt [[Malformed or missing fields||Thiếu trường hoặc dữ liệu sai định dạng]]
  G-->>UI: [[Reject invalid request||Từ chối request không hợp lệ]]
  UI-->>User: [[Show validation errors; allow correction||Hiện lỗi dữ liệu; cho sửa lại]]
 else [[Valid request structure||Request đúng cấu trúc]]
  G->>C: [[Verify username and password||Kiểm tra tên đăng nhập và mật khẩu]]
  alt [[Invalid credentials or locked account||Sai thông tin hoặc tài khoản bị khóa]]
   C-->>G: [[Authentication rejected||Từ chối xác thực]]
   G-->>UI: 401 AUTH_INVALID_CREDENTIALS
   UI-->>User: [[Show failure; no session created||Hiện lỗi; không tạo phiên]]
  else [[Identity provider unavailable or timed out||Dịch vụ định danh lỗi hoặc quá thời gian]]
   C-->>G: [[Failure / timeout||Lỗi / timeout]]
   G-->>UI: 503 AUTH_UPSTREAM_UNAVAILABLE / 504 AUTH_UPSTREAM_TIMEOUT
   UI-->>User: [[Show temporary failure; retry later||Hiện lỗi tạm thời; thử lại sau]]
  else [[Active account and valid credentials||Tài khoản hoạt động và thông tin đúng]]
   C-->>G: [[Verified account / role / identity||Tài khoản / role / định danh đã xác minh]]
   G->>G: [[Issue access token and refresh token||Cấp access token và refresh token]]
   G-->>UI: LoginResponse
   UI-->>User: [[Open authorized workspace||Mở khu vực theo quyền]]
  end
 end
 Note over User,UI: [[After an error, a new submission restarts this sequence; no protected action runs.||Sau lỗi, gửi lại bắt đầu từ đầu sequence; không chạy tác vụ được bảo vệ.]]
''', '[[Gateway issues tokens after Core verifies the account; Core does not send a token directly to the client. Renewal is figure 04, an independent use case. Invalid request error shape follows validation handling; login success uses LoginResponse rather than an invented universal envelope.||Gateway cấp token sau khi Core xác minh tài khoản; Core không trả token trực tiếp cho client. Gia hạn là hình 04, một use case độc lập. Lỗi cấu trúc request theo xử lý validation; đăng nhập thành công dùng LoginResponse, không giả định mọi endpoint cùng một envelope.]]')

figure(4, '[[Renew session — a separate authentication use case||Gia hạn phiên — use case xác thực độc lập]]', '''
sequenceDiagram
 autonumber
 actor User as [[User||Người dùng]]
 participant UI as Web / Flutter
 participant G as Gateway Auth
 User->>UI: [[Continue authenticated work||Tiếp tục làm việc trong phiên]]
 Note over UI,G: [[Client invokes renewal explicitly where implemented; no universal automatic retry is claimed.||Client gọi gia hạn ở nơi đã hỗ trợ; không khẳng định mọi client tự động retry.]]
 UI->>G: POST /auth/refresh + refreshToken
 alt [[Missing or malformed request||Request thiếu hoặc sai cấu trúc]]
  G-->>UI: [[Validation rejection||Từ chối dữ liệu]]
  UI-->>User: [[Show session error / request sign-in||Hiện lỗi phiên / yêu cầu đăng nhập]]
 else [[Refresh request can be parsed||Request gia hạn đọc được]]
  G->>G: [[Verify signature, expiry, token type and human identity||Kiểm chữ ký, hạn, loại token và định danh người dùng]]
  alt [[Invalid or expired refresh token||Refresh token sai hoặc hết hạn]]
   G-->>UI: 401 AUTH_INVALID_REFRESH_TOKEN
   UI-->>User: [[Request sign-in again; stop protected action||Yêu cầu đăng nhập lại; dừng tác vụ được bảo vệ]]
  else [[Valid refresh identity||Định danh refresh hợp lệ]]
   G->>G: [[Issue new access token||Cấp access token mới]]
   G-->>UI: RefreshResponse(accessToken)
   UI-->>User: [[Continue with the valid access token||Tiếp tục với access token hợp lệ]]
  end
 end
''', '[[Refresh validates the signed claims locally in Gateway; it does not call Core again or rotate a refresh token in the current implementation. This diagram describes the endpoint contract, not proof of automatic client refresh.||Gateway kiểm claims đã ký tại chỗ khi refresh; implementation hiện tại không gọi lại Core hoặc cấp refresh token mới. Hình mô tả hợp đồng endpoint, không chứng nhận client tự gia hạn.]]')

figure(5, '[[Schedule appointment before arrival||Đặt lịch khám trước khi đến tiếp nhận]]', '''
sequenceDiagram
 autonumber
 actor Patient as [[Patient||Bệnh nhân]]
 actor Nurse as [[Nurse||Điều dưỡng]]
 participant UI as Web / Flutter
 participant G as Gateway
 participant C as Clinical
 participant Core as Core
 Patient->>Nurse: [[Request a consultation appointment||Đề nghị đặt lịch khám]]
 Nurse->>UI: [[Find patient and select doctor, department, date and time||Tra cứu bệnh nhân; chọn bác sĩ, khoa, ngày và giờ]]
 UI->>G: [[Authenticated appointment creation request||Request tạo lịch đã xác thực]]
 alt [[Missing identity or role denied||Thiếu xác thực hoặc sai quyền]]
  G-->>UI: 401 / 403
  UI-->>Nurse: [[Sign in / access denied; no appointment created||Đăng nhập / không có quyền; chưa tạo lịch]]
 else [[Route policy accepted||Đạt chính sách route]]
  G->>C: [[Create appointment command||Lệnh tạo lịch hẹn]]
  C->>Core: [[Authorized patient and doctor context lookup||Tra cứu context bệnh nhân và bác sĩ có kiểm quyền]]
  Core-->>C: [[Context or lookup failure||Context hoặc lỗi tra cứu]]
  alt [[Invalid data, past schedule, ineligible doctor, duplicate pending visit or failed lookup||Dữ liệu sai, lịch quá khứ, bác sĩ không hợp lệ, trùng lịch PENDING hoặc lỗi tra cứu]]
   C-->>G: [[Validation / conflict / dependency error; no appointment commit||Lỗi dữ liệu / xung đột / phụ thuộc; không commit lịch]]
   G-->>UI: [[Error response||Response lỗi]]
   UI-->>Nurse: [[Show reason; correct selection or retry after recovery||Hiện lý do; sửa lựa chọn hoặc chờ phục hồi rồi thử lại]]
  else [[All creation rules pass||Đủ điều kiện tạo lịch]]
   C->>C: [[Persist PENDING appointment and configured event publication||Lưu lịch PENDING và phát sự kiện theo cấu hình]]
   C-->>G: [[Created appointment identity||Định danh lịch đã tạo]]
   G-->>UI: [[Appointment response||Response lịch hẹn]]
   UI-->>Nurse: [[Show confirmed appointment details||Hiện thông tin lịch đã ghi nhận]]
   Nurse-->>Patient: [[Confirm appointment time and arrival instructions||Xác nhận giờ hẹn và hướng dẫn đến tiếp nhận]]
  end
 end
''', '[[PATIENT requests scheduling through the nurse; no patient self-service API is invented. New patients are registered in Core first, outside this sequence. Only a successful saved appointment is a valid input to check-in (figure 07). Scheduling itself is not proof of payment or permission to start consultation.||PATIENT nhờ điều dưỡng đặt lịch; không thêm API tự đặt lịch cho bệnh nhân. Nếu chưa có hồ sơ bệnh nhân thì tạo tại Core trước, ngoài sequence này. Chỉ lịch lưu thành công mới là đầu vào check-in ở hình 07. Đặt lịch không chứng minh đã thanh toán hoặc được bắt đầu khám.]]')

figure(7, '[[Arrival → exam payment → start consultation (V1)||Đến tiếp nhận → thu phí khám → bắt đầu khám (V1)]]', '''
sequenceDiagram
 autonumber
 actor Patient as [[Patient||Bệnh nhân]]
 actor Nurse as [[Nurse||Điều dưỡng]]
 actor Cashier as [[Cashier||Thu ngân]]
 actor Doctor as [[Doctor||Bác sĩ]]
 participant C as Clinical API
 participant Q as RabbitMQ
 participant B as Billing API
 Note over Nurse,C: [[Precondition: figure 05 saved an appointment; API calls pass through Gateway.||Tiền điều kiện: hình 05 đã lưu lịch; các lệnh API đi qua Gateway.]]
 Patient->>Nurse: [[Arrive for the saved appointment||Đến theo lịch đã đặt]]
 Nurse->>C: [[Check in the exact appointment||Check-in đúng lịch hẹn]]
 alt [[Appointment missing, wrong lifecycle or unauthorized||Không có lịch, sai trạng thái hoặc không có quyền]]
  C-->>Nurse: [[Reject; correct appointment / permission, stop this attempt||Từ chối; xử lý lịch / quyền, dừng lượt thao tác]]
 else [[Valid pending appointment||Lịch chờ hợp lệ]]
  C->>C: [[Commit AWAITING_PAYMENT appointment + outbox||Commit lịch AWAITING_PAYMENT + outbox]]
  C-->>Nurse: [[Check-in accepted; examination not yet authorized||Tiếp nhận thành công; chưa được bắt đầu khám]]
  C-)Q: [[Publish exact appointment fact after commit||Phát fact đúng lịch sau commit]]
  Q-)B: [[Deliver fact for EXAM charge issuance||Gửi fact để lập khoản thu EXAM]]
  B->>B: [[Idempotently create charge / payment request||Tạo khoản thu / yêu cầu thu chống trùng]]
  Cashier->>B: [[Find exact EXAM payment request||Tra cứu yêu cầu thu EXAM đúng lượt khám]]
  alt [[Request not ready or issuing failed||Yêu cầu thu chưa sẵn sàng hoặc lập phí lỗi]]
   B-->>Cashier: [[Not ready / error; do not record an arbitrary payment||Chưa sẵn sàng / lỗi; không thu tiền vào yêu cầu tùy ý]]
   Note over Cashier,Doctor: [[Wait for source recovery, then retry request lookup; no examination in this branch.||Chờ phục hồi nguồn rồi tra cứu lại; nhánh này chưa khám.]]
  else [[Exact request available||Yêu cầu thu đúng đã sẵn sàng]]
   Cashier->>B: [[Record collected payment with idempotency key||Ghi tiền đã thu với khóa idempotency]]
   alt [[Invalid payment or conflicting retry||Thanh toán không hợp lệ hoặc retry khác intent]]
    B-->>Cashier: [[Reject; no new payment / allocation / clearance||Từ chối; không tạo tiền / phân bổ / quyền mới]]
   else [[Payment accepted or exact committed replay||Nhận thanh toán hoặc phát lại kết quả đã commit]]
    B->>B: [[Atomic payment + allocation + clearance + outbox||Atomic payment + allocation + clearance + outbox]]
    B-->>Cashier: [[Payment receipt||Biên nhận thanh toán]]
    Cashier-->>Patient: [[Confirm paid consultation fee||Xác nhận đã thu phí khám]]
    B-)Q: financial.clearance.granted
    Q-)C: [[Store exact EXAM clearance idempotently||Lưu EXAM clearance đúng lịch và chống trùng]]
    Doctor->>C: [[Start consultation for the exact appointment||Bắt đầu khám đúng lịch hẹn]]
    alt [[Clearance pending, expired, mismatched or actor/state invalid||Quyền chưa tới, hết hạn, lệch đối tượng hoặc sai actor/trạng thái]]
     C-->>Doctor: [[Reject start; remain outside consultation||Từ chối bắt đầu; chưa vào trạng thái khám]]
     Note over Doctor,C: [[Retry after valid evidence arrives; no second fee collection for delayed delivery.||Thử lại khi có bằng chứng hợp lệ; không thu phí lần hai vì chậm sự kiện.]]
    else [[Assigned doctor and valid exact clearance||Đúng bác sĩ và quyền đúng lượt còn hạn]]
     C->>C: [[Atomically start examination and create/reuse its record||Atomic bắt đầu khám và tạo/dùng lại hồ sơ đúng lịch]]
     C-->>Doctor: [[Open consultation record||Mở hồ sơ khám]]
     Doctor-->>Patient: [[Begin consultation||Bắt đầu thăm khám]]
    end
   end
  end
 end
''', '[[Normal V1 outpatient path. Check-in precedes fee collection, and fee authorization precedes examination. Audited emergency override remains a separately authorized source capability, not a fallback for ordinary unpaid visits. Actors use their own sessions; API lifelines abbreviate Gateway transport only. Event arrows are asynchronous and duplicate delivery does not create a second fee.||Luồng ngoại trú V1 thông thường. Check-in trước thu phí; có quyền từ phí khám trước khi bắt đầu khám. Audited emergency override là khả năng riêng có kiểm quyền trong source, không phải đường vòng cho lượt khám chưa trả phí bình thường. Mỗi actor dùng phiên riêng; lifeline API chỉ lược đường truyền Gateway. Mũi tên sự kiện là bất đồng bộ; phát lại không tạo khoản thu thứ hai.]]')

figure(8, '[[Lab order → payment → test → doctor reads results (V1)||Chỉ định → thanh toán → xét nghiệm → bác sĩ đọc kết quả (V1)]]', '''
sequenceDiagram
 autonumber
 actor Doctor as [[Doctor||Bác sĩ]]
 actor Cashier as [[Cashier||Thu ngân]]
 actor Tech as [[Lab technician||Kỹ thuật viên xét nghiệm]]
 participant C as Clinical API
 participant L as Lab API
 participant B as Billing API
 participant Q as RabbitMQ
 Note over Doctor,C: [[Precondition: authorized open consultation record; all actor API calls use Gateway.||Tiền điều kiện: hồ sơ khám đang mở và đúng quyền; mọi lệnh actor qua Gateway.]]
 Doctor->>C: [[Save explicit lab indication||Lưu chỉ định xét nghiệm cụ thể]]
 alt [[Invalid or unauthorized indication||Chỉ định sai hoặc không có quyền]]
  C-->>Doctor: [[Reject; correct indication, no test created||Từ chối; sửa chỉ định, chưa tạo xét nghiệm]]
 else [[Indication saved||Chỉ định đã lưu]]
  C-->>Doctor: source_order_id
  Doctor->>L: [[Create test with exact source_order_id||Tạo xét nghiệm với source_order_id đúng]]
  L->>C: [[Lookup persisted order and actor context||Tra cứu chỉ định đã lưu và context actor]]
  C-->>L: [[Order context or lookup failure||Context chỉ định hoặc lỗi tra cứu]]
  alt [[Context mismatch, duplicate intent conflict or dependency failure||Lệch context, trùng khác intent hoặc lỗi phụ thuộc]]
   L-->>Doctor: [[Reject creation; no new test or charge fact||Từ chối tạo; không có xét nghiệm mới hoặc fact tính phí]]
  else [[Context accepted / exact replay resolved||Context hợp lệ / nhận diện retry cùng ý định]]
   L->>L: [[Save or reuse exact test; outbox only for new creation||Lưu hoặc dùng lại xét nghiệm đúng; chỉ tạo outbox khi mới]]
   L-->>Doctor: testId
   L-)Q: lab.request.created
   Q-)B: [[Issue exact LAB_TEST charge / request||Lập khoản thu / yêu cầu thu LAB_TEST đúng xét nghiệm]]
   ref over Cashier,B: [[Find ready request and record payment, or stop/retry as figure 07||Tra yêu cầu đã sẵn sàng và ghi tiền, hoặc dừng/thử lại như hình 07]]
   Cashier->>B: [[Lookup the exact request, then submit its payment as in figure 07||Tra đúng yêu cầu, rồi gửi thanh toán như hình 07]]
   B-->>Cashier: [[Committed receipt or request/payment error||Biên nhận đã commit hoặc lỗi yêu cầu/thanh toán]]
   alt [[Payment not committed or request still unavailable||Chưa commit thanh toán hoặc yêu cầu thu chưa sẵn sàng]]
    Note over Cashier,Tech: [[Stop; resolve payment/request before requesting the test.||Dừng; xử lý thanh toán/yêu cầu thu trước khi xét nghiệm.]]
   else [[Exact payment committed||Đã commit thanh toán đúng xét nghiệm]]
   B-)Q: financial.clearance.granted
   Q-)L: [[Store exact LAB_TEST clearance||Lưu quyền LAB_TEST đúng testId]]
   Tech->>L: [[Start exact test||Bắt đầu đúng xét nghiệm]]
   alt [[Payment authority missing/expired or invalid lifecycle||Thiếu/hết hạn quyền thanh toán hoặc sai trạng thái]]
    L-->>Tech: [[Reject; do not perform protected test||Từ chối; chưa thực hiện xét nghiệm được bảo vệ]]
   else [[Start permitted||Được phép bắt đầu]]
    L-->>Tech: IN_PROGRESS
    Tech->>Tech: [[Perform the laboratory test||Thực hiện xét nghiệm]]
    loop [[Submit corrected results until accepted or work is stopped||Sửa và gửi kết quả đến khi được nhận hoặc dừng thao tác]]
     Tech->>L: [[Submit values, conclusion and performed date||Gửi chỉ số, kết luận và ngày thực hiện]]
     alt [[Empty results, invalid date, expired authority or finalized test||Kết quả rỗng, ngày sai, quyền hết hạn hoặc xét nghiệm đã chốt]]
      L-->>Tech: [[Reject with reason; no new completed result||Từ chối có lý do; không tạo kết quả hoàn tất mới]]
     else [[Result and current authority valid||Kết quả và quyền hiện tại hợp lệ]]
      L->>L: [[Commit COMPLETED, resultVersion and outbox once||Commit COMPLETED, resultVersion và outbox một lần]]
      L-->>Tech: [[Result accepted||Đã ghi nhận kết quả]]
     end
    end
    opt [[A completed result was committed||Đã commit kết quả hoàn tất]]
     L-)Q: lab.result.created
     Q-)C: [[Attach result idempotently through inbox||Gắn kết quả chống trùng qua inbox]]
     Doctor->>L: [[Read authorized completed result||Đọc kết quả hoàn tất đúng quyền]]
     L-->>Doctor: [[Test values / conclusion / result version||Chỉ số / kết luận / phiên bản kết quả]]
    end
   end
   end
  end
 end
''', '[[The payment reference imports the complete success/failure payment subflow, not an unconditional successful payment. Starting and entering results both enforce current authority. The technician records results; the doctor interprets them in figure 37. Support projections do not block this path and are omitted. A failed result cannot produce the completion event.||Khối ref thanh toán bao gồm cả nhánh thành công/thất bại, không mặc định đã thu tiền. Khi bắt đầu và khi ghi kết quả đều kiểm quyền hiện tại. Kỹ thuật viên ghi kết quả; bác sĩ diễn giải ở hình 37. Projection Support không chặn luồng này nên được lược. Ghi kết quả lỗi không được phát sự kiện hoàn tất.]]')

figure(37, '[[Doctor explains consultation outcome to patient||Bác sĩ kết luận và trả kết quả khám cho bệnh nhân]]', '''
sequenceDiagram
 autonumber
 actor Doctor as [[Doctor||Bác sĩ]]
 actor Patient as [[Patient||Bệnh nhân]]
 participant C as Clinical API
 participant L as Lab API
 Note over Doctor,C: [[Precondition: examination started in figure 07; actor API calls pass through Gateway.||Tiền điều kiện: đã bắt đầu khám ở hình 07; lệnh API actor qua Gateway.]]
 opt [[A laboratory test was indicated||Có chỉ định xét nghiệm]]
  ref over Doctor,L: [[Figure 08: completed result available, or stop and await result||Hình 08: có kết quả hoàn tất, hoặc dừng để chờ kết quả]]
 end
 Doctor->>C: [[Read current record and available clinical evidence||Đọc hồ sơ hiện tại và bằng chứng khám sẵn có]]
 C-->>Doctor: [[Record / diagnoses / attached results, or access error||Hồ sơ / chẩn đoán / kết quả đã gắn, hoặc lỗi truy cập]]
 alt [[Wrong actor, unavailable record or required evidence missing||Sai actor, hồ sơ không sẵn sàng hoặc thiếu bằng chứng cần thiết]]
  Doctor->>Doctor: [[Stop conclusion; resolve access or await evidence||Dừng kết luận; xử lý quyền hoặc chờ bằng chứng]]
 else [[Evidence sufficient for clinical conclusion||Đủ bằng chứng để kết luận khám]]
  Doctor->>C: [[Record findings and diagnosis||Ghi nội dung khám và chẩn đoán]]
  alt [[Data invalid or record no longer writable||Dữ liệu sai hoặc hồ sơ không còn cho ghi]]
   C-->>Doctor: [[Reject; correct or reload, do not confirm completion||Từ chối; sửa hoặc tải lại, chưa xác nhận hoàn tất]]
  else [[Diagnosis saved||Đã lưu chẩn đoán]]
   C-->>Doctor: [[Saved clinical information||Thông tin khám đã lưu]]
   Doctor-->>Patient: [[Explain findings, diagnosis and treatment plan||Giải thích kết quả, chẩn đoán và hướng điều trị]]
   Note over Doctor,Patient: [[Human consultation; no patient result portal or delivery receipt is invented.||Trao đổi trực tiếp; không giả định cổng xem kết quả hoặc biên nhận điện tử cho Patient.]]
   opt [[Medication is indicated||Có chỉ định dùng thuốc]]
    ref over Doctor,C: [[Figure 38: separate prescription workflow||Hình 38: quy trình kê đơn riêng]]
   end
   Doctor->>C: [[Complete visit with outpatient disposition||Hoàn tất lượt khám với hướng xử trí ngoại trú]]
   alt [[Required diagnosis/disposition or lifecycle guard fails||Thiếu chẩn đoán/hướng xử trí hoặc sai trạng thái]]
    C-->>Doctor: [[Reject completion; resolve and retry||Từ chối hoàn tất; xử lý rồi gửi lại]]
   else [[Completion rules pass||Đủ điều kiện hoàn tất]]
    C->>C: [[Commit completion and configured outbox||Commit hoàn tất và outbox theo cấu hình]]
    C-->>Doctor: [[Visit completion confirmed||Xác nhận lượt khám hoàn tất]]
    Doctor-->>Patient: [[Confirm next steps / outpatient follow-up||Xác nhận bước tiếp theo / hướng dẫn ngoại trú]]
   end
  end
 end
''', '[[The actor reads Lab results through its authorized API; asynchronous Clinical attachment may arrive later. This figure models professional explanation to the patient, not a new software result-delivery endpoint. Medication fulfilment is a separate Pharmacy/Billing process and is not proof of consultation-result delivery. The clinical completion command uses source lifecycle rules; it is not made dependent on a fabricated dispense condition.||Bác sĩ đọc kết quả Lab qua API có quyền; kết quả gắn bất đồng bộ vào Clinical có thể đến sau. Hình mô tả việc giải thích chuyên môn cho bệnh nhân, không tạo endpoint trả kết quả mới. Cấp thuốc thuộc quy trình Pharmacy/Billing riêng, không phải bằng chứng đã trả kết quả khám. Lệnh hoàn tất Clinical theo lifecycle trong source; không thêm điều kiện giả rằng phải cấp thuốc mới được kết thúc khám.]]')

figure(38, '[[Create prescription — an optional separate workflow||Kê đơn — quy trình riêng khi có chỉ định]]', '''
sequenceDiagram
 autonumber
 actor Doctor as [[Doctor||Bác sĩ]]
 participant P as Pharmacy API
 participant C as Clinical API
 participant Q as RabbitMQ
 participant B as Billing
 Note over Doctor,P: [[Precondition: authorized outpatient context while record is writable; API calls use Gateway.||Tiền điều kiện: context ngoại trú đúng quyền, hồ sơ còn cho ghi; lệnh API qua Gateway.]]
 Doctor->>P: [[Submit medicines, quantities and dosage with exact clinical identity||Gửi thuốc, số lượng, liều dùng và định danh khám chính xác]]
 P->>C: [[Validate persisted clinical context and doctor authority||Xác minh context khám đã lưu và quyền bác sĩ]]
 C-->>P: [[Context or lookup rejection||Context hoặc từ chối tra cứu]]
 alt [[Context invalid, unauthorized or unavailable||Context sai, không có quyền hoặc không khả dụng]]
  P-->>Doctor: [[Reject; no prescription or reservation||Từ chối; chưa có đơn hoặc giữ chỗ]]
 else [[Clinical context accepted||Context khám hợp lệ]]
  P->>P: [[Lock drug rows; read catalogue prices; check quantity, expiry and available stock||Khóa dòng thuốc; lấy giá danh mục; kiểm số lượng, hạn và tồn khả dụng]]
  alt [[Invalid medicine, quantity, expiry or insufficient available stock||Thuốc, số lượng, hạn sai hoặc thiếu tồn khả dụng]]
   P-->>Doctor: [[Reject; correct prescription, no partial reservation||Từ chối; sửa đơn, không giữ chỗ một phần]]
  else [[All lines valid||Mọi dòng thuốc hợp lệ]]
   P->>P: [[Atomic prescription + lines + pending slip + reservations + outbox||Atomic đơn + dòng thuốc + phiếu chờ + giữ chỗ + outbox]]
   P-->>Doctor: [[Prescription identity and server-calculated total||Định danh đơn và tổng tiền do server tính]]
   P-)Q: prescription.created
   Q-)B: [[Issue exact prescription charge / request once||Lập khoản thu / yêu cầu thu đúng đơn một lần]]
  end
 end
 Note over P,B: [[Reservation is not stock issue; figure 09 covers payment and explicit dispensing.||Giữ chỗ chưa phải xuất kho; hình 09 mô tả thu tiền và chủ động cấp thuốc.]]
''', '[[Prices come from server catalogue snapshots, not client-supplied amounts. Source context is validated rather than inferred from a diagnosis string or arbitrary UUID. Retry semantics follow the actual endpoint; this diagram does not invent a universal prescription idempotency key.||Giá lấy từ snapshot danh mục server, không nhận giá do client tự gửi. Context nguồn được xác minh, không suy ra từ chuỗi chẩn đoán hoặc UUID tùy ý. Retry theo hợp đồng endpoint thật; hình không thêm khóa idempotency kê đơn áp dụng chung khi source chưa có.]]')

figure(9, '[[Prescription payment and explicit pharmacist dispensing (V1)||Thanh toán đơn và dược sĩ chủ động cấp thuốc (V1)]]', '''
sequenceDiagram
 autonumber
 actor Patient as [[Patient||Bệnh nhân]]
 actor Cashier as [[Cashier||Thu ngân]]
 actor Pharmacist as [[Pharmacist||Dược sĩ]]
 participant B as Billing API
 participant Q as RabbitMQ
 participant P as Pharmacy API
 Note over Cashier,P: [[Precondition: figure 38 saved a V1 outpatient prescription; actor API calls use Gateway.||Tiền điều kiện: hình 38 đã lưu đơn ngoại trú V1; lệnh API actor qua Gateway.]]
 ref over Cashier,B: [[Find exact ready PRESCRIPTION request and record payment, or stop/retry as figure 07||Tra yêu cầu PRESCRIPTION đúng đơn đã sẵn sàng và ghi tiền, hoặc dừng/thử lại như hình 07]]
 Cashier->>B: [[Lookup the exact request, then submit its payment as in figure 07||Tra đúng yêu cầu, rồi gửi thanh toán như hình 07]]
 B-->>Cashier: [[Committed receipt or request/payment error||Biên nhận đã commit hoặc lỗi yêu cầu/thanh toán]]
 alt [[Payment failed, request pending or outcome unresolved||Thanh toán lỗi, yêu cầu thu đang chờ hoặc chưa rõ kết quả]]
  B-->>Cashier: [[Resolve payment / retry exact request; no dispensing||Xử lý thanh toán / thử lại đúng yêu cầu; chưa cấp thuốc]]
 else [[Exact prescription payment committed||Đã commit tiền đúng đơn]]
 B-)Q: financial.clearance.granted
 Q-)P: [[Store clearance for exact prescription||Lưu quyền đúng đơn thuốc]]
 Note over Q,P: [[Clearance delivery never automatically dispenses in V1.||Nhận clearance không tự cấp thuốc trong V1.]]
 Patient->>Pharmacist: [[Present prescription and request medicines||Đưa đơn và đề nghị nhận thuốc]]
 Pharmacist->>P: [[Read prescription and issue dispense command||Tra cứu đơn và gửi lệnh cấp thuốc]]
 P->>P: [[Resolve signed actor; lock prescription, slip, reservations and drug rows||Xác định actor đã ký; khóa đơn, phiếu, giữ chỗ và thuốc]]
 alt [[Exact committed command replay||Lặp đúng lệnh đã commit]]
  P-->>Pharmacist: [[Return committed dispense outcome; no second stock issue||Trả kết quả cấp thuốc đã commit; không xuất kho lần hai]]
 else [[Missing/expired/mismatched clearance or unauthorized actor||Thiếu/hết hạn/lệch clearance hoặc actor sai quyền]]
  P-->>Pharmacist: [[Reject; resolve authority then retry, no stock change||Từ chối; xử lý quyền rồi thử lại, chưa đổi tồn]]
 else [[Reservation expired, invalid lifecycle or stock failure||Giữ chỗ hết hạn, sai vòng đời hoặc lỗi tồn]]
  P-->>Pharmacist: [[Reject dispense; report failure for supported adjustment workflow||Từ chối cấp; chuyển lỗi sang luồng điều chỉnh đã hỗ trợ]]
  Note over Cashier,Pharmacist: [[No partial stock issue; refund is a separate authorized Billing operation, not assumed automatic cash return.||Không xuất kho một phần; hoàn tiền là thao tác Billing riêng có quyền, không mặc định đã trả tiền tự động.]]
 else [[Current authorization and every stock rule pass||Quyền hiện tại và mọi điều kiện tồn đều đạt]]
  P->>P: [[Atomic stock decrement + fulfilled prescription/slip + outbox||Atomic trừ tồn + hoàn tất đơn/phiếu + outbox]]
  P-->>Pharmacist: [[Dispense confirmation||Xác nhận cấp thuốc]]
  P-)Q: prescription.filled
  Q-)B: [[Apply subscribed financial lifecycle effect idempotently||Áp dụng hiệu ứng tài chính đã đăng ký và chống trùng]]
  Pharmacist-->>Patient: [[Hand over medicines and usage instructions||Giao thuốc và hướng dẫn sử dụng]]
 end
 end
''', '[[The V0 automatic payment.completed saga is a legacy compatibility mechanism, not the main V1 journey. Pharmacist command, current target clearance, lifecycle and locked stock evidence are mandatory. Physical handover is shown as a human action after persisted confirmation; the source does not prove receipt by the patient.||Saga V0 tự động từ payment.completed là cơ chế compatibility cũ, không phải luồng V1 chính. Phải có lệnh dược sĩ, clearance đúng đích còn hạn, lifecycle và tồn đã khóa hợp lệ. Giao thuốc thực tế là hành động người dùng sau xác nhận lưu; source không chứng minh bệnh nhân đã nhận.]]')

figure(10, '[[Clinical service — domain classes||Clinical service — các lớp nghiệp vụ]]', '''
classDiagram
 namespace Clinical {
  class Appointment {
   UUID appointmentId
   UUID patientId
   UUID doctorId
   UUID departmentId
   AppointmentStatus status
   checkIn(priceCode, at)
   startExam(clearanceId, overrideId, at)
   complete(at)
  }
  class MedicalRecord {
   UUID recordId
   UUID appointmentId
   MedicalRecordStatus status
   RecordDisposition disposition
  }
  class Diagnosis {
   UUID diagnosisId
   String diagnosisName
   String icdCode
  }
  class ClinicalLabOrder {
   UUID sourceOrderId
   UUID recordId
   UUID patientId
  }
  class ExamClearance {
   UUID clearanceId
   UUID appointmentId
   Instant expiresAt
  }
 }
 Appointment "0..1" --> "0..1" MedicalRecord : appointmentId
 MedicalRecord "1" *-- "0..*" Diagnosis
 ClinicalLabOrder ..> MedicalRecord : recordId
 ExamClearance ..> Appointment : appointmentId
''', '[[All classes in this figure belong to Clinical. LabTest/LabResult move to figure 40. Dashed UUID references do not imply JPA navigation. Appointment-backed records are distinct from preserved legacy records without an appointment. Selected attributes/methods are copied from source; no generated getters are listed.||Tất cả lớp trong hình thuộc Clinical. LabTest/LabResult chuyển sang hình 40. Tham chiếu UUID nét đứt không hàm ý điều hướng JPA. Hồ sơ theo lịch hẹn khác với hồ sơ legacy còn giữ không có lịch. Thuộc tính/method chọn lọc từ source; không liệt kê getter sinh tự động.]]', 'class')

figure(11, '[[Pharmacy and Billing — separate services, explicit contracts||Pharmacy và Billing — hai service riêng, hợp đồng rõ ràng]]', '''
classDiagram
 namespace Pharmacy {
  class Drug {
   UUID drugId
   BigDecimal price
   int stockQuantity
   LocalDate expiryDate
  }
  class Prescription {
   UUID prescriptionId
   BigDecimal totalAmount
   PrescriptionStatus status
  }
  class PrescriptionLine {
   UUID drugId
   int quantity
   BigDecimal unitPrice
  }
  class StockReservation {
   UUID prescriptionId
   UUID drugId
   int quantity
   Instant expiresAt
  }
  class DispenseSlip {
   UUID dispenseId
   UUID prescriptionId
  }
 }
 namespace Billing {
  class BillingAccount {
   UUID accountId
  }
  class Charge {
   UUID chargeId
   UUID accountId
   BigDecimal grossAmount
  }
  class PaymentRequest {
   UUID paymentRequestId
   UUID accountId
  }
  class PaymentTransaction {
   UUID transactionId
   UUID accountId
   BigDecimal amount
  }
  class PaymentAllocation {
   UUID transactionId
   UUID chargeId
   BigDecimal amount
  }
  class FinancialClearance {
   UUID clearanceId
   UUID prescriptionId
   Instant expiresAt
  }
 }
 Prescription "1" *-- "1..*" PrescriptionLine
 PrescriptionLine "*" --> "1" Drug : drugId
 Prescription "1" --> "1..*" StockReservation : prescriptionId
 Prescription "1" --> "1" DispenseSlip : prescriptionId
 Charge ..> BillingAccount : accountId
 PaymentRequest ..> BillingAccount : accountId
 PaymentTransaction ..> BillingAccount : accountId
 PaymentAllocation ..> PaymentTransaction : transactionId
 PaymentAllocation ..> Charge : chargeId
 Charge ..> Prescription : source identity via prescription.created
 FinancialClearance ..> Prescription : prescriptionId via clearance fact
''', '[[Namespaces are two independent service boundaries, each with its own database. Cross-boundary dependencies denote event/UUID contracts, never an entity relationship or shared transaction. The V1 class view intentionally uses ledger models; legacy Invoice remains in the database dictionary and compatibility source. StockReservation/DispenseSlip are created with a prescription in the represented successful creation workflow.||Namespace là ranh giới hai service độc lập, mỗi service có database riêng. Phụ thuộc xuyên vùng chỉ hợp đồng sự kiện/UUID, không phải quan hệ entity hoặc transaction chung. Hình V1 dùng mô hình ledger; Invoice legacy vẫn được mô tả trong từ điển database và source compatibility. StockReservation/DispenseSlip được tạo cùng đơn trong luồng tạo thành công đang mô tả.]]', 'class')

figure(39, '[[Core service — Organization and Patient modules||Core service — module Organization và Patient]]', '''
classDiagram
 namespace Core_Organization {
  class Account {
   UUID accountId
   String username
   Role role
  }
  class Staff {
   UUID staffId
   UUID departmentId
  }
  class Department {
   UUID departmentId
   String departmentName
  }
 }
 namespace Core_Patient {
  class Patient {
   UUID patientId
   String fullName
   LocalDate dateOfBirth
  }
 }
 Staff ..> Department : departmentId
 Account ..> Staff : optional staffId
 Account ..> Patient : optional patientId identity mapping
''', '[[Both modules run in Core but retain separate Organization/Patient stores. Identity references are optional and depend on account role; the drawing does not create a cross-database foreign key. Infrastructure classes are excluded from this domain overview.||Hai module chạy trong Core nhưng giữ store Organization/Patient riêng. Tham chiếu định danh là tùy chọn theo role tài khoản; hình không tạo khóa ngoại xuyên database. Hình domain tổng quan không trộn các lớp infrastructure.]]', 'class')

figure(40, '[[Lab service — tests, results and financial authority||Lab service — xét nghiệm, kết quả và quyền dịch vụ]]', '''
classDiagram
 namespace Lab {
  class LabTest {
   UUID testId
   UUID recordId
   UUID sourceOrderId
   LabTestStatus status
   int resultVersion
   start(clearance, override, at)
   recordResults(results, conclusion, performedDate)
  }
  class LabResult {
   UUID resultId
   String indicator
   String value
   String unit
  }
  class LabFinancialClearance {
   UUID clearanceId
   UUID testId
   Instant expiresAt
  }
 }
 LabTest "1" *-- "0..*" LabResult
 LabTest ..> LabFinancialClearance : validate exact LAB_TEST authority
''', '[[Clinical record and indication are external UUID references, not Clinical objects inside Lab. Lab stores its own received clearance evidence; Billing remains the authority for financial effects. Results are empty before completion and must be nonempty when completed.||Hồ sơ/chỉ định Clinical chỉ được tham chiếu bằng UUID bên ngoài, không đưa object Clinical vào Lab. Lab lưu bằng chứng clearance nhận được; Billing vẫn có quyền sở hữu hiệu ứng tiền. Trước hoàn tất có thể chưa có kết quả; khi hoàn tất phải có kết quả không rỗng.]]', 'class')

figure(41, '[[Support service — separate reporting and notification models||Support service — mô hình báo cáo và thông báo riêng]]', '''
classDiagram
 namespace Support_Report {
  class DailyVisitReport {
   UUID reportId
   LocalDate reportDate
   UUID departmentId
  }
  class MonthlyRevenueReport {
   UUID reportId
   int month
   int year
  }
  class DrugStatistic {
   UUID statisticId
   UUID drugId
  }
 }
 namespace Support_Notification {
  class Notification {
   UUID notificationId
   UUID patientId
   String content
  }
 }
''', '[[Report and Notification are composed in Support but have separate databases and transaction managers. There is no invented domain association between independent projections. Domain events update these models asynchronously; they are not required participants in the consultation/result/dispense request path.||Report và Notification ghép trong Support nhưng có database và transaction manager riêng. Không tạo quan hệ domain giả giữa các projection độc lập. Sự kiện nghiệp vụ cập nhật các mô hình bất đồng bộ; chúng không bắt buộc tham gia đường request khám/trả kết quả/cấp thuốc.]]', 'class')


def source(number, lang, orientation):
    item = FIGURES[number]
    seed = (ROOT/'assets/mermaid-config.json').read_text(encoding='utf8')
    config = json.loads(seed)
    # Preserve print palette from the established per-source configuration.
    original = (ROOT/'assets/mermaid/02-en.mmd').read_text(encoding='utf8').splitlines()[0]
    config.update(json.loads(original[len('%%{init: '):-len('}%%')]))
    config = copy.deepcopy(config)
    config.setdefault('flowchart', {}).update(nodeSpacing=24 if orientation=='portrait' else 40,
                                              rankSpacing=46, htmlLabels=True, curve='linear',
                                              subGraphTitleMargin={'top':12,'bottom':45})
    config.setdefault('sequence', {}).update(wrap=True, actorMargin=20 if orientation=='portrait' else 55,
        width=125 if orientation=='portrait' else 175, messageMargin=30, mirrorActors=False)
    config.setdefault('class', {}).update(nodeSpacing=30, rankSpacing=55)
    # Mermaid treats semicolons as statement separators even inside message labels.
    body = item['body'].replace(';', ',').replace('ref over ', 'Note over ') if item['kind']=='sequence' else item['body']
    if item['kind']=='flowchart':
        body = body.replace('flowchart TB', 'flowchart '+('TB' if orientation=='portrait' else 'LR'),1)
    elif item['kind']=='class':
        body = body.replace('classDiagram', 'classDiagram\n direction '+('TB' if orientation=='portrait' else 'LR'),1)
    replacements = {'CLIENT_WEB':pictogram('WEB'), 'CLIENT_MOBILE':pictogram('MOB'),
        'SERVER_ICON':pictogram('CORE'), 'NEXT':icon('nextjs')+' '+icon('react'),
        'FLUTTER':icon('flutter')+' '+icon('dart'), 'SPRING':icon('spring'),
        'RABBIT':icon('rabbitmq'), 'POSTGRES':icon('postgresql')}
    for key, value in replacements.items():
        body=body.replace(key,value)
    return '%%{init: '+json.dumps(config,ensure_ascii=False)+'}%%\n'+localize(body,lang)+'\n'


def section(number, lang):
    item=FIGURES[number];stem=f'{number:02}-{lang}'
    title=localize(item['title'],lang)
    return '\n'.join(['### '+title,'',f'![{title}](assets/svg/{stem}.svg)','',
        '<details>',f'<summary>Mermaid · {stem}</summary>','','```mermaid',
        source(number,lang,'landscape').rstrip(),'```','','</details>','',localize(item['note'],lang),''])


def polish_report(text, lang):
    text=re.sub(r'\n<!-- word-sequence-excerpts:start -->[\s\S]*?<!-- word-sequence-excerpts:end -->\n?', '\n', text)
    excerpt=localize('[[Word panels preserve the exact conditions enclosing each message. If a panel shows only one alternative branch, it uses `opt` with that branch condition to mark a guarded excerpt; read the complete figure for the full `alt` and other outcomes.||Các phần Word giữ nguyên điều kiện bao quanh từng thông điệp. Nếu chỉ trích một nhánh alternative, phần đó dùng `opt` với điều kiện của chính nhánh để biểu diễn trích đoạn có điều kiện; xem hình đầy đủ để đọc toàn bộ `alt` và các kết quả khác.]]',lang)
    text=text.replace('### 1.3. Sequences Diagrams\n', '### 1.3. Sequences Diagrams\n\n<!-- word-sequence-excerpts:start -->\n'+excerpt+'\n<!-- word-sequence-excerpts:end -->\n',1)
    text=text.replace('HEAD `434c609be5ad` + observed working tree', 'source commit `26232b54813d` + this documentation revision')
    text=text.replace('HEAD `434c609be5ad` + working tree hiện có', 'source commit `26232b54813d` + bản sửa tài liệu này')
    text=text.replace('Khối ref thanh toán', 'Chú thích tham chiếu thanh toán')
    text=text.replace('Existing working-tree changes are preserved and this task does not commit/push them.', 'This revision changes documentation and assets only; production behavior and activation flags are unchanged.')
    text=text.replace('File source đang sửa trong working tree được giữ nguyên và không commit/push trong tác vụ này.', 'Bản sửa này chỉ cập nhật tài liệu và assets; không đổi hành vi production hoặc cờ kích hoạt.')
    text=text.replace('Các thay đổi đang có trong working tree được giữ nguyên; tác vụ này không commit/push chúng.', 'Bản sửa này chỉ cập nhật tài liệu và assets; không đổi hành vi production hoặc cờ kích hoạt.')
    text=re.sub(r'\n<!-- supervisor-evidence:start -->[\s\S]*?<!-- supervisor-evidence:end -->\n?', '\n', text)
    links=[
        ('Sign-in / refresh','Đăng nhập / gia hạn','backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java'),
        ('Check-in / fee authority / exam start','Check-in / quyền từ phí khám / bắt đầu khám','backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/ClinicalCareFinanceApplicationService.java'),
        ('Persisted laboratory indication','Chỉ định xét nghiệm đã lưu','backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/ClinicalLabOrder.java'),
        ('Lab authorization / result commands','Kiểm quyền và lệnh ghi kết quả Lab','backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java'),
        ('Explicit V1 dispense transaction','Transaction chủ động cấp thuốc V1','backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/CareDispenseTransactionService.java'),
        ('Web hosting / gateway rewrite','Host web / rewrite đến Gateway','frontend/next.config.ts')]
    note=localize('[[Supervisor revision: figures 04/05 replace the former overviews with independent refresh/booking sequences. Use-case overviews are replaced by the eight role diagrams 29–36. Figures 37–41 add outcome, prescribing and service-grouped class models. Readability panels are ordered extracts of a complete figure: they retain guards, and message numbering continues across sequence panels.||Bản sửa theo góp ý: hình 04/05 thay tổng quan cũ bằng sequence gia hạn/đặt lịch riêng. Tổng quan use case được thay bằng tám hình theo role 29–36. Hình 37–41 bổ sung trả kết quả, kê đơn và class nhóm theo service. Các phần cho Word là trích đoạn theo thứ tự của hình đầy đủ: giữ điều kiện và tiếp tục đánh số thông điệp giữa các phần sequence.]]',lang)
    heading='Additional source evidence' if lang=='en' else 'Nguồn đối chiếu bổ sung'
    table=['| '+('Concern | Source' if lang=='en' else 'Nội dung | Source')+' |','|---|---|']
    table += ['| '+(en if lang=='en' else vi)+' | ['+path.rsplit('/',1)[-1]+'](../../../../'+path+') |' for en,vi,path in links]
    result=text.rstrip()+'\n\n<!-- supervisor-evidence:start -->\n### '+heading+'\n\n'+note+'\n\n'+'\n'.join(table)+'\n<!-- supervisor-evidence:end -->\n'
    return re.sub(r'\n{3,}', '\n\n', result)


def main(numbers=None):
    changed=[]
    selected={n:v for n,v in FIGURES.items() if numbers is None or n in numbers}
    for number,item in selected.items():
        for lang in ['en','vi']:
            stem=f'{number:02}-{lang}'
            (ROOT/'assets/mermaid'/f'{stem}.mmd').write_text(source(number,lang,'landscape'),encoding='utf8')
            for orient in ['portrait','landscape']:
                (OUT/orient/'mermaid'/f'{stem}.mmd').write_text(source(number,lang,orient),encoding='utf8')
            changed.append(stem+'.mmd')
            # Old sequence/class/architecture panels must not survive as stale choices.
            for old_panel in (OUT/'panels/mermaid').glob(stem+'-part-*.mmd'):
                for folder,ext in [('mermaid','mmd'),('svg','svg'),('png','png')]:
                    (OUT/'panels'/folder/(old_panel.stem+'.'+ext)).unlink(missing_ok=True)
    for lang,name in [('en','01-review-2-standard-en.md'),('vi','02-review-2-standard-vi.md')]:
        p=ROOT/name;text=p.read_text(encoding='utf8')
        a=text.index('### 1.1.');b=text.index('### 1.2.')
        # Preserve technology/tooling figure 02 and its pinned version evidence.
        tech_start=text.index('### '+('Technology and tooling architecture' if lang=='en' else 'Kiến trúc công nghệ và công cụ'))
        tech_end=text.index('### ',tech_start+4)
        tech=text[tech_start:tech_end]
        text=text[:a]+'### 1.1. System / Application Architecture\n\n'+section(1,lang)+'\n'+tech+section(3,lang)+'\n'+text[b:]
        a=text.index('### 1.2.');b=text.index('<!-- role-usecases:start -->')
        text=text[:a]+'### 1.2. Use Cases Diagrams\n\n'+localize('[[One diagram per human role. Common authentication goals are separate; business tasks require a valid session. Use cases describe goals, not the order of a visit.||Mỗi role người dùng có một sơ đồ. Đăng nhập và gia hạn độc lập; tác vụ nghiệp vụ cần phiên hợp lệ. Use case mô tả mục tiêu, không mô tả thứ tự một lượt khám.]]',lang)+'\n\n'+text[b:]
        a=text.index('### 1.3.');b=text.index('## 2. Data Flow Diagram')
        intro=localize('[[The main journey uses the V1 outpatient contract consistently. Read in this order: schedule → arrive/check in → pay exam fee → consultation → indicated Lab/payment/results → doctor conclusion → optional prescription → payment/explicit dispense. Figures 06 and 04 are independent authentication scenarios. Each actor uses its own authorized session; abbreviated service API lifelines route through Gateway. V1 feature/acceptance gates still apply. Reports and notifications update independently and do not block care.||Luồng chính dùng nhất quán hợp đồng ngoại trú V1. Đọc theo thứ tự: đặt lịch → đến/check-in → thu phí khám → khám → xét nghiệm/thanh toán/kết quả nếu có chỉ định → bác sĩ kết luận → kê đơn khi cần → thanh toán/chủ động cấp thuốc. Hình 06 và 04 là hai tình huống xác thực độc lập. Mỗi actor dùng phiên đúng quyền; lifeline API service viết gọn vẫn đi qua Gateway. Vẫn áp dụng feature/acceptance gate V1. Báo cáo và thông báo cập nhật riêng, không chặn chăm sóc.]]',lang)
        text=text[:a]+'### 1.3. Sequences Diagrams\n\n'+intro+'\n\n'+'\n'.join(section(n,lang) for n in [6,4,5,7,8,37,38,9])+'\n### 1.4. Class Diagrams\n\n'+localize('[[Domain classes are grouped by deployed service. Core/Support preserve their internal context boundaries; Pharmacy/Billing share a figure but remain two independent services. Attributes are selective and must match source; dotted integration dependencies are not database relationships.||Các lớp domain nhóm theo service triển khai. Core/Support giữ ranh giới context bên trong; Pharmacy/Billing cùng một hình nhưng là hai service độc lập. Thuộc tính chọn lọc phải khớp source; phụ thuộc tích hợp nét đứt không phải quan hệ database.]]',lang)+'\n\n'+'\n'.join(section(n,lang) for n in [39,10,40,11,41])+'\n'+text[b:]
        marker='## 2. Data Flow Diagram\n'
        text=re.sub(r'(?m)^(?:Consistency rule:|Quy tắc đồng bộ:).*\n', '', text)
        text=text.replace(marker,marker+'\n'+localize('[[Consistency rule: EXAM, LAB_TEST and PRESCRIPTION are distinct payment targets. A stored payment is not an immediate guarantee of event delivery. Clinical outcome explanation and physical medicine handover are human interactions; neither implies a new patient self-service endpoint. The older V0 invoice/saga tables and algorithms remain compatibility references, outside the main V1 sequences.||Quy tắc đồng bộ: EXAM, LAB_TEST và PRESCRIPTION là ba đích thanh toán riêng. Lưu thanh toán không bảo đảm sự kiện đã được nhận ngay. Giải thích kết quả khám và giao thuốc thực tế là tương tác con người, không tạo endpoint Patient tự phục vụ mới. Các bảng/thuật toán invoice/saga V0 cũ còn là tham chiếu compatibility, ngoài bộ sequence V1 chính.]]',lang)+'\n',1)
        p.write_text(polish_report(text,lang),encoding='utf8')
    (OUT/'supervisor-model.json').write_text(json.dumps(FIGURES,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (OUT/'changed-supervisor-sources.json').write_text(json.dumps([f'{n:02}-{lang}.mmd' for n in FIGURES for lang in ['en','vi']],indent=2)+'\n',encoding='utf8')
    from supervisor_panels import write_panels
    write_panels(ROOT,selected,source)
    print('Authored architecture, eight V1/auth sequences, five service-grouped class figures')


if __name__=='__main__':
    import sys
    main(set(map(int,sys.argv[1:])) if len(sys.argv)>1 else None)
