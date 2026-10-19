# MediFlow — Review 2: Thiết kế kiến trúc, dữ liệu và giao diện

[Bản tiếng Anh](01-review-2-standard-en.md)

| Mục | Nội dung |
|---|---|
| Project | MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management |
| Review | Review 2 |
| Baseline | 2026-10-19 · HEAD `434c609be5ad` + working tree hiện có |
| Scope | MVP ngoại trú; giữ source nội trú/phẫu thuật ở trạng thái hoãn |
| Status | Tài liệu thiết kế đối chiếu source; không phải chứng nhận nghiệm thu runtime |

Tài liệu này kế thừa bố cục báo cáo song ngữ của Review 1 nhưng cập nhật theo source hiện tại. Một số mô tả lịch sử trong Review 1 còn nói 12 module/chưa có Support: hiện root reactor có **13 module**, Compose mặc định có **6 business deployments**, **8 context stores active**. Gateway/Eureka là hạ tầng, common là thư viện; Report/Notification là thư viện trong Support và giữ launcher rollback.

Quy ước trạng thái: **SOURCE** = có code/config; **GATED** = cần đúng profile/contract/activation; **DEFERRED** = giữ source nhưng ngoài MVP. Sơ đồ mô tả hành vi theo mode được ghi, không khẳng định mọi đường đi đã chạy thành công trên môi trường hiện tại. Không chạy lại backend hoặc kiểm tra DB thật trong lần lập tài liệu này.

## Quy chuẩn sơ đồ

Tất cả sơ đồ dùng theme **MediFlow Sky/Teal**: nền trắng, chữ slate `#0f172a`, khối xanh `#e0f2fe`, viền `#0284c7`, phụ trợ teal `#ccfbf1`. Mỗi khối Mermaid chứa cùng cấu hình theme. Mũi tên liền biểu thị request/quan hệ trực tiếp; nét đứt trong kiến trúc biểu thị message/discovery. ERD chỉ dùng FK có trong cùng store; tham chiếu giữa contexts ghi riêng bằng UUID.

Ảnh SVG là bản dựng để xem trực tiếp; mở mục Mermaid bên dưới để sửa nguồn. Use case dùng flowchart Mermaid với actor SVG hình người và use case bo tròn, giữ tương thích Mermaid 11. Logo công nghệ lấy từ Devicon, lưu kèm để dựng ảnh offline; nguồn Mermaid giữ URL CDN. Trình đọc chặn HTML/ảnh ngoài có thể bỏ logo hoặc actor: xem ảnh SVG kèm theo. Xem [quy tắc flowchart của Mermaid](https://mermaid.js.org/syntax/flowchart.html).

## 1. Architecture Design

### 1.1. System / Application Architecture

Web gọi same-origin `/api/*`, qua proxy tới Gateway; Flutter gọi Gateway qua cấu hình `GATEWAY_URL`. Gateway xác minh JWT/route policy; downstream tiếp tục kiểm role, ownership và quyền nghiệp vụ. PostgreSQL 16 là một container phát triển, chứa các database context riêng. RabbitMQ giữ queue riêng cho consumer; không có truy vấn SQL xuyên context.

### Kiến trúc hệ thống và ranh giới dữ liệu

**Cách đọc kiến trúc:** Client side và Server side là các ranh giới riêng. Mũi tên request và response thể hiện giao tiếp HTTP đồng bộ; cạnh hai đầu gom một request và đường trả kết quả. Cạnh sự kiện nét đứt là message bất đồng bộ, không phải HTTP response. Proxy Next.js nằm ở Server side dù UI chạy trên client. Các port triển khai đã được lược bỏ.

![Kiến trúc hệ thống và ranh giới dữ liệu](assets/svg/01-vi.svg)

<details>
<summary>Mermaid · 01-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50, "subGraphTitleMargin": {"top": 8, "bottom": 28}}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}, "themeCSS": ".nodeLabel img {display:inline-block!important;width:28px!important;height:28px!important;vertical-align:middle!important;} .nodeLabel p {margin:0;}"}}%%
flowchart TB
 subgraph CLIENT["Client side — thiết bị người dùng"]
 WEB["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iNSIgeT0iNyIgd2lkdGg9IjU0IiBoZWlnaHQ9IjM2IiByeD0iNCIvPjxwYXRoIGQ9Ik0yNCA1NWgxNk0zMiA0M3YxMk02IDM0aDUyIi8+PGNpcmNsZSBjeD0iMzIiIGN5PSIyMCIgcj0iNSIvPjxwYXRoIGQ9Ik0yMiAzMWMwLTggMjAtOCAyMCAwIi8+PC9nPjwvc3ZnPg==' width='32' height='32'/><br/><b>Client side · Web</b><br/><img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/nextjs/nextjs-original.svg' width='34' height='34'/> <img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/react/react-original.svg' width='34' height='34'/><br/>Next.js UI / React<br/>TypeScript / Tailwind CSS"]
 MOB["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iMTYiIHk9IjMiIHdpZHRoPSIzMiIgaGVpZ2h0PSI1OCIgcng9IjYiLz48cGF0aCBkPSJNMjYgMTBoMTJNMjkgNTRoNiIvPjxjaXJjbGUgY3g9IjMyIiBjeT0iMjYiIHI9IjUiLz48cGF0aCBkPSJNMjIgNDBjMC0xMCAyMC0xMCAyMCAwIi8+PC9nPjwvc3ZnPg==' width='32' height='32'/><br/><b>Client side · Mobile</b><br/><img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/flutter/flutter-original.svg' width='34' height='34'/> <img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/dart/dart-original.svg' width='34' height='34'/><br/>Flutter / Riverpod<br/>Dio / GoRouter / SecureStorage"]
 end
 subgraph SERVER["Server side — ứng dụng và hạ tầng"]
 PX["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/nextjs/nextjs-original.svg' width='34' height='34'/> <img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/nodejs/nodejs-original.svg' width='34' height='34'/><br/>Next.js server / Node.js<br/>same-origin /api rewrite"]
 GW["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/spring/spring-original.svg' width='34' height='34'/><br/>Spring Cloud Gateway<br/>JWT / route authorization"]
 EU["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/spring/spring-original.svg' width='34' height='34'/><br/>Eureka<br/>Service registry"]
 MQ["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/rabbitmq/rabbitmq-original.svg' width='34' height='34'/><br/>RabbitMQ · retry / DLQ<br/>Publish: Clinical / Lab / Pharmacy / Billing<br/>Consume: Support and configured contexts"]
 DOCK["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/docker/docker-original.svg' width='34' height='34'/><br/>Docker Compose<br/>Development orchestration"]
 subgraph APPS["Sáu deployment nghiệp vụ — Java / Spring Boot"]
 CORE["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iOCIgeT0iNSIgd2lkdGg9IjQ4IiBoZWlnaHQ9IjE1IiByeD0iMyIvPjxyZWN0IHg9IjgiIHk9IjI1IiB3aWR0aD0iNDgiIGhlaWdodD0iMTUiIHJ4PSIzIi8+PHJlY3QgeD0iOCIgeT0iNDUiIHdpZHRoPSI0OCIgaGVpZ2h0PSIxNSIgcng9IjMiLz48cGF0aCBkPSJNMTcgMTJoMTdNMTcgMzJoMTdNMTcgNTJoMTciLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjEyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjMyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjUyIiByPSIxLjUiLz48L2c+PC9zdmc+' width='32' height='32'/><br/>Core<br/>Organization + Patient"]
 CL["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iOCIgeT0iNSIgd2lkdGg9IjQ4IiBoZWlnaHQ9IjE1IiByeD0iMyIvPjxyZWN0IHg9IjgiIHk9IjI1IiB3aWR0aD0iNDgiIGhlaWdodD0iMTUiIHJ4PSIzIi8+PHJlY3QgeD0iOCIgeT0iNDUiIHdpZHRoPSI0OCIgaGVpZ2h0PSIxNSIgcng9IjMiLz48cGF0aCBkPSJNMTcgMTJoMTdNMTcgMzJoMTdNMTcgNTJoMTciLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjEyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjMyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjUyIiByPSIxLjUiLz48L2c+PC9zdmc+' width='32' height='32'/><br/>Clinical"]
 LAB["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iOCIgeT0iNSIgd2lkdGg9IjQ4IiBoZWlnaHQ9IjE1IiByeD0iMyIvPjxyZWN0IHg9IjgiIHk9IjI1IiB3aWR0aD0iNDgiIGhlaWdodD0iMTUiIHJ4PSIzIi8+PHJlY3QgeD0iOCIgeT0iNDUiIHdpZHRoPSI0OCIgaGVpZ2h0PSIxNSIgcng9IjMiLz48cGF0aCBkPSJNMTcgMTJoMTdNMTcgMzJoMTdNMTcgNTJoMTciLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjEyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjMyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjUyIiByPSIxLjUiLz48L2c+PC9zdmc+' width='32' height='32'/><br/>Lab"]
 PH["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iOCIgeT0iNSIgd2lkdGg9IjQ4IiBoZWlnaHQ9IjE1IiByeD0iMyIvPjxyZWN0IHg9IjgiIHk9IjI1IiB3aWR0aD0iNDgiIGhlaWdodD0iMTUiIHJ4PSIzIi8+PHJlY3QgeD0iOCIgeT0iNDUiIHdpZHRoPSI0OCIgaGVpZ2h0PSIxNSIgcng9IjMiLz48cGF0aCBkPSJNMTcgMTJoMTdNMTcgMzJoMTdNMTcgNTJoMTciLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjEyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjMyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjUyIiByPSIxLjUiLz48L2c+PC9zdmc+' width='32' height='32'/><br/>Pharmacy"]
 BI["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iOCIgeT0iNSIgd2lkdGg9IjQ4IiBoZWlnaHQ9IjE1IiByeD0iMyIvPjxyZWN0IHg9IjgiIHk9IjI1IiB3aWR0aD0iNDgiIGhlaWdodD0iMTUiIHJ4PSIzIi8+PHJlY3QgeD0iOCIgeT0iNDUiIHdpZHRoPSI0OCIgaGVpZ2h0PSIxNSIgcng9IjMiLz48cGF0aCBkPSJNMTcgMTJoMTdNMTcgMzJoMTdNMTcgNTJoMTciLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjEyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjMyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjUyIiByPSIxLjUiLz48L2c+PC9zdmc+' width='32' height='32'/><br/>Billing"]
 SU["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iOCIgeT0iNSIgd2lkdGg9IjQ4IiBoZWlnaHQ9IjE1IiByeD0iMyIvPjxyZWN0IHg9IjgiIHk9IjI1IiB3aWR0aD0iNDgiIGhlaWdodD0iMTUiIHJ4PSIzIi8+PHJlY3QgeD0iOCIgeT0iNDUiIHdpZHRoPSI0OCIgaGVpZ2h0PSIxNSIgcng9IjMiLz48cGF0aCBkPSJNMTcgMTJoMTdNMTcgMzJoMTdNMTcgNTJoMTciLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjEyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjMyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjUyIiByPSIxLjUiLz48L2c+PC9zdmc+' width='32' height='32'/><br/>Support<br/>Report + Notification"]
 end
 subgraph STORES["PostgreSQL 16 — tám store riêng theo context"]
 PG["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/postgresql/postgresql-original.svg' width='34' height='34'/><br/>No cross-context SQL or foreign keys"]
 CDB[(organization / patient)]
 CLDB[(clinical)]
 LDB[(lab)]
 PDB[(pharmacy)]
 BDB[(billing)]
 SDB[(report / notification)]
 end
 end
 WEB -->|HTTP request /api| PX
 PX -->|JSON response / status| WEB
 MOB -->|HTTP request + JWT| GW
 GW -->|JSON response / status| MOB
 PX <-->|Forward request / return response| GW
 GW <-->|Request / response| CORE & CL & LAB & PH & BI & SU
 CORE <-->|Own store read / write| CDB
 CL <-->|Own store read / write| CLDB
 LAB <-->|Own store read / write| LDB
 PH <-->|Own store read / write| PDB
 BI <-->|Own store read / write| BDB
 SU <-->|Separate store read / write| SDB
 APPS -. register / discover .-> EU
 CL & LAB & PH & BI -. durable outbox events .-> MQ
 MQ -. inbox / projections / configured handlers .-> SU
 DOCK -. hosts .-> APPS
```

</details>

### Kiến trúc công nghệ và công cụ

![Kiến trúc công nghệ và công cụ](assets/svg/02-vi.svg)

<details>
<summary>Mermaid · 02-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart TB
 subgraph WEB["Web build"]
 direction LR
 nextjs["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/nextjs/nextjs-original.svg' width='34' height='34'/><br/>Next.js"]
 react["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/react/react-original.svg' width='34' height='34'/><br/>React"]
 typescript["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/typescript/typescript-original.svg' width='34' height='34'/><br/>TypeScript"]
 tailwindcss["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/tailwindcss/tailwindcss-original.svg' width='34' height='34'/><br/>Tailwind CSS"]
 nodejs["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/nodejs/nodejs-original.svg' width='34' height='34'/><br/>Node.js"]
 pnpm["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/pnpm/pnpm-original.svg' width='34' height='34'/><br/>pnpm"]
 nextjs ~~~ react ~~~ typescript ~~~ tailwindcss ~~~ nodejs ~~~ pnpm
 end
 subgraph MOBILE["Mobile"]
 direction LR
 flutter["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/flutter/flutter-original.svg' width='34' height='34'/><br/>Flutter"]
 dart["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/dart/dart-original.svg' width='34' height='34'/><br/>Dart"]
 flutter ~~~ dart
 end
 subgraph BACK["Backend"]
 direction LR
 java["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/java/java-original.svg' width='34' height='34'/><br/>Java"]
 spring["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/spring/spring-original.svg' width='34' height='34'/><br/>Spring"]
 maven["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/maven/maven-original.svg' width='34' height='34'/><br/>Maven"]
 hibernate["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/hibernate/hibernate-original.svg' width='34' height='34'/><br/>Hibernate"]
 swagger["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/swagger/swagger-original.svg' width='34' height='34'/><br/>OpenAPI / Swagger"]
 java ~~~ spring ~~~ maven ~~~ hibernate ~~~ swagger
 end
 subgraph DATA["Data / messaging / runtime"]
 direction LR
 postgresql["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/postgresql/postgresql-original.svg' width='34' height='34'/><br/>PostgreSQL"]
 rabbitmq["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/rabbitmq/rabbitmq-original.svg' width='34' height='34'/><br/>RabbitMQ"]
 docker["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/docker/docker-original.svg' width='34' height='34'/><br/>Docker"]
 postgresql ~~~ rabbitmq ~~~ docker
 end
 subgraph QUALITY["Source / verification"]
 direction LR
 git["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/git/git-original.svg' width='34' height='34'/><br/>Git"]
 githubactions["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/githubactions/githubactions-original.svg' width='34' height='34'/><br/>GitHub Actions"]
 junit["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/junit/junit-original.svg' width='34' height='34'/><br/>JUnit"]
 vitest["<img src='https://cdn.jsdelivr.net/gh/devicons/devicon@v2.17.0/icons/vitest/vitest-original.svg' width='34' height='34'/><br/>Vitest"]
 git ~~~ githubactions ~~~ junit ~~~ vitest
 end
 WEB ~~~ MOBILE ~~~ BACK ~~~ DATA ~~~ QUALITY
```

</details>
Các thư viện không có logo riêng trong bộ logo đi kèm được ghi bằng tên trong bảng sau; chúng không phải deployment mới. Logo Spring đại diện Boot/Cloud/Security/Data, logo Flutter đại diện hệ sinh thái mobile. Không thêm Redis, Kafka, Kubernetes hoặc payment provider ngoài vào kiến trúc.

| Lớp | Stack xác nhận trong source | Nguồn |
|---|---|---|
| Backend | Java 21; Spring Boot 3.3.5; Spring Cloud 2023.0.3; Maven; Spring Security; Spring Data JPA/Hibernate; Flyway; MapStruct; Lombok; JJWT 0.12.6; springdoc 2.6.0 | [pom.xml](../../../../pom.xml) |
| Web | Next.js 16.2.11; React 19.2.4; TypeScript 5; Tailwind CSS 4; Node.js/pnpm; Vitest 4; ESLint 9; Testing Library | [frontend/package.json](../../../../frontend/package.json) |
| Mobile | Flutter; Dart ^3.13.0; Riverpod ^3.4.3; GoRouter ^18.1.0; Dio ^5.11.1; flutter_secure_storage ^11.2.0 | [mobile/pubspec.yaml](../../../../mobile/pubspec.yaml) |
| Infrastructure | PostgreSQL 16-alpine; RabbitMQ 3-management-alpine; Docker Compose; Eureka; Gateway | [docker-compose.yml](../../../../docker-compose.yml) |
| Verification | JUnit / Spring Boot Test / Testcontainers; Git / GitHub Actions | [backend/support-service/pom.xml](../../../../backend/support-service/pom.xml) |

Phiên bản có `^` là constraint của manifest, không phải kết quả đọc runtime đã cài. Logo và tên công nghệ không chứng minh đã triển khai production.

### Kiến trúc ứng dụng và chiều phụ thuộc

**Chi tiết ứng dụng:** Các phần runtime mô tả request/response lúc chạy qua các module client, Gateway, controller, application service, output port, adapter và store riêng. Phần phụ thuộc thể hiện import source riêng: Web routes → features → shared API; Flutter presentation → domain ← data; backend driving adapters → application → domain, infrastructure hiện thực application port. Lỗi chặn tại Gateway và lỗi nghiệp vụ downstream trả theo đường đồng bộ tương ứng. Ngoại lệ API raw/legacy vẫn theo wire contract thực tế. Các khối backend mô tả mẫu theo context, không khẳng định mọi service đã kích hoạt toàn bộ tính năng.

![Kiến trúc ứng dụng và chiều phụ thuộc](assets/svg/03-vi.svg)

<details>
<summary>Mermaid · 03-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50, "subGraphTitleMargin": {"top": 8, "bottom": 28}}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}, "themeCSS": ".nodeLabel img {display:inline-block!important;width:28px!important;height:28px!important;vertical-align:middle!important;} .nodeLabel p {margin:0;}"}}%%
flowchart TB
 subgraph CLIENT["Client side — thiết bị người dùng"]
 subgraph WEBAPP["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iNSIgeT0iNyIgd2lkdGg9IjU0IiBoZWlnaHQ9IjM2IiByeD0iNCIvPjxwYXRoIGQ9Ik0yNCA1NWgxNk0zMiA0M3YxMk02IDM0aDUyIi8+PGNpcmNsZSBjeD0iMzIiIGN5PSIyMCIgcj0iNSIvPjxwYXRoIGQ9Ik0yMiAzMWMwLTggMjAtOCAyMCAwIi8+PC9nPjwvc3ZnPg==' width='32' height='32'/> Client side · Web / Next.js"]
 WR["App Router pages / layouts<br/>Role-aware navigation + shared UI"]
 WF["features/context<br/>components · api.ts · wire types"]
 WA["lib/api.ts<br/>Bearer JWT · envelope unwrap<br/>ApiRequestError / 401 session clear"]
 WR <-->|UI action / render state| WF
 WF <-->|API call / data or error| WA
 end
 subgraph MOBILEAPP["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iMTYiIHk9IjMiIHdpZHRoPSIzMiIgaGVpZ2h0PSI1OCIgcng9IjYiLz48cGF0aCBkPSJNMjYgMTBoMTJNMjkgNTRoNiIvPjxjaXJjbGUgY3g9IjMyIiBjeT0iMjYiIHI9IjUiLz48cGF0aCBkPSJNMjIgNDBjMC0xMCAyMC0xMCAyMCAwIi8+PC9nPjwvc3ZnPg==' width='32' height='32'/> Client side · Flutter"]
 MP["Presentation<br/>Riverpod providers / pages / widgets"]
 MU["Domain use cases / repository contracts<br/>Data repository implementation + DTO mapping"]
 MD["core/network/ApiClient · Dio<br/>Gateway-only paths · JWT<br/>envelope / failure mapping"]
 MP <-->|User intent / state| MU
 MU <-->|Data request / DTO result| MD
 end
 end
 subgraph EDGE["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iOCIgeT0iNSIgd2lkdGg9IjQ4IiBoZWlnaHQ9IjE1IiByeD0iMyIvPjxyZWN0IHg9IjgiIHk9IjI1IiB3aWR0aD0iNDgiIGhlaWdodD0iMTUiIHJ4PSIzIi8+PHJlY3QgeD0iOCIgeT0iNDUiIHdpZHRoPSI0OCIgaGVpZ2h0PSIxNSIgcng9IjMiLz48cGF0aCBkPSJNMTcgMTJoMTdNMTcgMzJoMTdNMTcgNTJoMTciLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjEyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjMyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjUyIiByPSIxLjUiLz48L2c+PC9zdmc+' width='32' height='32'/> Server side · API boundary"]
 NX["Next.js server rewrite<br/>same-origin /api to Gateway"]
 G["Spring Cloud Gateway<br/>JWT authentication / route-role policy"]
 end
 WA -->|HTTP request /api + JWT| NX
 NX -->|JSON response / status / error| WA
 NX <-->|Request / response| G
 MD -->|HTTP request to Gateway| G
 G -->|JSON response / status / error| MD
 subgraph BACKEND["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iOCIgeT0iNSIgd2lkdGg9IjQ4IiBoZWlnaHQ9IjE1IiByeD0iMyIvPjxyZWN0IHg9IjgiIHk9IjI1IiB3aWR0aD0iNDgiIGhlaWdodD0iMTUiIHJ4PSIzIi8+PHJlY3QgeD0iOCIgeT0iNDUiIHdpZHRoPSI0OCIgaGVpZ2h0PSIxNSIgcng9IjMiLz48cGF0aCBkPSJNMTcgMTJoMTdNMTcgMzJoMTdNMTcgNTJoMTciLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjEyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjMyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjUyIiByPSIxLjUiLz48L2c+PC9zdmc+' width='32' height='32'/> Server side · each logical backend context"]
 WC["Web controller / request DTO<br/>Bean validation / PreAuthorize<br/>Context and actor checks"]
 AS["Application service / input use case<br/>DTO mapping / local transaction<br/>Idempotency and business orchestration"]
 DM["Domain models / business rules<br/>UUID / money / lifecycle invariants<br/>Framework-free"]
 OP["Application output ports<br/>Repository / external context / event contracts"]
 PA["Persistence adapter<br/>JPA repository / entity mapper / Flyway"]
 RA["HTTP client adapter<br/>Resilient authorized context lookup"]
 OA["Outbox persistence / publisher adapter<br/>Only configured state-changing paths"]
 IA["Messaging consumer / inbox adapter<br/>Deduplication + local effect transaction"]
 WC <-->|Command or query / result DTO| AS
 AS -->|Evaluate rules| DM
 AS <-->|Port operation / mapped result| OP
 OP <-->|Repository operation / domain result| PA
 OP <-->|Context lookup / authorized DTO| RA
 AS -->|State change + durable event| OA
 IA -->|Input command / integration fact| AS
 end
 subgraph IO["Server side · owned data and integration"]
 DB[(Context-owned PostgreSQL store)]
 Q["RabbitMQ<br/>Durable queues / retry / DLQ"]
 PEER["Other context REST API<br/>Own authorization / own store"]
 end
 PA <-->|SQL write or read / rows| DB
 OA -->|Local outbox commit| DB
 OA -. publish after commit .-> Q
 Q -. asynchronous delivery .-> IA
 RA <-->|HTTP request / response| PEER
 G -->|Authorized HTTP request| WC
 WC -->|ApiResponse data or error / HTTP status| G
 subgraph DEP["Chiều phụ thuộc tĩnh — mũi tên là import hoặc hiện thực, không phải request mạng"]
 subgraph DC["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iNSIgeT0iNyIgd2lkdGg9IjU0IiBoZWlnaHQ9IjM2IiByeD0iNCIvPjxwYXRoIGQ9Ik0yNCA1NWgxNk0zMiA0M3YxMk02IDM0aDUyIi8+PGNpcmNsZSBjeD0iMzIiIGN5PSIyMCIgcj0iNSIvPjxwYXRoIGQ9Ik0yMiAzMWMwLTggMjAtOCAyMCAwIi8+PC9nPjwvc3ZnPg==' width='32' height='32'/> Client side · source dependencies"]
 DW["Web app routes"] -. imports .-> DF["Feature modules"]
 DF -. imports .-> DL["Shared lib/api and wire types"]
 DP["Flutter presentation"] -. imports .-> DD["Flutter domain<br/>Entities / use cases / repository contracts"]
 DT["Flutter data"] -. implements / imports .-> DD
 end
 subgraph DS["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PGcgZmlsbD0iI2UwZjJmZSIgc3Ryb2tlPSIjMDM2OWExIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PHJlY3QgeD0iOCIgeT0iNSIgd2lkdGg9IjQ4IiBoZWlnaHQ9IjE1IiByeD0iMyIvPjxyZWN0IHg9IjgiIHk9IjI1IiB3aWR0aD0iNDgiIGhlaWdodD0iMTUiIHJ4PSIzIi8+PHJlY3QgeD0iOCIgeT0iNDUiIHdpZHRoPSI0OCIgaGVpZ2h0PSIxNSIgcng9IjMiLz48cGF0aCBkPSJNMTcgMTJoMTdNMTcgMzJoMTdNMTcgNTJoMTciLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjEyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjMyIiByPSIxLjUiLz48Y2lyY2xlIGN4PSI0NiIgY3k9IjUyIiByPSIxLjUiLz48L2c+PC9zdmc+' width='32' height='32'/> Server side · inward dependencies"]
 DA["Web / messaging driving adapters"] -. imports .-> DAPP["Application<br/>Services / ports / DTOs / events"]
 DI["Persistence / REST / messaging adapters"] -. implements ports .-> DAPP
 DAPP -. imports .-> DDOMAIN["Domain<br/>Models / rules / exceptions"]
 DI -. maps domain objects .-> DDOMAIN
 end
 end
```

</details>
Domain Java không phụ thuộc Spring/JPA; ports nằm ở application, adapters implement ports ở infrastructure. Web giữ feature-based structure, không áp cây backend vào TypeScript. Core/Support chỉ composition, vẫn tách datasource/Flyway/entity manager/transaction manager cho từng context.

### 1.2. Use Cases Diagrams

Đây là sơ đồ vai trò/use case trong phạm vi MVP. Association chỉ thể hiện nhóm người dùng, không thay ma trận `@PreAuthorize` hay cấp quyền cho tất cả bản ghi. `NURSE` tham gia tiếp nhận theo endpoint policy; không thêm role RECEPTIONIST. `SYSTEM` là actor máy trong sequence, không phải tài khoản người dùng.

### Use case tiếp nhận, khám và xét nghiệm

![Use case tiếp nhận, khám và xét nghiệm](assets/svg/04-vi.svg)

<details>
<summary>Mermaid · 04-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart LR
 PAT["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='34' height='34'/><br/>PATIENT"]
 NUR["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='34' height='34'/><br/>NURSE"]
 DOC["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='34' height='34'/><br/>DOCTOR"]
 LT["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='34' height='34'/><br/>LAB_TECH"]
 subgraph SYS["MediFlow — Outpatient MVP"]
 U1(["Đăng nhập / gia hạn phiên"])
 U2(["Quản lý lịch hẹn / check-in"])
 U3(["Khám / diagnosis / hoàn tất ngoại trú"])
 U4(["Chỉ định xét nghiệm"])
 U5(["Bắt đầu xét nghiệm / ghi kết quả"])
 U6(["Xem kết quả theo quyền"])
 U7(["Kê đơn thuốc"])
 end
 PAT --- U1
 NUR --- U2
 DOC --- U2
 DOC --- U3
 DOC --- U4
 DOC --- U6
 DOC --- U7
 LT --- U5
 classDef actor fill:transparent,stroke:transparent,color:#0f172a;
 class PAT,NUR,DOC,LT actor;
```

</details>

### Use case thuốc, viện phí và hỗ trợ

![Use case thuốc, viện phí và hỗ trợ](assets/svg/05-vi.svg)

<details>
<summary>Mermaid · 05-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart LR
 PH["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='34' height='34'/><br/>PHARMACIST"]
 CA["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='34' height='34'/><br/>CASHIER"]
 MA["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='34' height='34'/><br/>MANAGER"]
 AD["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='34' height='34'/><br/>ADMIN"]
 PA["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='34' height='34'/><br/>PATIENT"]
 subgraph SYS["MediFlow — Outpatient MVP"]
 U8(["Danh mục thuốc / điều chỉnh tồn"])
 U9(["Tra cứu đơn / cấp thuốc"])
 U10(["Xem khoản thu / ghi nhận thanh toán"])
 U11(["Điều chỉnh / refund theo policy"])
 U12(["Xem báo cáo có source coverage"])
 U13(["Đọc thông báo của mình"])
 U14(["Quản lý account / khoa / staff"])
 end
 PH --- U8
 PH --- U9
 CA --- U10
 CA --- U11
 MA --- U12
 AD --- U14
 PA --- U13
 classDef actor fill:transparent,stroke:transparent,color:#0f172a;
 class PH,CA,MA,AD,PA actor;
```

</details>

| ID | Điều kiện / kết quả nghiệp vụ |
|---|---|
| U2 | Patient/staff hợp lệ; lịch không quá khứ; unique PENDING theo patient/ngày; check-in không đồng nghĩa được khám. |
| U3 | V1 cần exact EXAM clearance hoặc audited override đã hỗ trợ; completion có diagnosis/disposition ngoại trú. |
| U4–U5 | Chỉ định rõ ràng; V1 LAB_TEST clearance đúng testId trước protected action; kết quả không rỗng, thời gian hợp lệ. |
| U7–U9 | Giá snapshot từ server; reserve không trừ stock; dispense nguyên tử, chống trùng; V1 cần actor và clearance. |
| U10–U11 | Billing owns tiền; partial payment có guard; replay cùng key trả receipt cũ, khác intent báo conflict. |
| U12–U13 | Report eventually consistent, unavailable khác zero; Notification không quyết định authority. |

<!-- role-usecases:start -->
### Use case chi tiết — một sơ đồ cho mỗi vai trò

Hình 29–36 theo mẫu của giảng viên: mỗi hình có một actor người dùng, mũi tên liền đến tác vụ chính, mũi tên đứt `«include»` cho hành vi con bắt buộc và mũi tên đứt `«extend»` từ nhánh mở rộng có điều kiện về tác vụ gốc. Nhánh mở rộng có thể có luồng con `include` riêng. Mũi tên thể hiện quan hệ, không phải thứ tự thực hiện. Nhân viên đã đăng nhập là tiền điều kiện, không lặp lại ở mọi tác vụ. Oval xanh dương là tác vụ chính; xanh teal là tác vụ con/mở rộng. Hình mô tả khả năng trong source, không khẳng định mọi UI đã hoàn thiện hoặc mọi feature đã bật.

**Hiệu chỉnh phạm vi:** PATIENT hiện có đăng nhập/refresh và truy cập thông báo của mình. Hình tổng quan 04 đã bỏ quyền tự thao tác lịch hẹn/xét nghiệm cho PATIENT. SYSTEM là định danh máy, không tạo sơ đồ role người dùng. Quyền từng role lấy giao của ma trận Gateway và guards downstream.

#### ADMIN — Tác vụ quản trị viên

![ADMIN — Tác vụ quản trị viên](assets/svg/29-vi.svg)

<details>
<summary>Mermaid · 29-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff", "fontSize": "18px"}, "flowchart": {"htmlLabels": true, "curve": "linear", "nodeSpacing": 30, "rankSpacing": 44, "padding": 24, "diagramPadding": 24, "wrappingWidth": 165, "subGraphTitleMargin": {"top": 10, "bottom": 32}}, "sequence": {"useMaxWidth": true, "wrap": true, "actorMargin": 65, "width": 210, "height": 55, "boxMargin": 14, "boxTextMargin": 8, "noteMargin": 16, "messageMargin": 24, "diagramMarginX": 24, "diagramMarginY": 24, "mirrorActors": true, "actorFontSize": 17, "messageFontSize": 16, "noteFontSize": 16, "wrapPadding": 10}, "er": {"useMaxWidth": true, "nodeSpacing": 45, "rankSpacing": 55, "diagramPadding": 24, "entityPadding": 10, "fontSize": 17}, "themeCSS": ".nodeLabel p {margin:0;} .edgeLabel {font-size:15px;} .nodeLabel img {display:inline-block!important;width:36px!important;height:48px!important;}", "class": {"diagramPadding": 24, "nodeSpacing": 32, "rankSpacing": 45}}}%%
%% Role use case: one actor; solid primary task; dashed include/extend.
flowchart TB
 ACTOR["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='36' height='48'/><br/>ADMIN"]
 subgraph SYS["MediFlow — ADMIN"]
 A1(["Tạo tài khoản"])
 A2(["Gán vai trò và định danh"])
 A3(["Đổi trạng thái tài khoản"])
 A4(["Kiểm tra tài khoản đích"])
 A5(["Tạo hồ sơ nhân viên"])
 A6(["Ghi thông tin nhân viên"])
 A7(["Cập nhật hồ sơ nhân viên"])
 A8(["Xem hồ sơ nhân viên"])
 A9(["Chuyển khoa công tác"])
 A10(["Kiểm tra khoa tiếp nhận"])
 A11(["Cập nhật khoa"])
 A12(["Xem thông tin khoa"])
 A13(["Tạo khoa"])
 A14(["Nhập mã và tên khoa"])
 A15(["Xem báo cáo quản lý"])
 A16(["Chọn kỳ và phạm vi"])
 A17(["Gửi thông báo"])
 A18(["Chọn người nhận và kênh"])
 end
 ACTOR --> A1
 ACTOR --> A3
 ACTOR --> A5
 ACTOR --> A7
 ACTOR --> A11
 ACTOR --> A13
 ACTOR --> A15
 ACTOR --> A17
 A1 -.->|"«include»"| A2
 A3 -.->|"«include»"| A4
 A5 -.->|"«include»"| A6
 A7 -.->|"«include»"| A8
 A9 -.->|"«extend» [E1]"| A7
 A9 -.->|"«include»"| A10
 A11 -.->|"«include»"| A12
 A13 -.->|"«include»"| A14
 A15 -.->|"«include»"| A16
 A17 -.->|"«include»"| A18
 classDef actor fill:transparent,stroke:transparent,color:#0f172a;
 classDef main fill:#e0f2fe,stroke:#0284c7,color:#0f172a;
 classDef sub fill:#f0fdfa,stroke:#0f766e,color:#0f172a;
 class ACTOR actor;
 class A1,A3,A5,A7,A11,A13,A15,A17 main;
 class A2,A4,A6,A8,A9,A10,A12,A14,A16,A18 sub;
```

</details>

Hình tập trung quản trị tổ chức/tài khoản. ADMIN còn có các quyền cụ thể tại những nghiệp vụ khác theo ma trận nguồn; không bỏ qua business guards. Không thêm xóa tài khoản hay màn hình chỉnh quyền tùy ý.

Điều kiện mở rộng: E1: Chuyển khoa công tác — cần chuyển khoa

Đối chiếu source: [AccountController](../../../../backend/core-service/src/main/java/com/mediflow/organization/web/controller/AccountController.java) · [StaffController](../../../../backend/core-service/src/main/java/com/mediflow/organization/web/controller/StaffController.java) · [DepartmentController](../../../../backend/core-service/src/main/java/com/mediflow/organization/web/controller/DepartmentController.java) · [ReportController](../../../../backend/report-service/src/main/java/com/mediflow/report/web/ReportController.java) · [NotificationController](../../../../backend/notification-service/src/main/java/com/mediflow/notification/web/NotificationController.java) · [RouteAuthorizationFilter](../../../../backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java)

#### MANAGER — Tác vụ quản lý

![MANAGER — Tác vụ quản lý](assets/svg/30-vi.svg)

<details>
<summary>Mermaid · 30-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff", "fontSize": "18px"}, "flowchart": {"htmlLabels": true, "curve": "linear", "nodeSpacing": 30, "rankSpacing": 44, "padding": 24, "diagramPadding": 24, "wrappingWidth": 165, "subGraphTitleMargin": {"top": 10, "bottom": 32}}, "sequence": {"useMaxWidth": true, "wrap": true, "actorMargin": 65, "width": 210, "height": 55, "boxMargin": 14, "boxTextMargin": 8, "noteMargin": 16, "messageMargin": 24, "diagramMarginX": 24, "diagramMarginY": 24, "mirrorActors": true, "actorFontSize": 17, "messageFontSize": 16, "noteFontSize": 16, "wrapPadding": 10}, "er": {"useMaxWidth": true, "nodeSpacing": 45, "rankSpacing": 55, "diagramPadding": 24, "entityPadding": 10, "fontSize": 17}, "themeCSS": ".nodeLabel p {margin:0;} .edgeLabel {font-size:15px;} .nodeLabel img {display:inline-block!important;width:36px!important;height:48px!important;}", "class": {"diagramPadding": 24, "nodeSpacing": 32, "rankSpacing": 45}}}%%
%% Role use case: one actor; solid primary task; dashed include/extend.
flowchart TB
 ACTOR["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='36' height='48'/><br/>MANAGER"]
 subgraph SYS["MediFlow — MANAGER"]
 M1(["Xem báo cáo ngoại trú"])
 M2(["Chọn kỳ báo cáo"])
 M3(["Xem độ phủ nguồn dữ liệu"])
 M4(["Xem báo cáo tài chính"])
 M5(["Chọn ngày hoặc tháng"])
 M6(["Xem thống kê thuốc"])
 M7(["Chọn kỳ và số lượng"])
 M8(["Xem danh sách lịch hẹn"])
 M9(["Đọc trang lịch hẹn"])
 M10(["Lọc theo khoa"])
 M11(["Xem danh sách nhân viên"])
 M12(["Đọc trang nhân viên"])
 end
 ACTOR --> M1
 ACTOR --> M4
 ACTOR --> M6
 ACTOR --> M8
 ACTOR --> M11
 M1 -.->|"«include»"| M2
 M1 -.->|"«include»"| M3
 M4 -.->|"«include»"| M5
 M6 -.->|"«include»"| M7
 M8 -.->|"«include»"| M9
 M10 -.->|"«extend» [E1]"| M8
 M11 -.->|"«include»"| M12
 classDef actor fill:transparent,stroke:transparent,color:#0f172a;
 classDef main fill:#e0f2fe,stroke:#0284c7,color:#0f172a;
 classDef sub fill:#f0fdfa,stroke:#0f766e,color:#0f172a;
 class ACTOR actor;
 class M1,M4,M6,M8,M11 main;
 class M2,M3,M5,M7,M9,M10,M12 sub;
```

</details>

Nghiệp vụ quản lý chỉ đọc: báo cáo ngày/tháng, top thuốc, doanh thu khoa, danh sách lịch hẹn/xét nghiệm, nhân viên và khoa. MANAGER không tạo tài khoản, sửa nhân viên hay thực hiện lệnh khám/thu tiền. Báo cáo snapshot phụ thuộc availability và coverage tại runtime.

Điều kiện mở rộng: E1: Lọc theo khoa — đã chọn khoa

Đối chiếu source: [OutpatientReportController](../../../../backend/report-service/src/main/java/com/mediflow/report/web/OutpatientReportController.java) · [FinancialSnapshotReportController](../../../../backend/report-service/src/main/java/com/mediflow/report/web/FinancialSnapshotReportController.java) · [ReportController](../../../../backend/report-service/src/main/java/com/mediflow/report/web/ReportController.java) · [RevenueController](../../../../backend/billing-service/src/main/java/com/mediflow/billing/web/RevenueController.java) · [AppointmentController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/AppointmentController.java) · [StaffController](../../../../backend/core-service/src/main/java/com/mediflow/organization/web/controller/StaffController.java)

#### DOCTOR — Tác vụ bác sĩ

![DOCTOR — Tác vụ bác sĩ](assets/svg/31-vi.svg)

<details>
<summary>Mermaid · 31-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff", "fontSize": "18px"}, "flowchart": {"htmlLabels": true, "curve": "linear", "nodeSpacing": 30, "rankSpacing": 44, "padding": 24, "diagramPadding": 24, "wrappingWidth": 165, "subGraphTitleMargin": {"top": 10, "bottom": 32}}, "sequence": {"useMaxWidth": true, "wrap": true, "actorMargin": 65, "width": 210, "height": 55, "boxMargin": 14, "boxTextMargin": 8, "noteMargin": 16, "messageMargin": 24, "diagramMarginX": 24, "diagramMarginY": 24, "mirrorActors": true, "actorFontSize": 17, "messageFontSize": 16, "noteFontSize": 16, "wrapPadding": 10}, "er": {"useMaxWidth": true, "nodeSpacing": 45, "rankSpacing": 55, "diagramPadding": 24, "entityPadding": 10, "fontSize": 17}, "themeCSS": ".nodeLabel p {margin:0;} .edgeLabel {font-size:15px;} .nodeLabel img {display:inline-block!important;width:36px!important;height:48px!important;}", "class": {"diagramPadding": 24, "nodeSpacing": 32, "rankSpacing": 45}}}%%
%% Role use case: one actor; solid primary task; dashed include/extend.
flowchart TB
 ACTOR["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='36' height='48'/><br/>DOCTOR"]
 subgraph SYS["MediFlow — DOCTOR"]
 D1(["Tra cứu hồ sơ khám"])
 D2(["Xem các lần khám"])
 D3(["Bắt đầu khám [V1]"])
 D4(["Kiểm tra quyền khám"])
 D5(["Ghi nhận khám bệnh"])
 D6(["Ghi nội dung thăm khám"])
 D7(["Thêm chẩn đoán"])
 D8(["Chỉ định xét nghiệm"])
 D9(["Chọn xét nghiệm và bệnh nhân"])
 D10(["Xem kết quả xét nghiệm"])
 D11(["Kê đơn thuốc"])
 D12(["Chọn thuốc và liều dùng"])
 D13(["Hoàn tất lượt khám [V1]"])
 D14(["Xác nhận chẩn đoán / hướng xử trí"])
 D15(["Hủy đơn thuốc"])
 D16(["Kiểm tra điều kiện hủy"])
 end
 ACTOR --> D1
 ACTOR --> D3
 ACTOR --> D5
 ACTOR --> D10
 ACTOR --> D11
 ACTOR --> D13
 D1 -.->|"«include»"| D2
 D3 -.->|"«include»"| D4
 D5 -.->|"«include»"| D6
 D7 -.->|"«extend» [E1]"| D5
 D8 -.->|"«extend» [E2]"| D5
 D8 -.->|"«include»"| D9
 D11 -.->|"«include»"| D12
 D13 -.->|"«include»"| D14
 D15 -.->|"«extend» [E3]"| D11
 D15 -.->|"«include»"| D16
 classDef actor fill:transparent,stroke:transparent,color:#0f172a;
 classDef main fill:#e0f2fe,stroke:#0284c7,color:#0f172a;
 classDef sub fill:#f0fdfa,stroke:#0f766e,color:#0f172a;
 class ACTOR actor;
 class D1,D3,D5,D10,D11,D13 main;
 class D2,D4,D6,D7,D8,D9,D12,D14,D15,D16 sub;
```

</details>

Vẫn kiểm tra clinical authority, bệnh nhân và context. [V1] cần bật care-finance-v2; start-exam cần exact EXAM clearance hoặc audited override đã hỗ trợ. AppointmentController còn cấp quyền xem/sửa lịch/trạng thái; source cũng cho phép xem báo cáo vận hành và hủy xét nghiệm. Bác sĩ không check-in thay điều dưỡng, cấp thuốc thay dược sĩ hay thu tiền thay thu ngân.

Điều kiện mở rộng: E1: Thêm chẩn đoán — bổ sung chẩn đoán; E2: Chỉ định xét nghiệm — cần xét nghiệm; E3: Hủy đơn thuốc — yêu cầu hủy đơn

Đối chiếu source: [AppointmentController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/AppointmentController.java) · [MedicalRecordController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/MedicalRecordController.java) · [ClinicalCareFinanceController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalCareFinanceController.java) · [ClinicalLabOrderController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalLabOrderController.java) · [LabController](../../../../backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java) · [PrescriptionController](../../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/PrescriptionController.java)

#### NURSE — Tác vụ điều dưỡng

![NURSE — Tác vụ điều dưỡng](assets/svg/32-vi.svg)

<details>
<summary>Mermaid · 32-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff", "fontSize": "18px"}, "flowchart": {"htmlLabels": true, "curve": "linear", "nodeSpacing": 30, "rankSpacing": 44, "padding": 24, "diagramPadding": 24, "wrappingWidth": 165, "subGraphTitleMargin": {"top": 10, "bottom": 32}}, "sequence": {"useMaxWidth": true, "wrap": true, "actorMargin": 65, "width": 210, "height": 55, "boxMargin": 14, "boxTextMargin": 8, "noteMargin": 16, "messageMargin": 24, "diagramMarginX": 24, "diagramMarginY": 24, "mirrorActors": true, "actorFontSize": 17, "messageFontSize": 16, "noteFontSize": 16, "wrapPadding": 10}, "er": {"useMaxWidth": true, "nodeSpacing": 45, "rankSpacing": 55, "diagramPadding": 24, "entityPadding": 10, "fontSize": 17}, "themeCSS": ".nodeLabel p {margin:0;} .edgeLabel {font-size:15px;} .nodeLabel img {display:inline-block!important;width:36px!important;height:48px!important;}", "class": {"diagramPadding": 24, "nodeSpacing": 32, "rankSpacing": 45}}}%%
%% Role use case: one actor; solid primary task; dashed include/extend.
flowchart TB
 ACTOR["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='36' height='48'/><br/>NURSE"]
 subgraph SYS["MediFlow — NURSE"]
 N1(["Tạo hồ sơ bệnh nhân"])
 N2(["Nhập thông tin bệnh nhân"])
 N3(["Cập nhật bệnh nhân"])
 N4(["Tra cứu bệnh nhân"])
 N5(["Tạo lịch hẹn"])
 N6(["Chọn bệnh nhân / bác sĩ / ngày"])
 N7(["Xem lịch hẹn"])
 N8(["Đọc chi tiết lịch"])
 N9(["Điều chỉnh lịch hẹn"])
 N10(["Kiểm tra lịch mới"])
 N11(["Tiếp nhận check-in [V1]"])
 N12(["Kiểm tra trạng thái lịch"])
 N13(["Tra cứu thông tin chăm sóc"])
 N14(["Xem hồ sơ khám / xét nghiệm"])
 N15(["Xem thông báo bệnh nhân"])
 end
 ACTOR --> N1
 ACTOR --> N3
 ACTOR --> N5
 ACTOR --> N7
 ACTOR --> N11
 ACTOR --> N13
 ACTOR --> N15
 N1 -.->|"«include»"| N2
 N3 -.->|"«include»"| N4
 N5 -.->|"«include»"| N6
 N7 -.->|"«include»"| N8
 N9 -.->|"«extend» [E1]"| N7
 N9 -.->|"«include»"| N10
 N11 -.->|"«include»"| N12
 N13 -.->|"«include»"| N14
 classDef actor fill:transparent,stroke:transparent,color:#0f172a;
 classDef main fill:#e0f2fe,stroke:#0284c7,color:#0f172a;
 classDef sub fill:#f0fdfa,stroke:#0f766e,color:#0f172a;
 class ACTOR actor;
 class N1,N3,N5,N7,N11,N13,N15 main;
 class N2,N4,N6,N8,N9,N10,N12,N14 sub;
```

</details>

[V1] check-in cần bật care-finance-v2 và không đồng nghĩa bắt đầu khám. NURSE xem danh sách/chi tiết xét nghiệm và danh sách thông báo theo bệnh nhân; không ghi chẩn đoán, kết quả xét nghiệm, đơn thuốc hay thanh toán; không có quyền mở endpoint chi tiết thông báo. Không tạo role RECEPTIONIST.

Điều kiện mở rộng: E1: Điều chỉnh lịch hẹn — cần đổi lịch

Đối chiếu source: [PatientController](../../../../backend/core-service/src/main/java/com/mediflow/patient/web/PatientController.java) · [AppointmentController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/AppointmentController.java) · [ClinicalCareFinanceController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalCareFinanceController.java) · [MedicalRecordController](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/MedicalRecordController.java) · [LabController](../../../../backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java) · [NotificationController](../../../../backend/notification-service/src/main/java/com/mediflow/notification/web/NotificationController.java)

#### LAB_TECH — Tác vụ kỹ thuật viên xét nghiệm

![LAB_TECH — Tác vụ kỹ thuật viên xét nghiệm](assets/svg/33-vi.svg)

<details>
<summary>Mermaid · 33-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff", "fontSize": "18px"}, "flowchart": {"htmlLabels": true, "curve": "linear", "nodeSpacing": 30, "rankSpacing": 44, "padding": 24, "diagramPadding": 24, "wrappingWidth": 165, "subGraphTitleMargin": {"top": 10, "bottom": 32}}, "sequence": {"useMaxWidth": true, "wrap": true, "actorMargin": 65, "width": 210, "height": 55, "boxMargin": 14, "boxTextMargin": 8, "noteMargin": 16, "messageMargin": 24, "diagramMarginX": 24, "diagramMarginY": 24, "mirrorActors": true, "actorFontSize": 17, "messageFontSize": 16, "noteFontSize": 16, "wrapPadding": 10}, "er": {"useMaxWidth": true, "nodeSpacing": 45, "rankSpacing": 55, "diagramPadding": 24, "entityPadding": 10, "fontSize": 17}, "themeCSS": ".nodeLabel p {margin:0;} .edgeLabel {font-size:15px;} .nodeLabel img {display:inline-block!important;width:36px!important;height:48px!important;}", "class": {"diagramPadding": 24, "nodeSpacing": 32, "rankSpacing": 45}}}%%
%% Role use case: one actor; solid primary task; dashed include/extend.
flowchart TB
 ACTOR["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='36' height='48'/><br/>LAB_TECH"]
 subgraph SYS["MediFlow — LAB_TECH"]
 L1(["Xem danh sách xét nghiệm"])
 L2(["Đọc trang xét nghiệm"])
 L3(["Lọc xét nghiệm chờ"])
 L4(["Bắt đầu xét nghiệm [V1 gate]"])
 L5(["Kiểm tra trạng thái xét nghiệm"])
 L6(["Kiểm tra LAB_TEST clearance [V1]"])
 L7(["Ghi kết quả xét nghiệm"])
 L8(["Nhập các giá trị kết quả"])
 L9(["Kiểm tra thời gian hoàn tất"])
 L10(["Hủy xét nghiệm"])
 L11(["Ghi lý do hủy"])
 L12(["Cập nhật trạng thái hợp lệ"])
 L13(["Kiểm tra chuyển trạng thái"])
 end
 ACTOR --> L1
 ACTOR --> L4
 ACTOR --> L7
 ACTOR --> L10
 ACTOR --> L12
 L1 -.->|"«include»"| L2
 L3 -.->|"«extend» [E1]"| L1
 L4 -.->|"«include»"| L5
 L4 -.->|"«include»"| L6
 L7 -.->|"«include»"| L8
 L7 -.->|"«include»"| L9
 L10 -.->|"«include»"| L11
 L12 -.->|"«include»"| L13
 classDef actor fill:transparent,stroke:transparent,color:#0f172a;
 classDef main fill:#e0f2fe,stroke:#0284c7,color:#0f172a;
 classDef sub fill:#f0fdfa,stroke:#0f766e,color:#0f172a;
 class ACTOR actor;
 class L1,L4,L7,L10,L12 main;
 class L2,L3,L5,L6,L8,L9,L11,L13 sub;
```

</details>

Start/cancel tuân theo lifecycle và feature availability; exact LAB_TEST clearance áp dụng cho protected action V1. LAB_TECH tra cứu xét nghiệm, ghi kết quả/trạng thái; không tạo chỉ định, kê đơn hay thu tiền. Lọc trạng thái là nhánh mở rộng tùy chọn của danh sách.

Điều kiện mở rộng: E1: Lọc xét nghiệm chờ — đã chọn bộ lọc

Đối chiếu source: [LabController](../../../../backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java)

#### PHARMACIST — Tác vụ dược sĩ

![PHARMACIST — Tác vụ dược sĩ](assets/svg/34-vi.svg)

<details>
<summary>Mermaid · 34-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff", "fontSize": "18px"}, "flowchart": {"htmlLabels": true, "curve": "linear", "nodeSpacing": 30, "rankSpacing": 44, "padding": 24, "diagramPadding": 24, "wrappingWidth": 165, "subGraphTitleMargin": {"top": 10, "bottom": 32}}, "sequence": {"useMaxWidth": true, "wrap": true, "actorMargin": 65, "width": 210, "height": 55, "boxMargin": 14, "boxTextMargin": 8, "noteMargin": 16, "messageMargin": 24, "diagramMarginX": 24, "diagramMarginY": 24, "mirrorActors": true, "actorFontSize": 17, "messageFontSize": 16, "noteFontSize": 16, "wrapPadding": 10}, "er": {"useMaxWidth": true, "nodeSpacing": 45, "rankSpacing": 55, "diagramPadding": 24, "entityPadding": 10, "fontSize": 17}, "themeCSS": ".nodeLabel p {margin:0;} .edgeLabel {font-size:15px;} .nodeLabel img {display:inline-block!important;width:36px!important;height:48px!important;}", "class": {"diagramPadding": 24, "nodeSpacing": 32, "rankSpacing": 45}}}%%
%% Role use case: one actor; solid primary task; dashed include/extend.
flowchart TB
 ACTOR["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='36' height='48'/><br/>PHARMACIST"]
 subgraph SYS["MediFlow — PHARMACIST"]
 P1(["Tra cứu thuốc"])
 P2(["Đọc danh mục thuốc"])
 P3(["Lọc theo từ khóa"])
 P4(["Thêm thuốc"])
 P5(["Nhập thông tin thuốc"])
 P6(["Điều chỉnh tồn kho"])
 P7(["Ghi số lượng và lý do"])
 P8(["Tra cứu đơn thuốc"])
 P9(["Xem các dòng thuốc"])
 P10(["Cấp thuốc theo đơn [V1]"])
 P11(["Kiểm tra clearance đúng đơn"])
 P12(["Kiểm tra tồn / giữ chỗ"])
 P13(["Ghi cấp thuốc và biến động tồn"])
 end
 ACTOR --> P1
 ACTOR --> P4
 ACTOR --> P6
 ACTOR --> P8
 ACTOR --> P10
 P1 -.->|"«include»"| P2
 P3 -.->|"«extend» [E1]"| P1
 P4 -.->|"«include»"| P5
 P6 -.->|"«include»"| P7
 P8 -.->|"«include»"| P9
 P10 -.->|"«include»"| P11
 P10 -.->|"«include»"| P12
 P10 -.->|"«include»"| P13
 classDef actor fill:transparent,stroke:transparent,color:#0f172a;
 classDef main fill:#e0f2fe,stroke:#0284c7,color:#0f172a;
 classDef sub fill:#f0fdfa,stroke:#0f766e,color:#0f172a;
 class ACTOR actor;
 class P1,P4,P6,P8,P10 main;
 class P2,P3,P5,P7,P9,P11,P12,P13 sub;
```

</details>

Lệnh cấp thuốc ngoại trú lấy actor đã ký và lifecycle V1; kiểm tra exact PRESCRIPTION clearance, tồn và reservation. Dược sĩ chủ động cấp thuốc, không tự cấp khi nhận sự kiện thanh toán. PHARMACIST không tạo/hủy đơn; hết hạn là xử lý lifecycle của hệ thống, không phải tác vụ người dùng.

Điều kiện mở rộng: E1: Lọc theo từ khóa — có từ khóa

Đối chiếu source: [DrugController](../../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/DrugController.java) · [PrescriptionController](../../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/web/PrescriptionController.java)

#### CASHIER — Tác vụ thu ngân

![CASHIER — Tác vụ thu ngân](assets/svg/35-vi.svg)

<details>
<summary>Mermaid · 35-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff", "fontSize": "18px"}, "flowchart": {"htmlLabels": true, "curve": "linear", "nodeSpacing": 30, "rankSpacing": 44, "padding": 24, "diagramPadding": 24, "wrappingWidth": 165, "subGraphTitleMargin": {"top": 10, "bottom": 32}}, "sequence": {"useMaxWidth": true, "wrap": true, "actorMargin": 65, "width": 210, "height": 55, "boxMargin": 14, "boxTextMargin": 8, "noteMargin": 16, "messageMargin": 24, "diagramMarginX": 24, "diagramMarginY": 24, "mirrorActors": true, "actorFontSize": 17, "messageFontSize": 16, "noteFontSize": 16, "wrapPadding": 10}, "er": {"useMaxWidth": true, "nodeSpacing": 45, "rankSpacing": 55, "diagramPadding": 24, "entityPadding": 10, "fontSize": 17}, "themeCSS": ".nodeLabel p {margin:0;} .edgeLabel {font-size:15px;} .nodeLabel img {display:inline-block!important;width:36px!important;height:48px!important;}", "class": {"diagramPadding": 24, "nodeSpacing": 32, "rankSpacing": 45}}}%%
%% Role use case: one actor; solid primary task; dashed include/extend.
flowchart TB
 ACTOR["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='36' height='48'/><br/>CASHIER"]
 subgraph SYS["MediFlow — CASHIER"]
 C1(["Tra cứu hóa đơn"])
 C2(["Xem hóa đơn bệnh nhân"])
 C3(["Tạo hóa đơn"])
 C4(["Kiểm tra các khoản thu"])
 C5(["Ghi thanh toán hóa đơn"])
 C6(["Kiểm tra lệnh thanh toán"])
 C7(["Tra cứu yêu cầu thu [V1]"])
 C8(["Xác định nguồn và đối tượng"])
 C9(["Ghi thanh toán ledger [V1]"])
 C10(["Kiểm tra số tiền và khóa lặp"])
 C11(["Trả biên nhận thanh toán"])
 C12(["Hoàn tiền giao dịch [V1]"])
 C13(["Kiểm tra số dư được hoàn"])
 C14(["Ghi số tiền và lý do"])
 end
 ACTOR --> C1
 ACTOR --> C3
 ACTOR --> C5
 ACTOR --> C7
 ACTOR --> C9
 C1 -.->|"«include»"| C2
 C3 -.->|"«include»"| C4
 C5 -.->|"«include»"| C6
 C7 -.->|"«include»"| C8
 C9 -.->|"«include»"| C10
 C9 -.->|"«include»"| C11
 C12 -.->|"«extend» [E1]"| C9
 C12 -.->|"«include»"| C13
 C12 -.->|"«include»"| C14
 classDef actor fill:transparent,stroke:transparent,color:#0f172a;
 classDef main fill:#e0f2fe,stroke:#0284c7,color:#0f172a;
 classDef sub fill:#f0fdfa,stroke:#0f766e,color:#0f172a;
 class ACTOR actor;
 class C1,C3,C5,C7,C9 main;
 class C2,C4,C6,C8,C10,C11,C12,C13,C14 sub;
```

</details>

Hóa đơn compatibility và ledger V1 là hai đường xử lý riêng, không phải các bước bắt buộc nối tiếp. [V1] phụ thuộc feature availability. Hoàn tiền là tác vụ mở rộng sau thanh toán gốc đủ điều kiện; có luồng con kiểm tra số dư, số tiền/lý do. Retry cùng khóa phải giữ intent. Không kích hoạt quyết toán nội trú/phẫu thuật.

Điều kiện mở rộng: E1: Hoàn tiền giao dịch [V1] — yêu cầu hoàn hợp lệ

Đối chiếu source: [InvoiceController](../../../../backend/billing-service/src/main/java/com/mediflow/billing/web/InvoiceController.java) · [PaymentRequestDiscoveryController](../../../../backend/billing-service/src/main/java/com/mediflow/billing/web/PaymentRequestDiscoveryController.java) · [LedgerPaymentController](../../../../backend/billing-service/src/main/java/com/mediflow/billing/web/LedgerPaymentController.java) · [LedgerRefundController](../../../../backend/billing-service/src/main/java/com/mediflow/billing/web/LedgerRefundController.java) · [LedgerRefundService](../../../../backend/billing-service/src/main/java/com/mediflow/billing/application/service/LedgerRefundService.java)

#### PATIENT — Tác vụ bệnh nhân

![PATIENT — Tác vụ bệnh nhân](assets/svg/36-vi.svg)

<details>
<summary>Mermaid · 36-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff", "fontSize": "18px"}, "flowchart": {"htmlLabels": true, "curve": "linear", "nodeSpacing": 30, "rankSpacing": 44, "padding": 24, "diagramPadding": 24, "wrappingWidth": 165, "subGraphTitleMargin": {"top": 10, "bottom": 32}}, "sequence": {"useMaxWidth": true, "wrap": true, "actorMargin": 65, "width": 210, "height": 55, "boxMargin": 14, "boxTextMargin": 8, "noteMargin": 16, "messageMargin": 24, "diagramMarginX": 24, "diagramMarginY": 24, "mirrorActors": true, "actorFontSize": 17, "messageFontSize": 16, "noteFontSize": 16, "wrapPadding": 10}, "er": {"useMaxWidth": true, "nodeSpacing": 45, "rankSpacing": 55, "diagramPadding": 24, "entityPadding": 10, "fontSize": 17}, "themeCSS": ".nodeLabel p {margin:0;} .edgeLabel {font-size:15px;} .nodeLabel img {display:inline-block!important;width:36px!important;height:48px!important;}", "class": {"diagramPadding": 24, "nodeSpacing": 32, "rankSpacing": 45}}}%%
%% Role use case: one actor; solid primary task; dashed include/extend.
flowchart TB
 ACTOR["<img src='data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSI3NCIgdmlld0JveD0iMCAwIDU2IDc0Ij48ZyBmaWxsPSJub25lIiBzdHJva2U9IiMwMzY5YTEiIHN0cm9rZS13aWR0aD0iMyIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48Y2lyY2xlIGN4PSIyOCIgY3k9IjExIiByPSI4Ii8+PHBhdGggZD0iTTI4IDIwdjI4TTggMzNoNDBNMjggNDhMMTIgNjlNMjggNDhsMTYgMjEiLz48L2c+PC9zdmc+' width='36' height='48'/><br/>PATIENT"]
 subgraph SYS["MediFlow — PATIENT"]
 T1(["Đăng nhập"])
 T2(["Nhập tên đăng nhập / mật khẩu"])
 T3(["Nhận access / refresh token"])
 T4(["Gia hạn phiên truy cập"])
 T5(["Kiểm tra định danh refresh"])
 T6(["Xem thông báo của mình"])
 T7(["Kiểm tra đúng bệnh nhân"])
 T8(["Xem danh sách thông báo"])
 T9(["Mở chi tiết thông báo"])
 T10(["Đọc nội dung thông báo"])
 end
 ACTOR --> T1
 ACTOR --> T4
 ACTOR --> T6
 T1 -.->|"«include»"| T2
 T1 -.->|"«include»"| T3
 T4 -.->|"«include»"| T5
 T6 -.->|"«include»"| T7
 T6 -.->|"«include»"| T8
 T9 -.->|"«extend» [E1]"| T6
 T9 -.->|"«include»"| T7
 T9 -.->|"«include»"| T10
 classDef actor fill:transparent,stroke:transparent,color:#0f172a;
 classDef main fill:#e0f2fe,stroke:#0284c7,color:#0f172a;
 classDef sub fill:#f0fdfa,stroke:#0f766e,color:#0f172a;
 class ACTOR actor;
 class T1,T4,T6 main;
 class T2,T3,T5,T7,T8,T9,T10 sub;
```

</details>

Hình phản ánh đúng phạm vi quyền hiện có. PATIENT chưa trực tiếp tạo/xem lịch hẹn, lấy hồ sơ bệnh nhân, kết quả xét nghiệm, đơn thuốc hay hóa đơn qua ma trận Gateway/controller hiện tại. Chưa có tự đăng ký hoặc sửa hồ sơ. Đây là khoảng trống tương lai, không vẽ thành chức năng đã có. Mở chi tiết thông báo vẫn kiểm tra ownership độc lập.

Điều kiện mở rộng: E1: Mở chi tiết thông báo — đã chọn thông báo

Đối chiếu source: [AuthController](../../../../backend/gateway/src/main/java/com/mediflow/gateway/auth/AuthController.java) · [NotificationController](../../../../backend/notification-service/src/main/java/com/mediflow/notification/web/NotificationController.java) · [RouteAuthorizationFilter](../../../../backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java)

<!-- role-usecases:end -->

### 1.3. Sequences Diagrams

### Đăng nhập và truy cập có kiểm quyền — SOURCE

![Đăng nhập và truy cập có kiểm quyền — SOURCE](assets/svg/06-vi.svg)

<details>
<summary>Mermaid · 06-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
sequenceDiagram
 autonumber
 actor User
 participant Client as Web / Flutter
 participant Gateway
 participant Core as Core Organization
 participant Service as Business context
 User->>Client: Credentials
 Client->>Gateway: Login request
 Gateway->>Core: Verify credentials
 Core->>Core: Account active / BCrypt / signed JWT
 Core-->>Client: Token response via Gateway
 Client->>Gateway: Authorized API request + bearer JWT
 Gateway->>Gateway: Verify token type / role / route
 alt Policy accepted
 Gateway->>Service: Forward authenticated request
 Service->>Service: Verify JWT / PreAuthorize / ownership / authority
 Service-->>Client: ApiResponse via Gateway
 else Unauthorized or forbidden
 Gateway-->>Client: 401 / 403
 end
```

</details>

### Check-in → thanh toán EXAM → khám — V1 GATED

![Check-in → thanh toán EXAM → khám — V1 GATED](assets/svg/07-vi.svg)

<details>
<summary>Mermaid · 07-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
sequenceDiagram
 autonumber
 actor Staff
 participant UI as Client via Gateway
 participant C as Clinical
 participant B as Billing
 participant Q as RabbitMQ
 Staff->>UI: Check in exact appointment
 UI->>C: Check-in command
 C->>C: Save appointment + durable outbox
 C-->>Q: Appointment fact with exact visit identity
 Q->>B: Consume selected V1 fact
 B->>B: Claim source / issue EXAM charge and request
 Staff->>UI: Record payment
 UI->>B: Complete request with idempotency key
 B->>B: Atomic payment + allocation + clearance + outbox
 B-->>Q: financial.clearance.granted
 Q->>C: Store exact EXAM clearance idempotently
 Staff->>UI: Start examination
 UI->>C: Start exact appointment
 C->>C: Recheck authority / target / expiry
 alt Clearance missing or provider unavailable
 C-->>UI: Reject protected action
 else Valid authority
 C->>C: Create or reuse appointment-backed record atomically
 C-->>UI: Open medical record
 end
```

</details>

### Chỉ định và trả kết quả xét nghiệm — V1 GATED

![Chỉ định và trả kết quả xét nghiệm — V1 GATED](assets/svg/08-vi.svg)

<details>
<summary>Mermaid · 08-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
sequenceDiagram
 autonumber
 actor Doctor
 actor Tech as LAB_TECH
 participant C as Clinical
 participant L as Lab
 participant B as Billing
 participant Q as RabbitMQ
 participant S as Support
 Doctor->>C: Explicit order in authorized record
 C->>C: Persist explicit clinical_lab_order
 C-->>Doctor: Source order identity
 Doctor->>L: Create test via Gateway with exact source_order_id
 L->>C: REST lookup /lab-orders/id/context with authenticated actor
 C-->>L: Exact persisted order and authority context
 L->>L: Validate context and dedupe source order / persist test + outbox
 L-->>Q: lab.request.created
 Q->>B: Issue LAB_TEST charge and request
 Note over B,L: Cashier pays exact request, clearance targets exact testId
 B-->>Q: financial.clearance.granted
 Q->>L: Persist target clearance
 Tech->>L: Start protected test through Gateway
 L->>L: Verify scope / target / expiry
 Tech->>L: Submit nonempty results and valid performed date
 L->>L: Complete once + resultVersion + outbox
 L-->>Q: lab.result.created
 Q->>C: Atomic inbox and attached result
 Q->>S: Independent report / notification projections
```

</details>

### Thanh toán và cấp thuốc — V0 compatibility saga

![Thanh toán và cấp thuốc — V0 compatibility saga](assets/svg/09-vi.svg)

<details>
<summary>Mermaid · 09-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
sequenceDiagram
 autonumber
 actor Cashier
 participant B as Billing
 participant Q as RabbitMQ
 participant P as Pharmacy
 participant S as Support
 Cashier->>B: Pay prescription invoice via Gateway
 B->>B: Save payment state and outbox atomically
 B-->>Q: payment.completed V0
 Q->>P: Durable delivery / possible redelivery
 P->>P: Claim receipt, lock prescription / reservations / drugs
 alt All dispense rules pass
 P->>P: Atomic stock decrement + fulfilled states + outbox
 P-->>Q: prescription.filled
 Q->>B: Complete saga once
 else Terminal reservation / stock / lifecycle failure
 P->>P: Persist terminal failure and durable fact
 P-->>Q: prescription.dispense.failed
 Q->>B: Apply V0 compensation once
 end
 Q->>S: Project selected facts independently
 Note over B,P: Redelivery returns committed outcome, no second stock effect
 Note over B,S: V0 reversal is not proof of completed bank refund
```

</details>
V1 dispense là luồng khác: payment tạo receipt/clearance; PHARMACIST gọi protected action với exact PRESCRIPTION clearance và actor xác thực. Không vẽ V1 như auto-dispense từ payment event. Các sequence V1 là hành vi source có gate, không xác nhận toàn luồng đã active.

### 1.4. Class Diagrams

Các sơ đồ rút gọn từ class thật; thuộc tính/method chọn lọc, không liệt kê getter/constructor. Quan hệ xuyên context không phải Java entity association.

### Class diagram Clinical và Lab

![Class diagram Clinical và Lab](assets/svg/10-vi.svg)

<details>
<summary>Mermaid · 10-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
classDiagram
 class Appointment {
 -UUID appointmentId
 -UUID patientId
 -AppointmentStatus status
 -short careContractVersion
 +checkIn(priceCode, at)
 +startExam(clearanceId, overrideId, at)
 +complete(at)
 }
 class MedicalRecord {
 -UUID recordId
 -UUID appointmentId
 -MedicalRecordStatus status
 -RecordDisposition disposition
 +openForAppointment()
 }
 class Diagnosis {
 -UUID diagnosisId
 -String diagnosisName
 -String icdCode
 }
 class LabTest {
 -UUID testId
 -UUID recordId
 -int resultVersion
 +start(clearance, override, at)
 +recordResults(results, conclusion, performedDate)
 }
 class LabResult {
 -UUID resultId
 -String indicator
 -String value
 -String unit
 }
 Appointment "0..1" --> "0..1" MedicalRecord : appointmentId
 MedicalRecord "1" *-- "0..*" Diagnosis
 LabTest "1" *-- "0..*" LabResult
 LabTest ..> MedicalRecord : recordId UUID only across contexts
```

</details>

### Class diagram Pharmacy và Billing

![Class diagram Pharmacy và Billing](assets/svg/11-vi.svg)

<details>
<summary>Mermaid · 11-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
classDiagram
 class Drug {
 -UUID drugId
 -BigDecimal price
 -int stockQuantity
 -LocalDate expiryDate
 }
 class Prescription {
 -UUID prescriptionId
 -BigDecimal totalAmount
 -PrescriptionStatus status
 +computeTotal()
 +cancel(actorId, reason, now)
 +markFulfilled(now)
 }
 class PrescriptionLine {
 -UUID drugId
 -int quantity
 -BigDecimal unitPrice
 -BigDecimal lineTotal
 }
 class StockReservation {
 -UUID prescriptionId
 -UUID drugId
 -int quantity
 -Instant expiresAt
 }
 class DispenseSlip {
 -UUID dispenseId
 -UUID prescriptionId
 }
 class Invoice {
 -UUID invoiceId
 -BigDecimal totalAmount
 -SagaStatus sagaStatus
 +pay(method, timestamp)
 +transitionSaga(next)
 +refund()
 }
 class LedgerPaymentService {
 +complete(requestId, command, actorAccountId, correlationId)
 }
 class LedgerPaymentRepositoryPort {
 <<interface>>
 +lockIdempotencyKey(key)
 +lockRequest(requestId)
 +allocate(payment)
 }
 class LedgerEventPort {
 <<interface>>
 +appendForOutpatientRequest()
 }
 Prescription "1" *-- "1..*" PrescriptionLine
 PrescriptionLine "*" --> "1" Drug
 Prescription "1" --> "*" StockReservation
 Prescription "1" --> "1" DispenseSlip
 Invoice ..> Prescription : prescriptionId UUID only
 LedgerPaymentService --> LedgerPaymentRepositoryPort
 LedgerPaymentService --> LedgerEventPort
```

</details>

## 2. Data Flow Diagram

DFD biểu diễn dữ liệu nghiệp vụ, không dùng nó để suy ra endpoint hoặc transaction xuyên service. P0 ở mức context được phân rã thành P1–P6 ở Level 1; tất cả giao tiếp actor đều đi qua client/Gateway về mặt triển khai.

### DFD Level 0 — ranh giới hệ thống

![DFD Level 0 — ranh giới hệ thống](assets/svg/12-vi.svg)

<details>
<summary>Mermaid · 12-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart LR
 PA["PATIENT"] -->|định danh / lịch hẹn| P0(("P0 MediFlow"))
 P0 -->|lịch / kết quả / thông báo| PA
 ST["DOCTOR / NURSE / LAB_TECH"] -->|hồ sơ / chỉ định / kết quả| P0
 P0 -->|trạng thái khám / clearance| ST
 PC["PHARMACIST / CASHIER"] -->|thu tiền / cấp thuốc| P0
 P0 -->|receipt / trạng thái tồn| PC
 MA["ADMIN / MANAGER"] -->|cấu hình / phạm vi báo cáo| P0
 P0 -->|audit / báo cáo có coverage| MA
```

</details>

### DFD Level 1 — sáu tiến trình và tám store

![DFD Level 1 — sáu tiến trình và tám store](assets/svg/13-vi.svg)

<details>
<summary>Mermaid · 13-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart TB
 U["Authorized actors / clients"] -->|identity data| P1(("P1 Core"))
 U -->|appointment / examination / order| P2(("P2 Clinical"))
 U -->|test lifecycle / result data| P3(("P3 Lab"))
 U -->|prescription / stock / dispensing| P4(("P4 Pharmacy"))
 U -->|payment / adjustment commands| P5(("P5 Billing"))
 U -->|report query / notification read| P6(("P6 Support"))
 P1 <-->|account / organization data| D1[(D1 Organization)]
 P1 <-->|patient identity| D2[(D2 Patient)]
 P2 <-->|clinical state| D3[(D3 Clinical)]
 P3 <-->|tests / results| D4[(D4 Lab)]
 P4 <-->|stock / prescriptions| D5[(D5 Pharmacy)]
 P5 <-->|charges / payments / clearance| D6[(D6 Billing)]
 P6 <-->|report projections| D7[(D7 Report)]
 P6 <-->|notification history| D8[(D8 Notification)]
 P2 & P3 & P4 & P5 -->|committed facts| BUS["RabbitMQ — integration transport"]
 BUS -->|targeted clearance / results / fill outcomes| P2 & P3 & P4 & P5
 BUS -->|selected report / notification facts| P6
 P1 -->|authoritative identity lookup response| P2 & P3 & P4
 P2 & P3 & P4 & P5 & P6 -->|authorized result / status / receipt| U
```

</details>

### DFD Level 2 — ghi tiền và cấp quyền dịch vụ V1

![DFD Level 2 — ghi tiền và cấp quyền dịch vụ V1](assets/svg/14-vi.svg)

<details>
<summary>Mermaid · 14-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart LR
 IN["Payment command + signed actor"] --> P51(("P5.1 Validate intent"))
 P51 --> P52(("P5.2 Lock key / account / request"))
 D6[(Billing store)] -->|request / previous receipt / totals| P52
 P52 -->|same key / same intent| OLD["Original persisted receipt"]
 P52 -->|new admissible payment| P53(("P5.3 Allocate payment"))
 P53 --> P54(("P5.4 Update request and grant if fully paid"))
 P54 -->|single local transaction| D6
 P54 -->|payment / allocation / clearance / outbox| D6
 D6 -->|unpublished durable outbox row| P55(("P5.5 Publish"))
 P55 --> MQ["RabbitMQ"]
 MQ --> P56(("P5.6 Consumer inbox + exact-target authority"))
 P56 --> OWN[(Clinical / Lab / Pharmacy own store)]
```

</details>

## 3. Entity Relationship Diagram

ERD dưới đây là lát cắt vật lý của mỗi database, trích từ migration. Dấu chân chim biểu thị FK local, không đường nối giả giữa các DB. Trường UUID có tên đối tượng ở context khác là logical reference. Mỗi sơ đồ chọn nhóm bảng cốt lõi; inventory ở mục 4 bao phủ toàn bộ CREATE TABLE của tám context, kể cả nền tảng mở rộng đang gated/deferred.

### ERD — Organization

![ERD — Organization](assets/svg/15-vi.svg)

<details>
<summary>Mermaid · 15-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
erDiagram
 department {
  uuid department_id PK
  uuid department_head_id
 }
 staff {
  uuid staff_id PK
  uuid department_id
  varchar status
 }
 account {
  uuid account_id PK
  uuid staff_id
  uuid patient_id
 }
 room {
  uuid room_id PK
  uuid department_id
 }
 staff |o--o{ department : "department_head_id"
 department ||--o{ staff : "department_id"
 staff |o--o{ account : "staff_id"
 department ||--o{ room : "department_id"
```

</details>

### ERD — Patient

![ERD — Patient](assets/svg/16-vi.svg)

<details>
<summary>Mermaid · 16-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
erDiagram
 patient {
  uuid patient_id PK
 }
```

</details>

### ERD — Clinical

![ERD — Clinical](assets/svg/17-vi.svg)

<details>
<summary>Mermaid · 17-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
erDiagram
 appointment {
  uuid appointment_id PK
  uuid patient_id
  uuid doctor_id
  uuid department_id
  varchar status
  smallint care_contract_version
  uuid exam_clearance_id
  uuid emergency_override_id
 }
 medical_record {
  uuid record_id PK
  uuid patient_id
  uuid doctor_id
  uuid department_id
  uuid appointment_id FK
  varchar status
 }
 diagnosis {
  uuid diagnosis_id PK
  uuid record_id FK
 }
 clinical_lab_order {
  uuid source_order_id PK
  uuid record_id FK
  uuid patient_id
  uuid requesting_doctor_id
  uuid requesting_department_id
  varchar care_episode_type
  uuid care_episode_id FK
 }
 exam_clearance {
  uuid clearance_id PK
  uuid event_id
  uuid invoice_id
  uuid account_id
  uuid appointment_id
  uuid record_id
  uuid patient_id
  varchar care_episode_type
  uuid care_episode_id
  numeric amount
 }
 attached_result {
  uuid record_id PK, FK
  uuid reference_id PK
 }
 appointment |o--o| medical_record : "appointment_id"
 medical_record ||--o{ diagnosis : "record_id"
 medical_record ||--o{ clinical_lab_order : "record_id"
 appointment ||--o{ clinical_lab_order : "care_episode_id"
 medical_record ||--o{ attached_result : "record_id"
```

</details>

### ERD — Lab

![ERD — Lab](assets/svg/18-vi.svg)

<details>
<summary>Mermaid · 18-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
erDiagram
 lab_test {
  uuid test_id PK
  uuid record_id
  uuid patient_id
  uuid requesting_department_id
  varchar status
  smallint care_contract_version
  varchar care_episode_type
  uuid care_episode_id
  uuid source_order_id
  uuid clearance_id
 }
 lab_result {
  uuid result_id PK
  uuid test_id FK
 }
 lab_financial_clearance {
  uuid clearance_target_id PK
  uuid clearance_id
  uuid event_id
  uuid invoice_id
  uuid account_id
  uuid test_id FK
  uuid patient_id
  varchar care_episode_type
  uuid care_episode_id
  numeric amount
 }
 lab_test ||--o{ lab_result : "test_id"
 lab_test ||--o{ lab_financial_clearance : "test_id"
```

</details>

### ERD — Pharmacy

![ERD — Pharmacy](assets/svg/19-vi.svg)

<details>
<summary>Mermaid · 19-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
erDiagram
 drug {
  uuid drug_id PK
  decimal price
  int stock_quantity
 }
 prescription {
  uuid prescription_id PK
  uuid record_id
  uuid patient_id
  uuid doctor_id
  uuid department_id
  decimal total_amount
  varchar status
  smallint care_contract_version
  varchar care_episode_type
  uuid care_episode_id
 }
 prescription_line {
  uuid line_id PK
  uuid prescription_id FK
  uuid drug_id FK
  int quantity
 }
 stock_reservation {
  uuid reservation_id PK
  uuid drug_id FK
  uuid prescription_id FK
  int quantity
  varchar status
 }
 dispense_slip {
  uuid dispense_id PK
  uuid prescription_id FK
  varchar status
 }
 prescription ||--o{ prescription_line : "prescription_id"
 drug ||--o{ prescription_line : "drug_id"
 drug ||--o{ stock_reservation : "drug_id"
 prescription ||--o{ stock_reservation : "prescription_id"
 prescription ||--o| dispense_slip : "prescription_id"
```

</details>

### ERD — Billing

![ERD — Billing](assets/svg/20-vi.svg)

<details>
<summary>Mermaid · 20-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
erDiagram
 billing_account {
  uuid account_id PK
  uuid patient_id
  uuid department_id
  varchar care_episode_type
  uuid care_episode_id
  varchar status
 }
 charge {
  uuid charge_id PK
  uuid account_id FK
  uuid patient_id
  uuid department_id
  uuid source_id
  numeric quantity
  varchar status
  uuid reconciled_result_id
 }
 payment_request {
  uuid payment_request_id PK
  uuid invoice_id
  uuid account_id FK
  varchar status
  decimal requested_amount
 }
 payment_request_charge {
  uuid payment_request_id PK, FK
  uuid charge_id PK, FK
  decimal requested_amount
 }
 payment_transaction {
  uuid transaction_id PK
  uuid account_id FK
  uuid payment_request_id FK
  varchar status
  decimal amount
  uuid original_transaction_id FK
  uuid actor_account_id
 }
 payment_allocation {
  uuid allocation_id PK
  uuid transaction_id FK
  uuid charge_id FK
  decimal amount
 }
 financial_clearance {
  uuid clearance_id PK
  uuid account_id FK
  uuid payment_request_id FK
  uuid invoice_id
  uuid patient_id
  varchar care_episode_type
  uuid care_episode_id
  uuid appointment_id
  uuid record_id
  uuid prescription_id
 }
 billing_account ||--o{ charge : "account_id"
 billing_account ||--o{ payment_request : "account_id"
 payment_request ||--o{ payment_request_charge : "payment_request_id"
 charge ||--o{ payment_request_charge : "charge_id"
 billing_account ||--o{ payment_transaction : "account_id"
 payment_request |o--o{ payment_transaction : "payment_request_id"
 payment_transaction |o--o{ payment_transaction : "original_transaction_id"
 payment_transaction ||--o{ payment_allocation : "transaction_id"
 charge ||--o{ payment_allocation : "charge_id"
 billing_account ||--o{ financial_clearance : "account_id"
 payment_request ||--o| financial_clearance : "payment_request_id"
```

</details>

### ERD — Notification

![ERD — Notification](assets/svg/21-vi.svg)

<details>
<summary>Mermaid · 21-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
erDiagram
 notification {
  uuid notification_id PK
  uuid patient_id
  varchar status
  uuid source_event_id
  uuid source_id
  varchar correlation_id
 }
 processed_event {
  uuid event_id PK
 }
```

</details>

### ERD — Report

![ERD — Report](assets/svg/22-vi.svg)

<details>
<summary>Mermaid · 22-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
erDiagram
 daily_visit_report {
  uuid report_id PK
  date report_date
  uuid department_id
 }
 monthly_revenue_report {
  uuid report_id PK
  uuid department_id
 }
 drug_statistic {
  uuid statistic_id PK
  uuid drug_id
  date report_date
  uuid department_id
 }
 payment_contribution {
  uuid invoice_id PK
  uuid completed_event_id
  uuid failed_event_id
  uuid department_id
  decimal amount
  varchar status
 }
 processed_event {
  uuid event_id PK
 }
```

</details>
Cardinality bổ sung từ unique index: một appointment có tối đa một medical_record (`uq_medical_record_appointment`); một prescription có tối đa một dispense_slip; một financial_clearance ứng với một payment_request. Report là read model có UUID nguồn, không FK về Billing/Pharmacy. Trường `invoice_id` trong PAYMENT_REQUEST không được suy thành FK tới bảng INVOICE V0.

| Tham chiếu logic | Authority | Cách trao đổi |
|---|---|---|
| `patient_id` | Core / Patient | REST identity lookup; event payload UUID |
| `doctor_id / department_id` | Core / Organization | REST authority lookup; signed actor context |
| `record_id / care_episode_id` | Clinical | Exact outpatient context contract / event |
| `test_id / source_order_id` | Lab / Clinical | Explicit order / result contracts |
| `prescription_id` | Pharmacy | Prescription facts / target lookup |
| `clearance_id / payment_request_id` | Billing | Purpose + exact target + expiry; event/lookup |

## 4. Table Design

### 4.1. Database Tables

Tên bảng không có dấu ngoặc kép trong PostgreSQL được chuẩn hóa chữ thường, dù migration viết HOA. Inventory sau đọc các migration theo số V, ghi migration tạo bảng và các migration ALTER TABLE liên quan. Đây là inventory source, không phải ảnh chụp live DB: bảng chưa migrate hoặc feature tắt vẫn có thể xuất hiện. Không đưa `flyway_schema_history` do Flyway tạo tự động vào tổng số bảng nghiệp vụ.

| Context | Database | Số bảng khai báo | Migration |
|---|---|---:|---|
| Organization | `mediflow_organization` | 7 | 4 |
| Patient | `mediflow_patient` | 1 | 1 |
| Clinical | `mediflow_clinical` | 12 | 7 |
| Lab | `mediflow_lab` | 8 | 3 |
| Pharmacy | `mediflow_pharmacy` | 17 | 20 |
| Billing | `mediflow_billing` | 42 | 23 |
| Notification | `mediflow_notification` | 7 | 4 |
| Report | `mediflow_report` | 58 | 24 |

**Không có nghĩa mọi bảng đều active trong MVP.** Clinical có admission_referral giữ lại; Billing/Pharmacy/Report/Notification có các cấu trúc nâng cao hoặc nội trú/phẫu thuật. Inventory ghi tồn tại vật lý trong source; activate theo profile, không theo tên bảng.

#### Organization — `mediflow_organization`

| Table | PK / định danh | CREATE migration | ALTER migrations |
|---|---|---|---|
| `department` | `department_id` | [V1__init.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V1__init.sql) | [V1](../../../../backend/core-service/src/main/resources/db/organization-migration/V1__init.sql) |
| `staff` | `staff_id` | [V1__init.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V1__init.sql) | — |
| `account` | `account_id` | [V1__init.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V1__init.sql) | [V2](../../../../backend/core-service/src/main/resources/db/organization-migration/V2__account_patient_identity.sql) |
| `room` | `room_id` | [V3__room_lookup.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V3__room_lookup.sql) | — |
| `operating_room` | `room_id` | [V4__surgery_reference_authority.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V4__surgery_reference_authority.sql) | — |
| `surgical_capability` | `staff_id, team_role` | [V4__surgery_reference_authority.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V4__surgery_reference_authority.sql) | — |
| `surgery_authority_outbox` | `event_id` | [V4__surgery_reference_authority.sql](../../../../backend/core-service/src/main/resources/db/organization-migration/V4__surgery_reference_authority.sql) | — |

#### Patient — `mediflow_patient`

| Table | PK / định danh | CREATE migration | ALTER migrations |
|---|---|---|---|
| `patient` | `patient_id` | [V1__init_patient.sql](../../../../backend/core-service/src/main/resources/db/patient-migration/V1__init_patient.sql) | — |

#### Clinical — `mediflow_clinical`

| Table | PK / định danh | CREATE migration | ALTER migrations |
|---|---|---|---|
| `appointment` | `appointment_id` | [V1__init.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V1__init.sql) | [V4](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) |
| `medical_record` | `record_id` | [V1__init.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V1__init.sql) | [V4](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) |
| `diagnosis` | `diagnosis_id` | [V1__init.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V1__init.sql) | — |
| `attached_result` | `record_id, type, reference_id` | [V2__attached_result.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V2__attached_result.sql) | — |
| `processed_event` | `event_id` | [V3__processed_event.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V3__processed_event.sql) | — |
| `exam_clearance` | `clearance_id` | [V4__care_finance_v2.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) | — |
| `clinical_emergency_override` | `override_id` | [V4__care_finance_v2.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) | — |
| `admission_referral` | `admission_request_id` | [V4__care_finance_v2.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) | — |
| `clinical_outbox_event` | `event_id` | [V4__care_finance_v2.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V4__care_finance_v2.sql) | [V5](../../../../backend/clinical-service/src/main/resources/db/migration/V5__clinical_outbox_delivery_state.sql) |
| `clinical_lab_order` | `source_order_id` | [V6__clinical_lab_orders.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V6__clinical_lab_orders.sql) | — |
| `consumer_inbox_delivery` | `consumer_name, event_id` | [V7__consumer_inbox.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V7__consumer_inbox.sql) | — |
| `consumer_inbox_operation` | `consumer_name, operation_key` | [V7__consumer_inbox.sql](../../../../backend/clinical-service/src/main/resources/db/migration/V7__consumer_inbox.sql) | — |

#### Lab — `mediflow_lab`

| Table | PK / định danh | CREATE migration | ALTER migrations |
|---|---|---|---|
| `lab_test` | `test_id` | [V1__init.sql](../../../../backend/lab-service/src/main/resources/db/migration/V1__init.sql) | [V2](../../../../backend/lab-service/src/main/resources/db/migration/V2__care_finance_v2.sql) |
| `lab_result` | `result_id` | [V1__init.sql](../../../../backend/lab-service/src/main/resources/db/migration/V1__init.sql) | — |
| `processed_event` | `event_id` | [V1__init.sql](../../../../backend/lab-service/src/main/resources/db/migration/V1__init.sql) | — |
| `lab_financial_clearance` | `clearance_target_id` | [V2__care_finance_v2.sql](../../../../backend/lab-service/src/main/resources/db/migration/V2__care_finance_v2.sql) | — |
| `lab_emergency_override` | `override_id` | [V2__care_finance_v2.sql](../../../../backend/lab-service/src/main/resources/db/migration/V2__care_finance_v2.sql) | — |
| `lab_outbox_event` | `event_id` | [V2__care_finance_v2.sql](../../../../backend/lab-service/src/main/resources/db/migration/V2__care_finance_v2.sql) | — |
| `consumer_inbox_delivery` | `consumer_name, event_id` | [V3__consumer_inbox.sql](../../../../backend/lab-service/src/main/resources/db/migration/V3__consumer_inbox.sql) | — |
| `consumer_inbox_operation` | `consumer_name, operation_key` | [V3__consumer_inbox.sql](../../../../backend/lab-service/src/main/resources/db/migration/V3__consumer_inbox.sql) | — |

#### Pharmacy — `mediflow_pharmacy`

| Table | PK / định danh | CREATE migration | ALTER migrations |
|---|---|---|---|
| `drug` | `drug_id` | [V1__init.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | — |
| `prescription` | `prescription_id` | [V1__init.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | [V4](../../../../backend/pharmacy-service/src/main/resources/db/migration/V4__prescription_lifecycle_and_reservation_audit.sql), [V14](../../../../backend/pharmacy-service/src/main/resources/db/migration/V14__prescription_care_context.sql), [V17](../../../../backend/pharmacy-service/src/main/resources/db/migration/V17__held_prescription_care_events.sql) |
| `prescription_line` | `line_id` | [V1__init.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | [V17](../../../../backend/pharmacy-service/src/main/resources/db/migration/V17__held_prescription_care_events.sql) |
| `dispense_slip` | `dispense_id` | [V1__init.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | [V4](../../../../backend/pharmacy-service/src/main/resources/db/migration/V4__prescription_lifecycle_and_reservation_audit.sql), [V13](../../../../backend/pharmacy-service/src/main/resources/db/migration/V13__dispense_actor_audit_type.sql), [V17](../../../../backend/pharmacy-service/src/main/resources/db/migration/V17__held_prescription_care_events.sql) |
| `processed_event` | `event_id` | [V1__init.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V1__init.sql) | — |
| `stock_reservation` | `reservation_id` | [V2__stock_reservation.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V2__stock_reservation.sql) | [V3](../../../../backend/pharmacy-service/src/main/resources/db/migration/V3__unique_prescription_drug_reservation.sql), [V4](../../../../backend/pharmacy-service/src/main/resources/db/migration/V4__prescription_lifecycle_and_reservation_audit.sql) |
| `pharmacy_event_outbox` | `event_id` | [V5__pharmacy_event_outbox.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V5__pharmacy_event_outbox.sql) | [V7](../../../../backend/pharmacy-service/src/main/resources/db/migration/V7__pharmacy_event_outbox_lease.sql), [V9](../../../../backend/pharmacy-service/src/main/resources/db/migration/V9__pharmacy_event_outbox_quarantine.sql), [V12](../../../../backend/pharmacy-service/src/main/resources/db/migration/V12__pharmacy_outbox_causal_order.sql), [V17](../../../../backend/pharmacy-service/src/main/resources/db/migration/V17__held_prescription_care_events.sql), [V20](../../../../backend/pharmacy-service/src/main/resources/db/migration/V20__selective_outpatient_delivery_cohort.sql) |
| `payment_receipt` | `receipt_id` | [V6__payment_receipt.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V6__payment_receipt.sql) | — |
| `stock_adjustment` | `adjustment_id` | [V8__stock_adjustment_audit.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V8__stock_adjustment_audit.sql) | — |
| `pharmacy_scheduler_lease` | `job_name` | [V10__pharmacy_scheduler_cursor_lease.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V10__pharmacy_scheduler_cursor_lease.sql) | [V11](../../../../backend/pharmacy-service/src/main/resources/db/migration/V11__pharmacy_scheduler_fencing_token.sql) |
| `admission_medication_context` | `admission_id` | [V15__admission_medication_context.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V15__admission_medication_context.sql) | [V19](../../../../backend/pharmacy-service/src/main/resources/db/migration/V19__admission_medical_discharge.sql) |
| `admission_medication_event` | `event_id` | [V15__admission_medication_context.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V15__admission_medication_context.sql) | — |
| `prescription_clearance_target` | `prescription_id` | [V16__prescription_clearance.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V16__prescription_clearance.sql) | — |
| `prescription_clearance` | `clearance_id` | [V16__prescription_clearance.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V16__prescription_clearance.sql) | — |
| `prescription_clearance_event` | `event_id` | [V16__prescription_clearance.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V16__prescription_clearance.sql) | — |
| `care_prescription_creation` | `command_id` | [V18__held_care_creation_receipt.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V18__held_care_creation_receipt.sql) | — |
| `pharmacy_outpatient_delivery_cohort` | `prescription_id` | [V20__selective_outpatient_delivery_cohort.sql](../../../../backend/pharmacy-service/src/main/resources/db/migration/V20__selective_outpatient_delivery_cohort.sql) | — |

#### Billing — `mediflow_billing`

| Table | PK / định danh | CREATE migration | ALTER migrations |
|---|---|---|---|
| `fee` | `fee_id` | [V1__init.sql](../../../../backend/billing-service/src/main/resources/db/migration/V1__init.sql) | — |
| `invoice` | `invoice_id` | [V1__init.sql](../../../../backend/billing-service/src/main/resources/db/migration/V1__init.sql) | [V3](../../../../backend/billing-service/src/main/resources/db/migration/V3__drop_invoice_dispense_id.sql) |
| `processed_event` | `event_id` | [V1__init.sql](../../../../backend/billing-service/src/main/resources/db/migration/V1__init.sql) | — |
| `lab_test_type` | `lab_id` | [V1__init.sql](../../../../backend/billing-service/src/main/resources/db/migration/V1__init.sql) | — |
| `billing_event_outbox` | `event_id` | [V2__billing_event_outbox.sql](../../../../backend/billing-service/src/main/resources/db/migration/V2__billing_event_outbox.sql) | [V5](../../../../backend/billing-service/src/main/resources/db/migration/V5__ledger_payment_authority.sql), [V6](../../../../backend/billing-service/src/main/resources/db/migration/V6__ledger_event_delivery_fence.sql), [V22](../../../../backend/billing-service/src/main/resources/db/migration/V22__outpatient_publication_cohort.sql) |
| `billing_account` | `account_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `charge` | `charge_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | [V7](../../../../backend/billing-service/src/main/resources/db/migration/V7__surgery_charge_reconciliation.sql), [V9](../../../../backend/billing-service/src/main/resources/db/migration/V9__surgery_planned_request_issuance.sql) |
| `payment_request` | `payment_request_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `payment_request_charge` | `payment_request_id, charge_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `payment_transaction` | `transaction_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | [V5](../../../../backend/billing-service/src/main/resources/db/migration/V5__ledger_payment_authority.sql) |
| `payment_allocation` | `allocation_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `financial_clearance` | `clearance_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `insurance_adjustment` | `adjustment_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `settlement` | `settlement_id` | [V4__care_finance_ledger.sql](../../../../backend/billing-service/src/main/resources/db/migration/V4__care_finance_ledger.sql) | — |
| `payment_request_target` | `payment_request_id` | [V5__ledger_payment_authority.sql](../../../../backend/billing-service/src/main/resources/db/migration/V5__ledger_payment_authority.sql) | — |
| `ledger_refund_evidence` | `refund_transaction_id` | [V8__ledger_refund_evidence.sql](../../../../backend/billing-service/src/main/resources/db/migration/V8__ledger_refund_evidence.sql) | — |
| `surgery_charge_delivery` | `event_id` | [V9__surgery_planned_request_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V9__surgery_planned_request_issuance.sql) | [V9](../../../../backend/billing-service/src/main/resources/db/migration/V9__surgery_planned_request_issuance.sql) |
| `surgery_charge_source` | `surgery_case_id` | [V9__surgery_planned_request_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V9__surgery_planned_request_issuance.sql) | [V11](../../../../backend/billing-service/src/main/resources/db/migration/V11__surgery_requested_at_exact_time.sql) |
| `surgery_charge_item` | `surgery_case_id,item_code` | [V9__surgery_planned_request_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V9__surgery_planned_request_issuance.sql) | — |
| `surgery_cancellation_source` | `surgery_case_id` | [V10__surgery_cancellation_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V10__surgery_cancellation_adjustment.sql) | — |
| `surgery_cancellation_delivery` | `event_id` | [V10__surgery_cancellation_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V10__surgery_cancellation_adjustment.sql) | — |
| `surgery_cancellation_refund_due` | `cancellation_id,original_transaction_id,charge_id` | [V10__surgery_cancellation_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V10__surgery_cancellation_adjustment.sql) | — |
| `lab_test_charge_delivery` | `event_id` | [V12__lab_test_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V12__lab_test_charge_issuance.sql) | [V12](../../../../backend/billing-service/src/main/resources/db/migration/V12__lab_test_charge_issuance.sql) |
| `lab_test_charge_source` | `lab_id` | [V12__lab_test_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V12__lab_test_charge_issuance.sql) | — |
| `admission_deposit_delivery` | `event_id` | [V13__admission_deposit_request_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V13__admission_deposit_request_issuance.sql) | [V13](../../../../backend/billing-service/src/main/resources/db/migration/V13__admission_deposit_request_issuance.sql) |
| `admission_deposit_source` | `admission_id` | [V13__admission_deposit_request_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V13__admission_deposit_request_issuance.sql) | — |
| `admission_reconciliation` | `settlement_id` | [V14__admission_reconciliation_snapshots.sql](../../../../backend/billing-service/src/main/resources/db/migration/V14__admission_reconciliation_snapshots.sql) | — |
| `admission_charge_close` | `admission_id` | [V15__admission_medical_charge_close.sql](../../../../backend/billing-service/src/main/resources/db/migration/V15__admission_medical_charge_close.sql) | — |
| `admission_charge_close_delivery` | `event_id` | [V15__admission_medical_charge_close.sql](../../../../backend/billing-service/src/main/resources/db/migration/V15__admission_medical_charge_close.sql) | — |
| `deposit_allocation_operation` | `allocation_operation_id` | [V16__admission_deposit_allocation.sql](../../../../backend/billing-service/src/main/resources/db/migration/V16__admission_deposit_allocation.sql) | — |
| `surgery_completion_source` | `surgery_case_id` | [V17__surgery_completion_proof.sql](../../../../backend/billing-service/src/main/resources/db/migration/V17__surgery_completion_proof.sql) | — |
| `surgery_completion_delivery` | `event_id` | [V17__surgery_completion_proof.sql](../../../../backend/billing-service/src/main/resources/db/migration/V17__surgery_completion_proof.sql) | — |
| `final_admission_settlement_operation` | `recognition_operation_id` | [V18__final_admission_settlement_operation.sql](../../../../backend/billing-service/src/main/resources/db/migration/V18__final_admission_settlement_operation.sql) | — |
| `prescription_charge_source` | `prescription_id` | [V19__prescription_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V19__prescription_charge_issuance.sql) | [V20](../../../../backend/billing-service/src/main/resources/db/migration/V20__prescription_terminal_adjustment.sql) |
| `prescription_charge_item` | `prescription_id,drug_id` | [V19__prescription_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V19__prescription_charge_issuance.sql) | — |
| `prescription_charge_delivery` | `event_id` | [V19__prescription_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V19__prescription_charge_issuance.sql) | — |
| `prescription_terminal_source` | `prescription_id` | [V20__prescription_terminal_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V20__prescription_terminal_adjustment.sql) | — |
| `prescription_terminal_delivery` | `event_id` | [V20__prescription_terminal_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V20__prescription_terminal_adjustment.sql) | — |
| `prescription_terminal_refund_due` | `prescription_id, original_transaction_id, charge_id` | [V20__prescription_terminal_adjustment.sql](../../../../backend/billing-service/src/main/resources/db/migration/V20__prescription_terminal_adjustment.sql) | — |
| `exam_charge_delivery` | `event_id` | [V21__exam_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V21__exam_charge_issuance.sql) | [V21](../../../../backend/billing-service/src/main/resources/db/migration/V21__exam_charge_issuance.sql) |
| `exam_charge_source` | `appointment_id` | [V21__exam_charge_issuance.sql](../../../../backend/billing-service/src/main/resources/db/migration/V21__exam_charge_issuance.sql) | — |
| `billing_outpatient_publication_cohort` | `payment_request_id` | [V22__outpatient_publication_cohort.sql](../../../../backend/billing-service/src/main/resources/db/migration/V22__outpatient_publication_cohort.sql) | — |

#### Notification — `mediflow_notification`

| Table | PK / định danh | CREATE migration | ALTER migrations |
|---|---|---|---|
| `notification` | `notification_id` | [V1__init.sql](../../../../backend/notification-service/src/main/resources/db/migration/V1__init.sql) | [V2](../../../../backend/notification-service/src/main/resources/db/migration/V2__care_receipt_delivery.sql) |
| `processed_event` | `event_id` | [V1__init.sql](../../../../backend/notification-service/src/main/resources/db/migration/V1__init.sql) | [V2](../../../../backend/notification-service/src/main/resources/db/migration/V2__care_receipt_delivery.sql) |
| `notification_event_outbox` | `event_id` | [V2__care_receipt_delivery.sql](../../../../backend/notification-service/src/main/resources/db/migration/V2__care_receipt_delivery.sql) | — |
| `surgery_notice_case` | `surgery_case_id` | [V3__surgery_reminder_evidence.sql](../../../../backend/notification-service/src/main/resources/db/migration/V3__surgery_reminder_evidence.sql) | — |
| `surgery_notice_source` | `event_type,source_id` | [V3__surgery_reminder_evidence.sql](../../../../backend/notification-service/src/main/resources/db/migration/V3__surgery_reminder_evidence.sql) | — |
| `surgery_notice_snapshot` | `snapshot_id` | [V3__surgery_reminder_evidence.sql](../../../../backend/notification-service/src/main/resources/db/migration/V3__surgery_reminder_evidence.sql) | — |
| `care_notification_source` | `event_type,source_id` | [V4__care_notification_source.sql](../../../../backend/notification-service/src/main/resources/db/migration/V4__care_notification_source.sql) | — |

#### Report — `mediflow_report`

| Table | PK / định danh | CREATE migration | ALTER migrations |
|---|---|---|---|
| `daily_visit_report` | `report_id` | [V1__init.sql](../../../../backend/report-service/src/main/resources/db/migration/V1__init.sql) | — |
| `monthly_revenue_report` | `report_id` | [V1__init.sql](../../../../backend/report-service/src/main/resources/db/migration/V1__init.sql) | — |
| `drug_statistic` | `statistic_id` | [V1__init.sql](../../../../backend/report-service/src/main/resources/db/migration/V1__init.sql) | — |
| `processed_event` | `event_id` | [V1__init.sql](../../../../backend/report-service/src/main/resources/db/migration/V1__init.sql) | — |
| `payment_contribution` | `invoice_id` | [V1__init.sql](../../../../backend/report-service/src/main/resources/db/migration/V1__init.sql) | [V2](../../../../backend/report-service/src/main/resources/db/migration/V2__enforce_pending_payment_scope.sql) |
| `financial_contribution` | `contribution_id` | [V6__care_finance_projections.sql](../../../../backend/report-service/src/main/resources/db/migration/V6__care_finance_projections.sql) | — |
| `daily_financial_report` | `report_id` | [V6__care_finance_projections.sql](../../../../backend/report-service/src/main/resources/db/migration/V6__care_finance_projections.sql) | — |
| `operational_contribution` | `contribution_id` | [V6__care_finance_projections.sql](../../../../backend/report-service/src/main/resources/db/migration/V6__care_finance_projections.sql) | [V7](../../../../backend/report-service/src/main/resources/db/migration/V7__operational_journal.sql) |
| `daily_operational_report` | `report_id` | [V6__care_finance_projections.sql](../../../../backend/report-service/src/main/resources/db/migration/V6__care_finance_projections.sql) | [V7](../../../../backend/report-service/src/main/resources/db/migration/V7__operational_journal.sql), [V21](../../../../backend/report-service/src/main/resources/db/migration/V21__medical_discharge_report_projection.sql) |
| `operational_event_journal` | `event_id` | [V7__operational_journal.sql](../../../../backend/report-service/src/main/resources/db/migration/V7__operational_journal.sql) | — |
| `operational_delivery` | `event_id, metric_type` | [V7__operational_journal.sql](../../../../backend/report-service/src/main/resources/db/migration/V7__operational_journal.sql) | — |
| `operational_replay_generation` | `generation_id` | [V8__isolated_operational_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V8__isolated_operational_replay.sql) | — |
| `operational_replay_input` | `generation_id, event_id` | [V8__isolated_operational_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V8__isolated_operational_replay.sql) | — |
| `operational_replay_contribution` | `generation_id, source_type, source_id, source_revision, metric_type` | [V8__isolated_operational_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V8__isolated_operational_replay.sql) | — |
| `operational_replay_scope` | `see DDL` | [V8__isolated_operational_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V8__isolated_operational_replay.sql) | — |
| `report_admission_target` | `admission_id` | [V9__pending_admission_report_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V9__pending_admission_report_evidence.sql) | — |
| `report_admission_delivery` | `event_id` | [V9__pending_admission_report_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V9__pending_admission_report_evidence.sql) | — |
| `report_admission_fact` | `admission_id, fact_type` | [V9__pending_admission_report_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V9__pending_admission_report_evidence.sql) | — |
| `operational_report_publication` | `report_kind` | [V10__operational_snapshot_publication.sql](../../../../backend/report-service/src/main/resources/db/migration/V10__operational_snapshot_publication.sql) | — |
| `operational_source_snapshot` | `source_type,source_id,source_revision,event_type` | [V11__operational_source_fingerprints.sql](../../../../backend/report-service/src/main/resources/db/migration/V11__operational_source_fingerprints.sql) | — |
| `report_cash_receipt` | `transaction_id` | [V12__offline_gross_cash_receipts.sql](../../../../backend/report-service/src/main/resources/db/migration/V12__offline_gross_cash_receipts.sql) | — |
| `report_cash_delivery` | `event_id` | [V12__offline_gross_cash_receipts.sql](../../../../backend/report-service/src/main/resources/db/migration/V12__offline_gross_cash_receipts.sql) | — |
| `report_gross_cash_daily` | `scope_id` | [V12__offline_gross_cash_receipts.sql](../../../../backend/report-service/src/main/resources/db/migration/V12__offline_gross_cash_receipts.sql) | — |
| `cash_replay_generation` | `generation_id` | [V13__isolated_cash_receipt_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V13__isolated_cash_receipt_replay.sql) | — |
| `cash_replay_input` | `generation_id,transaction_id` | [V13__isolated_cash_receipt_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V13__isolated_cash_receipt_replay.sql) | — |
| `cash_replay_receipt` | `generation_id,transaction_id` | [V13__isolated_cash_receipt_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V13__isolated_cash_receipt_replay.sql) | — |
| `cash_replay_scope` | `see DDL` | [V13__isolated_cash_receipt_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V13__isolated_cash_receipt_replay.sql) | — |
| `report_cash_refund` | `refund_transaction_id` | [V15__completed_cash_refund_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V15__completed_cash_refund_evidence.sql) | — |
| `report_cash_refund_delivery` | `event_id` | [V15__completed_cash_refund_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V15__completed_cash_refund_evidence.sql) | — |
| `report_refund_cash_daily` | `scope_id` | [V15__completed_cash_refund_evidence.sql](../../../../backend/report-service/src/main/resources/db/migration/V15__completed_cash_refund_evidence.sql) | — |
| `prescription_fill_receipt` | `prescription_id` | [V16__prescription_fill_source_receipt.sql](../../../../backend/report-service/src/main/resources/db/migration/V16__prescription_fill_source_receipt.sql) | — |
| `refund_replay_generation` | `generation_id` | [V17__isolated_cash_refund_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V17__isolated_cash_refund_replay.sql) | — |
| `refund_replay_input` | `generation_id,refund_transaction_id` | [V17__isolated_cash_refund_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V17__isolated_cash_refund_replay.sql) | — |
| `refund_replay_fact` | `generation_id,refund_transaction_id` | [V17__isolated_cash_refund_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V17__isolated_cash_refund_replay.sql) | — |
| `refund_replay_scope` | `see DDL` | [V17__isolated_cash_refund_replay.sql](../../../../backend/report-service/src/main/resources/db/migration/V17__isolated_cash_refund_replay.sql) | — |
| `report_settlement_snapshot` | `settlement_id` | [V19__settlement_receivable_stock.sql](../../../../backend/report-service/src/main/resources/db/migration/V19__settlement_receivable_stock.sql) | — |
| `report_settlement_delivery` | `event_id` | [V19__settlement_receivable_stock.sql](../../../../backend/report-service/src/main/resources/db/migration/V19__settlement_receivable_stock.sql) | — |
| `report_receivable_current` | `account_id` | [V19__settlement_receivable_stock.sql](../../../../backend/report-service/src/main/resources/db/migration/V19__settlement_receivable_stock.sql) | — |
| `report_receivable_stock` | `see DDL` | [V19__settlement_receivable_stock.sql](../../../../backend/report-service/src/main/resources/db/migration/V19__settlement_receivable_stock.sql) | — |
| `financial_report_generation` | `generation_id` | [V20__financial_snapshot_read_boundary.sql](../../../../backend/report-service/src/main/resources/db/migration/V20__financial_snapshot_read_boundary.sql) | — |
| `financial_report_day` | `see DDL` | [V20__financial_snapshot_read_boundary.sql](../../../../backend/report-service/src/main/resources/db/migration/V20__financial_snapshot_read_boundary.sql) | — |
| `financial_report_publication` | `currency,report_zone` | [V20__financial_snapshot_read_boundary.sql](../../../../backend/report-service/src/main/resources/db/migration/V20__financial_snapshot_read_boundary.sql) | — |
| `report_medical_discharge` | `admission_id` | [V21__medical_discharge_report_projection.sql](../../../../backend/report-service/src/main/resources/db/migration/V21__medical_discharge_report_projection.sql) | — |
| `report_medical_discharge_delivery` | `event_id` | [V21__medical_discharge_report_projection.sql](../../../../backend/report-service/src/main/resources/db/migration/V21__medical_discharge_report_projection.sql) | — |
| `report_final_recognition` | `recognition_operation_id` | [V22__final_settlement_recognition.sql](../../../../backend/report-service/src/main/resources/db/migration/V22__final_settlement_recognition.sql) | — |
| `report_final_recognition_delivery` | `event_id` | [V22__final_settlement_recognition.sql](../../../../backend/report-service/src/main/resources/db/migration/V22__final_settlement_recognition.sql) | — |
| `report_final_recognition_contribution` | `see DDL` | [V22__final_settlement_recognition.sql](../../../../backend/report-service/src/main/resources/db/migration/V22__final_settlement_recognition.sql) | — |
| `report_final_deposit_release` | `recognition_operation_id,original_transaction_id` | [V22__final_settlement_recognition.sql](../../../../backend/report-service/src/main/resources/db/migration/V22__final_settlement_recognition.sql) | — |
| `report_final_recognition_daily` | `see DDL` | [V22__final_settlement_recognition.sql](../../../../backend/report-service/src/main/resources/db/migration/V22__final_settlement_recognition.sql) | — |
| `financial_source_rebuild` | `generation_id` | [V23__finite_financial_source_rebuild.sql](../../../../backend/report-service/src/main/resources/db/migration/V23__finite_financial_source_rebuild.sql) | — |
| `financial_source_rebuild_input` | `generation_id,source_kind,source_id` | [V23__finite_financial_source_rebuild.sql](../../../../backend/report-service/src/main/resources/db/migration/V23__finite_financial_source_rebuild.sql) | — |
| `financial_source_rebuild_day` | `see DDL` | [V23__finite_financial_source_rebuild.sql](../../../../backend/report-service/src/main/resources/db/migration/V23__finite_financial_source_rebuild.sql) | — |
| `financial_archive_restore` | `archive_fingerprint` | [V24__financial_archive_restore_provenance.sql](../../../../backend/report-service/src/main/resources/db/migration/V24__financial_archive_restore_provenance.sql) | — |
| `financial_generation_acceptance` | `generation_id` | [V25__controlled_financial_publication.sql](../../../../backend/report-service/src/main/resources/db/migration/V25__controlled_financial_publication.sql) | — |
| `financial_publication_audit` | `command_id` | [V25__controlled_financial_publication.sql](../../../../backend/report-service/src/main/resources/db/migration/V25__controlled_financial_publication.sql) | — |
| `operational_generation_acceptance` | `generation_id` | [V26__controlled_operational_publication.sql](../../../../backend/report-service/src/main/resources/db/migration/V26__controlled_operational_publication.sql) | — |
| `operational_publication_audit` | `command_id` | [V26__controlled_operational_publication.sql](../../../../backend/report-service/src/main/resources/db/migration/V26__controlled_operational_publication.sql) | — |
| `report_outpatient_coverage` | `coverage_id` | [V27__outpatient_read_coverage.sql](../../../../backend/report-service/src/main/resources/db/migration/V27__outpatient_read_coverage.sql) | — |

### 4.2. Từ điển cột của các bảng cốt lõi

Chọn các bảng của hành trình ngoại trú. Kiểu/ràng buộc cột được trích từ CREATE và ADD COLUMN; cột DROP đã bỏ. Đây không phải trình chạy SQL đầy đủ: ALTER TYPE/DEFAULT/constraint/index và SQL có điều kiện phải đọc thêm trong migration được link; không dùng bảng này để tự tạo DDL production. Các trường đã loại như `invoice.dispense_id` không còn xuất hiện.

#### Organization.account

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `account_id` | `UUID PRIMARY KEY` |
| `username` | `VARCHAR(50) NOT NULL` |
| `password_hash` | `VARCHAR(255) NOT NULL` |
| `staff_id` | `UUID` |
| `role` | `VARCHAR(20) NOT NULL` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `last_login_at` | `TIMESTAMPTZ` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `patient_id` | `UUID` |

#### Organization.staff

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `staff_id` | `UUID PRIMARY KEY` |
| `full_name` | `VARCHAR(100) NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `job_title` | `VARCHAR(20) NOT NULL` |
| `specialization` | `VARCHAR(100)` |
| `license_number` | `VARCHAR(50)` |
| `phone_number` | `VARCHAR(15)` |
| `email` | `VARCHAR(100)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Organization.department

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `department_id` | `UUID PRIMARY KEY` |
| `department_name` | `VARCHAR(100) NOT NULL` |
| `abbreviation` | `VARCHAR(20) NOT NULL` |
| `department_type` | `VARCHAR(20) NOT NULL` |
| `department_head_id` | `UUID` |
| `location` | `VARCHAR(255)` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Patient.patient

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `patient_id` | `UUID PRIMARY KEY` |
| `full_name` | `VARCHAR(100) NOT NULL` |
| `date_of_birth` | `DATE NOT NULL` |
| `gender` | `VARCHAR(1) NOT NULL` |
| `identity_number` | `VARCHAR(20) NOT NULL` |
| `address` | `VARCHAR(255)` |
| `phone_number` | `VARCHAR(15)` |
| `email` | `VARCHAR(100)` |
| `health_insurance_number` | `VARCHAR(20)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Clinical.appointment

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `appointment_id` | `UUID PRIMARY KEY` |
| `patient_id` | `UUID NOT NULL` |
| `doctor_id` | `UUID NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `appointment_date` | `DATE NOT NULL` |
| `appointment_time` | `TIME NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `reason` | `TEXT` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `care_contract_version` | `SMALLINT NOT NULL DEFAULT 0` |
| `exam_clearance_id` | `UUID` |
| `exam_clearance_at` | `TIMESTAMPTZ` |
| `emergency_override_id` | `UUID` |
| `exam_price_code` | `VARCHAR(64)` |
| `checked_in_at` | `TIMESTAMPTZ` |
| `examination_started_at` | `TIMESTAMPTZ` |
| `completed_at` | `TIMESTAMPTZ` |

#### Clinical.medical_record

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `record_id` | `UUID PRIMARY KEY` |
| `patient_id` | `UUID NOT NULL` |
| `doctor_id` | `UUID NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `examination_date` | `DATE NOT NULL` |
| `symptoms` | `TEXT` |
| `appointment_id` | `UUID REFERENCES appointment(appointment_id)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'OPEN'` |
| `disposition` | `VARCHAR(32)` |
| `disposition_note` | `TEXT` |
| `completed_at` | `TIMESTAMPTZ` |

#### Clinical.diagnosis

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `diagnosis_id` | `UUID PRIMARY KEY` |
| `record_id` | `UUID NOT NULL REFERENCES medical_record(record_id) ON DELETE CASCADE` |
| `diagnosis_name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `icd_code` | `VARCHAR(10)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |

#### Clinical.clinical_lab_order

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `source_order_id` | `UUID PRIMARY KEY` |
| `record_id` | `UUID NOT NULL REFERENCES medical_record(record_id)` |
| `patient_id` | `UUID NOT NULL` |
| `requesting_doctor_id` | `UUID NOT NULL` |
| `requesting_department_id` | `UUID NOT NULL` |
| `care_episode_type` | `VARCHAR(32) NOT NULL CHECK (care_episode_type = 'OUTPATIENT_VISIT')` |
| `care_episode_id` | `UUID NOT NULL REFERENCES appointment(appointment_id)` |
| `lab_type` | `VARCHAR(50) NOT NULL CHECK (length(btrim(lab_type)) > 0)` |
| `requested_date` | `DATE NOT NULL` |
| `price_code` | `VARCHAR(64) NOT NULL CHECK (length(btrim(price_code)) > 0)` |
| `ordered_at` | `TIMESTAMPTZ NOT NULL` |

#### Lab.lab_test

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `test_id` | `UUID PRIMARY KEY` |
| `record_id` | `UUID NOT NULL` |
| `patient_id` | `UUID NOT NULL` |
| `requesting_department_id` | `UUID NOT NULL` |
| `test_type` | `VARCHAR(50) NOT NULL` |
| `requested_date` | `DATE NOT NULL` |
| `performed_date` | `DATE` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `conclusion` | `TEXT` |
| `is_paid` | `BOOLEAN NOT NULL DEFAULT false` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `care_contract_version` | `SMALLINT NOT NULL DEFAULT 0` |
| `care_episode_type` | `VARCHAR(32)` |
| `care_episode_id` | `UUID` |
| `source_order_id` | `UUID` |
| `price_code` | `VARCHAR(64)` |
| `clearance_id` | `UUID` |
| `clearance_granted_at` | `TIMESTAMPTZ` |
| `emergency_override_id` | `UUID` |
| `result_version` | `INTEGER NOT NULL DEFAULT 0` |

#### Lab.lab_result

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `result_id` | `UUID PRIMARY KEY` |
| `test_id` | `UUID NOT NULL REFERENCES lab_test(test_id) ON DELETE CASCADE` |
| `indicator` | `VARCHAR(100) NOT NULL` |
| `value` | `VARCHAR(50) NOT NULL` |
| `unit` | `VARCHAR(20)` |
| `reference_range` | `VARCHAR(50)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |

#### Pharmacy.drug

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `drug_id` | `UUID PRIMARY KEY` |
| `drug_name` | `VARCHAR(150) NOT NULL` |
| `active_ingredient` | `VARCHAR(150)` |
| `unit` | `VARCHAR(20) NOT NULL` |
| `price` | `DECIMAL(15,2) NOT NULL` |
| `stock_quantity` | `INT NOT NULL DEFAULT 0` |
| `expiry_date` | `DATE NOT NULL` |
| `manufacturer` | `VARCHAR(150)` |
| `low_stock_threshold` | `INT NOT NULL DEFAULT 10` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Pharmacy.prescription

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `prescription_id` | `UUID PRIMARY KEY` |
| `record_id` | `UUID` |
| `patient_id` | `UUID NOT NULL` |
| `doctor_id` | `UUID NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `prescribed_date` | `DATE NOT NULL` |
| `total_amount` | `DECIMAL(15,2) NOT NULL DEFAULT 0` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `status` | `VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'` |
| `cancelled_at` | `TIMESTAMPTZ` |
| `cancelled_by` | `UUID` |
| `cancellation_reason` | `VARCHAR(500)` |
| `care_contract_version` | `SMALLINT NOT NULL DEFAULT 0` |
| `care_context` | `VARCHAR(20) NOT NULL DEFAULT 'OUTPATIENT'` |
| `care_episode_type` | `VARCHAR(32)` |
| `care_episode_id` | `UUID` |
| `admission_id` | `UUID` |
| `price_code` | `VARCHAR(64)` |
| `lifecycle_at_iso` | `VARCHAR(35)` |

#### Pharmacy.prescription_line

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `line_id` | `UUID PRIMARY KEY` |
| `prescription_id` | `UUID NOT NULL REFERENCES PRESCRIPTION(prescription_id) ON DELETE CASCADE` |
| `drug_id` | `UUID NOT NULL REFERENCES DRUG(drug_id)` |
| `quantity` | `INT NOT NULL` |
| `unit_price` | `DECIMAL(15,2) NOT NULL` |
| `dosage` | `VARCHAR(255)` |
| `line_total` | `DECIMAL(15,2) NOT NULL` |
| `drug_name_snapshot` | `VARCHAR(150)` |

#### Pharmacy.stock_reservation

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `reservation_id` | `UUID PRIMARY KEY` |
| `drug_id` | `UUID NOT NULL REFERENCES DRUG(drug_id)` |
| `prescription_id` | `UUID NOT NULL REFERENCES PRESCRIPTION(prescription_id)` |
| `quantity` | `INT NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'RESERVED'` |
| `expires_at` | `TIMESTAMPTZ NOT NULL` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `release_reason` | `VARCHAR(50)` |
| `released_at` | `TIMESTAMPTZ` |
| `released_by` | `UUID` |

#### Pharmacy.dispense_slip

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `dispense_id` | `UUID PRIMARY KEY` |
| `prescription_id` | `UUID NOT NULL UNIQUE REFERENCES PRESCRIPTION(prescription_id)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `dispensed_at` | `TIMESTAMPTZ` |
| `dispensed_by` | `UUID` |
| `failure_reason` | `VARCHAR(500)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |
| `dispensed_actor_type` | `VARCHAR(30)` |
| `lifecycle_at_iso` | `VARCHAR(35)` |

#### Billing.invoice

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `invoice_id` | `UUID PRIMARY KEY` |
| `patient_id` | `UUID NOT NULL` |
| `created_date` | `DATE NOT NULL` |
| `total_amount` | `DECIMAL(15,2) NOT NULL` |
| `is_paid` | `BOOLEAN NOT NULL DEFAULT false` |
| `payment_method` | `VARCHAR(20)` |
| `prescription_id` | `UUID` |
| `saga_status` | `VARCHAR(20) NOT NULL DEFAULT 'NONE'` |
| `paid_at` | `TIMESTAMPTZ` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Billing.billing_account

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `account_id` | `UUID PRIMARY KEY` |
| `patient_id` | `UUID NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `care_episode_type` | `VARCHAR(32) NOT NULL` |
| `care_episode_id` | `UUID NOT NULL` |
| `status` | `VARCHAR(32) NOT NULL DEFAULT 'OPEN'` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'VND'` |
| `version` | `BIGINT NOT NULL DEFAULT 0` |
| `opened_at` | `TIMESTAMPTZ NOT NULL` |
| `charge_closed_at` | `TIMESTAMPTZ` |
| `closed_at` | `TIMESTAMPTZ` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |

#### Billing.charge

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `charge_id` | `UUID PRIMARY KEY` |
| `account_id` | `UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id)` |
| `patient_id` | `UUID NOT NULL` |
| `department_id` | `UUID NOT NULL` |
| `source_type` | `VARCHAR(32) NOT NULL` |
| `source_id` | `UUID NOT NULL` |
| `price_code` | `VARCHAR(64) NOT NULL` |
| `description` | `VARCHAR(255) NOT NULL` |
| `quantity` | `NUMERIC(19,4) NOT NULL DEFAULT 1 CHECK (quantity > 0)` |
| `unit_amount` | `DECIMAL(19,2) NOT NULL CHECK (unit_amount >= 0)` |
| `gross_amount` | `DECIMAL(19,2) NOT NULL CHECK (gross_amount >= 0)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'POSTED'` |
| `void_reason` | `VARCHAR(500)` |
| `incurred_at` | `TIMESTAMPTZ NOT NULL` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `reconciled_result_id` | `UUID` |

#### Billing.payment_request

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `payment_request_id` | `UUID PRIMARY KEY` |
| `invoice_id` | `UUID NOT NULL UNIQUE` |
| `account_id` | `UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id)` |
| `purpose` | `VARCHAR(32) NOT NULL` |
| `status` | `VARCHAR(24) NOT NULL DEFAULT 'PENDING'` |
| `requested_amount` | `DECIMAL(19,2) NOT NULL CHECK (requested_amount >= 0)` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'VND'` |
| `expires_at` | `TIMESTAMPTZ` |
| `created_by` | `UUID` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `completed_at` | `TIMESTAMPTZ` |

#### Billing.payment_transaction

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `transaction_id` | `UUID PRIMARY KEY` |
| `account_id` | `UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id)` |
| `payment_request_id` | `UUID REFERENCES PAYMENT_REQUEST(payment_request_id)` |
| `transaction_type` | `VARCHAR(20) NOT NULL` |
| `classification` | `VARCHAR(24) NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL` |
| `amount` | `DECIMAL(19,2) NOT NULL CHECK (amount > 0)` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'VND'` |
| `payment_method` | `VARCHAR(20) NOT NULL` |
| `provider_reference` | `VARCHAR(120)` |
| `idempotency_key` | `VARCHAR(120) NOT NULL UNIQUE` |
| `original_transaction_id` | `UUID REFERENCES PAYMENT_TRANSACTION(transaction_id)` |
| `completed_at` | `TIMESTAMPTZ` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `actor_account_id` | `UUID` |

#### Billing.payment_allocation

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `allocation_id` | `UUID PRIMARY KEY` |
| `transaction_id` | `UUID NOT NULL REFERENCES PAYMENT_TRANSACTION(transaction_id)` |
| `charge_id` | `UUID NOT NULL REFERENCES CHARGE(charge_id)` |
| `amount` | `DECIMAL(19,2) NOT NULL CHECK (amount > 0)` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |

#### Billing.financial_clearance

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `clearance_id` | `UUID PRIMARY KEY` |
| `account_id` | `UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id)` |
| `payment_request_id` | `UUID NOT NULL REFERENCES PAYMENT_REQUEST(payment_request_id)` |
| `invoice_id` | `UUID NOT NULL` |
| `patient_id` | `UUID NOT NULL` |
| `purpose` | `VARCHAR(32) NOT NULL` |
| `care_episode_type` | `VARCHAR(32) NOT NULL` |
| `care_episode_id` | `UUID NOT NULL` |
| `appointment_id` | `UUID` |
| `record_id` | `UUID` |
| `lab_test_ids` | `UUID[] NOT NULL DEFAULT '{}'` |
| `prescription_id` | `UUID` |
| `admission_id` | `UUID` |
| `surgery_case_id` | `UUID` |
| `amount` | `DECIMAL(19,2) NOT NULL CHECK (amount >= 0)` |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'VND'` |
| `payment_method` | `VARCHAR(20) NOT NULL` |
| `emergency_override` | `BOOLEAN NOT NULL DEFAULT FALSE` |
| `expires_at` | `TIMESTAMPTZ` |
| `granted_at` | `TIMESTAMPTZ NOT NULL` |
| `revoked_at` | `TIMESTAMPTZ` |

#### Notification.notification

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `notification_id` | `UUID PRIMARY KEY` |
| `patient_id` | `UUID NOT NULL` |
| `title` | `VARCHAR(255) NOT NULL` |
| `content` | `TEXT NOT NULL` |
| `channel` | `VARCHAR(10) NOT NULL` |
| `recipient_address` | `VARCHAR(150)` |
| `status` | `VARCHAR(10) NOT NULL DEFAULT 'PENDING'` |
| `failure_reason` | `VARCHAR(255)` |
| `retry_count` | `INT NOT NULL DEFAULT 0` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `sent_at` | `TIMESTAMPTZ` |
| `template_key` | `VARCHAR(64)` |
| `source_event_id` | `UUID` |
| `source_event_type` | `VARCHAR(100)` |
| `source_id` | `UUID` |
| `correlation_id` | `VARCHAR(120)` |
| `sensitivity` | `VARCHAR(32) NOT NULL DEFAULT 'STANDARD'` |

#### Report.daily_visit_report

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `report_id` | `UUID PRIMARY KEY` |
| `report_date` | `DATE NOT NULL` |
| `department_id` | `UUID` |
| `visit_count` | `INT NOT NULL DEFAULT 0` |
| `lab_count` | `INT NOT NULL DEFAULT 0` |
| `prescription_count` | `INT NOT NULL DEFAULT 0` |
| `revenue` | `DECIMAL(15,2) NOT NULL DEFAULT 0` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Report.monthly_revenue_report

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `report_id` | `UUID PRIMARY KEY` |
| `month` | `INT NOT NULL` |
| `year` | `INT NOT NULL` |
| `department_id` | `UUID` |
| `total_revenue` | `DECIMAL(15,2) NOT NULL DEFAULT 0` |
| `invoice_count` | `INT NOT NULL DEFAULT 0` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` |

#### Report.drug_statistic

| Column | Kiểu và ràng buộc khai báo |
|---|---|
| `statistic_id` | `UUID PRIMARY KEY` |
| `drug_id` | `UUID NOT NULL` |
| `drug_name` | `VARCHAR(150) NOT NULL` |
| `report_date` | `DATE NOT NULL` |
| `department_id` | `UUID` |
| `dispensed_quantity` | `INT NOT NULL DEFAULT 0` |
| `updated_at` | `TIMESTAMPTZ` |

### 4.3. Ràng buộc và chiến lược lưu trữ

- UUID là khóa nghiệp vụ; tham chiếu xuyên context không có JPA relation/FK. Account.patient_id ở Organization vẫn tham chiếu logic Patient dù cùng Core JVM.
- Tiền dùng BigDecimal và NUMERIC/DECIMAL theo DDL; V0 thường `(15,2)`, ledger `(19,2)`. Không đổi precision về một kiểu chung trong tài liệu.
- Appointment unique PENDING theo patient/date; medical_record unique appointment; prescription_line quantity > 0; stock/price không âm; unique prescription/drug reservation.
- Billing unique charge `(source_type, source_id, price_code)` và `payment_transaction.idempotency_key`; foreign keys/allocations chỉ trong Billing store.
- Timestamp nghiệp vụ dùng TIMESTAMPTZ/Instant, ngày dùng DATE/LocalDate. Lab result.value là VARCHAR, giữ kết quả dạng text và reference_range.
- Outbox cùng transaction với state; inbox/receipt cùng transaction với effect. Retry không đổi eventId/bytes; poison message bounded retry → DLQ; HELD không tự release.
- Flyway migration đã phát hành là append-only. Các index và CHECK nhiều cột nằm trong DDL link ở inventory, không chỉ trong bảng cột phía trên.

### 4.4. Database Diagram

### Triển khai database: một server phát triển, tám authority

![Triển khai database: một server phát triển, tám authority](assets/svg/23-vi.svg)

<details>
<summary>Mermaid · 23-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart TB
 PG["PostgreSQL 16 development instance"]
 subgraph CORE["Core host — independent pools / Flyway / transactions"]
 O[(mediflow_organization)]
 P[(mediflow_patient)]
 end
 C[(mediflow_clinical)]
 L[(mediflow_lab)]
 PH[(mediflow_pharmacy)]
 B[(mediflow_billing)]
 subgraph SUPPORT["Support host — independent pools / Flyway / transactions"]
 R[(mediflow_report)]
 N[(mediflow_notification)]
 end
 PG --- O & P & C & L & PH & B & R & N
 DEFER["Retained databases: mediflow_inpatient / mediflow_surgery<br/>DEFERRED — not active MVP authorities"]
 PG -. initialized on fresh setup .-> DEFER
```

</details>

## 5. SiteMap

Sitemap lấy từ `frontend/src/app/**/page.tsx` và `mobile/lib/app/router.dart`. Route tồn tại không chứng minh workflow đã nghiệm thu; route động giữ nguyên `[id]` theo App Router. Các route Inpatient được giữ nhưng ngoài MVP; không suy ra Surgery route nếu source không có.

### Sitemap web theo nhóm route

![Sitemap web theo nhóm route](assets/svg/24-vi.svg)

<details>
<summary>Mermaid · 24-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart TB
 ROOT["/ entry"] --> LOGIN["/login"]
 ROOT --> SHELL["Authenticated dashboard layout"]
 SHELL --> ORG["/organization<br/>departments / staff / accounts"]
 SHELL --> PAT["/patients<br/>list / new / detail / edit"]
 SHELL --> APP["/appointments<br/>list / new / detail / edit"]
 SHELL --> REC["/records<br/>list / new / detail / edit"]
 SHELL --> LAB["/lab<br/>list / new / detail"]
 SHELL --> PH["/pharmacy<br/>drugs / prescriptions / admin outbox"]
 SHELL --> BILL["/billing<br/>invoices / payment-requests / revenue / admin outbox"]
 SHELL --> REP["/reports<br/>outpatient"]
 SHELL --> NOT["/notifications<br/>list / detail / send"]
 SHELL -. DEFERRED .-> INP["/inpatient<br/>new / detail / beds"]
 ROOT -. utility .-> LOAD["/loading-test"]
```

</details>

| Web route | File source | Phạm vi |
|---|---|---|
| `/appointments/[appointmentId]/edit` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/appointments/%5BappointmentId%5D/edit/page.tsx) | SOURCE / theo gate và policy |
| `/appointments/[appointmentId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/appointments/%5BappointmentId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/appointments/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/appointments/new/page.tsx) | SOURCE / theo gate và policy |
| `/appointments` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/appointments/page.tsx) | SOURCE / theo gate và policy |
| `/billing/[invoiceId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/%5BinvoiceId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/billing/admin/outbox` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/admin/outbox/page.tsx) | SOURCE / theo gate và policy |
| `/billing/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/new/page.tsx) | SOURCE / theo gate và policy |
| `/billing` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/page.tsx) | SOURCE / theo gate và policy |
| `/billing/payment-requests/[requestId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/payment-requests/%5BrequestId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/billing/payment-requests` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/payment-requests/page.tsx) | SOURCE / theo gate và policy |
| `/billing/revenue` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/billing/revenue/page.tsx) | SOURCE / theo gate và policy |
| `/inpatient/[admissionId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/inpatient/%5BadmissionId%5D/page.tsx) | DEFERRED |
| `/inpatient/beds` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/inpatient/beds/page.tsx) | DEFERRED |
| `/inpatient/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/inpatient/new/page.tsx) | DEFERRED |
| `/inpatient` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/inpatient/page.tsx) | DEFERRED |
| `/lab/[testId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/lab/%5BtestId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/lab/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/lab/new/page.tsx) | SOURCE / theo gate và policy |
| `/lab` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/lab/page.tsx) | SOURCE / theo gate và policy |
| `/notifications/[notificationId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/notifications/%5BnotificationId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/notifications` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/notifications/page.tsx) | SOURCE / theo gate và policy |
| `/notifications/send` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/notifications/send/page.tsx) | SOURCE / theo gate và policy |
| `/organization/accounts` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/accounts/page.tsx) | SOURCE / theo gate và policy |
| `/organization/departments/[departmentId]/edit` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/departments/%5BdepartmentId%5D/edit/page.tsx) | SOURCE / theo gate và policy |
| `/organization/departments/[departmentId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/departments/%5BdepartmentId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/organization/departments/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/departments/new/page.tsx) | SOURCE / theo gate và policy |
| `/organization` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/page.tsx) | SOURCE / theo gate và policy |
| `/organization/staff/[staffId]/edit` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/staff/%5BstaffId%5D/edit/page.tsx) | SOURCE / theo gate và policy |
| `/organization/staff/[staffId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/staff/%5BstaffId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/organization/staff/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/organization/staff/new/page.tsx) | SOURCE / theo gate và policy |
| `/patients/[patientId]/edit` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/patients/%5BpatientId%5D/edit/page.tsx) | SOURCE / theo gate và policy |
| `/patients/[patientId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/patients/%5BpatientId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/patients/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/patients/new/page.tsx) | SOURCE / theo gate và policy |
| `/patients` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/patients/page.tsx) | SOURCE / theo gate và policy |
| `/pharmacy/admin/outbox` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/admin/outbox/page.tsx) | SOURCE / theo gate và policy |
| `/pharmacy/drugs/[drugId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/drugs/%5BdrugId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/pharmacy/drugs/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/drugs/new/page.tsx) | SOURCE / theo gate và policy |
| `/pharmacy/drugs` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/drugs/page.tsx) | SOURCE / theo gate và policy |
| `/pharmacy` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/page.tsx) | SOURCE / theo gate và policy |
| `/pharmacy/prescriptions/[prescriptionId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/prescriptions/%5BprescriptionId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/pharmacy/prescriptions/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/prescriptions/new/page.tsx) | SOURCE / theo gate và policy |
| `/pharmacy/prescriptions` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/pharmacy/prescriptions/page.tsx) | SOURCE / theo gate và policy |
| `/records/[recordId]/edit` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/records/%5BrecordId%5D/edit/page.tsx) | SOURCE / theo gate và policy |
| `/records/[recordId]` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/records/%5BrecordId%5D/page.tsx) | SOURCE / theo gate và policy |
| `/records/new` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/records/new/page.tsx) | SOURCE / theo gate và policy |
| `/records` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/records/page.tsx) | SOURCE / theo gate và policy |
| `/reports/outpatient` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/reports/outpatient/page.tsx) | SOURCE / theo gate và policy |
| `/reports` | [page.tsx](../../../../frontend/src/app/%28dashboard%29/reports/page.tsx) | SOURCE / theo gate và policy |
| `/loading-test` | [page.tsx](../../../../frontend/src/app/loading-test/page.tsx) | UTILITY |
| `/login` | [page.tsx](../../../../frontend/src/app/login/page.tsx) | SOURCE / theo gate và policy |
| `/` | [page.tsx](../../../../frontend/src/app/page.tsx) | SOURCE / theo gate và policy |

### Sitemap mobile và mức triển khai

![Sitemap mobile và mức triển khai](assets/svg/25-vi.svg)

<details>
<summary>Mermaid · 25-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart TB
 LOGIN["/login — real Gateway login"] --> HOME["/ — authenticated shell"]
 HOME --> AP["/appointments — read-only Gateway list"]
 HOME --> LAB["/lab — read-only Gateway list"]
 HOME --> REST["/organization /patients /records<br/>/pharmacy /billing /notifications /reports<br/>feature workspaces — unfinished business flows"]
 HOME --> DENY["/forbidden — role denied"]
 HOME --> UNKNOWN["Unknown path — not found"]
```

</details>
Loading/error/empty/retry phải rõ; 401 yêu cầu đăng nhập lại, không tự gửi lại mutation chưa biết kết quả. UI role gating chỉ hỗ trợ UX. Flutter có real login và read-only Appointment/Lab, các workspace còn lại không được mô tả như native app đã hoàn tất.

## 6. Algorithm

### 6.1. Tạo đơn và giữ chỗ tồn kho

Nguồn chính: `CarePrescriptionCreationService.createInternal`. Giá từ catalogue server; không dùng giá client, không tự giả clinical identity.

### Thuật toán tạo đơn V1 có kiểm context

![Thuật toán tạo đơn V1 có kiểm context](assets/svg/26-vi.svg)

<details>
<summary>Mermaid · 26-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart TB
 A([Command + signed actor]) --> V{Valid V1 OUTPATIENT intent<br/>and exact identity/context?}
 V -->|No| ERR([Reject])
 V -->|Yes| R[Claim commandId + actor + fingerprint]
 R --> RE{Existing matching receipt?}
 RE -->|Yes| OLD([Return original prescriptionId])
 RE -->|No| SORT[Sort unique drug IDs and acquire row locks]
 SORT --> TIME[Recheck context / identity / clock after waits]
 TIME --> STOCK{All quantities positive<br/>drugs unexpired<br/>stock minus RESERVED sufficient?}
 STOCK -->|No| ERR
 STOCK -->|Yes| SNAP[Build server price/name snapshots]
 SNAP --> AGAIN[Recheck clock / date / authority before writes]
 AGAIN --> TX[Atomic prescription + lines + reservations<br/>pending dispense slip + held event + receipt]
 TX --> DONE([Return persisted ID])
```

</details>
Công thức: `available = drug.stock_quantity − Σ quantity(RESERVED)`; `line_total = quantity × server_unit_price`; `total_amount = Σ line_total`. Reserve chưa làm giảm stock vật lý. Sắp xếp n dòng thuốc có độ phức tạp CPU O(n log n); số hàng reservation đọc và thời gian chờ DB không bị chặn chỉ bởi n.

### 6.2. Ghi nhận thanh toán idempotent

Nguồn: `LedgerPaymentService.complete`. Lock idempotency key rồi account/request; replay kiểm requestId, actor, amount, currency, method và provider reference. Partial payment chưa cấp clearance; chỉ đủ tiền và purpose khác SETTLEMENT mới grant đúng target.

### Thuật toán payment và clearance

![Thuật toán payment và clearance](assets/svg/27-vi.svg)

<details>
<summary>Mermaid · 27-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart TB
 A([Payment command]) --> V[Validate amount / currency / actor / correlation / key]
 V --> L[Lock idempotency key and account/request]
 L --> EX{Receipt exists?}
 EX -->|Yes| EQ{Same business intent?}
 EQ -->|Yes| OLD([Return persisted receipt])
 EQ -->|No| CON([BILLING_IDEMPOTENCY_CONFLICT])
 EX -->|No| GUARD{Payable request / open account<br/>unexpired / same currency<br/>completed + amount no greater than requested?}
 GUARD -->|No| NO([Reject])
 GUARD -->|Yes| PAY[Save completed transaction and allocations]
 PAY --> FULL{Fully paid and service purpose?}
 FULL -->|Yes| CLEAR[Persist exact-purpose target clearance]
 FULL -->|No| PART[Persist partial or settlement state]
 CLEAR --> TX[Commit state + durable events atomically]
 PART --> TX
 TX --> OUT([Return receipt])
```

</details>

### 6.3. Phát sự kiện, chống trùng và phục hồi


### Thuật toán outbox / inbox

![Thuật toán outbox / inbox](assets/svg/28-vi.svg)

<details>
<summary>Mermaid · 28-vi</summary>

```mermaid
%%{init: {"theme": "base", "securityLevel": "loose", "fontFamily": "Arial, sans-serif", "themeVariables": {"primaryColor": "#e0f2fe", "primaryTextColor": "#0f172a", "primaryBorderColor": "#0284c7", "lineColor": "#475569", "secondaryColor": "#ccfbf1", "tertiaryColor": "#f1f5f9", "background": "#ffffff", "mainBkg": "#e0f2fe", "nodeBorder": "#0284c7", "clusterBkg": "#f8fafc", "clusterBorder": "#94a3b8", "actorBkg": "#e0f2fe", "actorBorder": "#0284c7", "actorTextColor": "#0f172a", "signalColor": "#475569", "signalTextColor": "#0f172a", "labelBoxBkgColor": "#f1f5f9", "labelBoxBorderColor": "#94a3b8", "activationBkgColor": "#ccfbf1", "activationBorderColor": "#0f766e", "sequenceNumberColor": "#ffffff", "attributeBackgroundColorOdd": "#ffffff", "attributeBackgroundColorEven": "#f0f9ff"}, "flowchart": {"htmlLabels": true, "curve": "basis", "nodeSpacing": 30, "rankSpacing": 50}, "sequence": {"useMaxWidth": true, "wrap": true}, "er": {"useMaxWidth": true}}}%%
flowchart LR
 WRITE["Local state + outbox commit"] --> CLAIM["Publisher claims eligible row / lease"]
 CLAIM --> PUB["Publish original ID and bytes"]
 PUB --> CONF{Broker confirms?}
 CONF -->|No / timeout| RETRY["Bounded retry preserving identity"]
 RETRY --> CLAIM
 CONF -->|Yes| SENT["Record published outcome"]
 PUB --> DEL["Durable queue delivers"]
 DEL --> INBOX{Consumer receipt exists?}
 INBOX -->|Same payload / committed effect| ACK([ACK without duplicate effect])
 INBOX -->|New| ATOMIC["Atomic inbox claim + business effect + own outbox"]
 ATOMIC --> ACK
 INBOX -->|Conflicting identity / poison| DLQ["Reject / bounded retry / DLQ or quarantine"]
```

</details>
Publisher confirm không tạo exactly-once. Crash sau commit trước ACK phải trả lại committed outcome, không trừ kho/thu tiền lần hai. Crash trước commit rollback toàn bộ local effect. HELD/quarantine chỉ release theo policy/gate, không theo retry timer.

### 6.4. Tổng hợp báo cáo có coverage

Nhận fact có source hợp lệ → kiểm receipt/fingerprint → cập nhật projection trong local transaction → query scope/date/department → kiểm coverage → trả số đo hoặc unavailable. `cash received − completed cash refunds = net cash` chỉ tính trên dữ kiện và khoảng đã có coverage; không suy payment thành earned revenue. Replay/rebuild Report không phát lại notification nghiệp vụ.

## 7. Đối chiếu source và giới hạn review

| Nội dung | Nguồn kiểm chứng |
|---|---|
| Topology / scope | [docs/plans/01-system-design.md](../../../plans/01-system-design.md) |
| Business invariants | [docs/plans/02-business-flows.md](../../../plans/02-business-flows.md) |
| Contract modes / wire / RBAC | [docs/plans/03-contracts.md](../../../plans/03-contracts.md) |
| Atomicity / reliability | [docs/plans/04-data-reliability.md](../../../plans/04-data-reliability.md) |
| Acceptance gates | [docs/plans/06-acceptance-runbook.md](../../../plans/06-acceptance-runbook.md) |
| Compose deployment | [docker-compose.yml](../../../../docker-compose.yml) |
| Core host | [backend/core-service/README.md](../../../../backend/core-service/README.md) |
| Support host | [backend/support-service/README.md](../../../../backend/support-service/README.md) |
| Clinical aggregate | [backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/Appointment.java](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/Appointment.java) |
| Medical record | [backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/MedicalRecord.java](../../../../backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/MedicalRecord.java) |
| Lab aggregate | [backend/lab-service/src/main/java/com/mediflow/lab/domain/model/LabTest.java](../../../../backend/lab-service/src/main/java/com/mediflow/lab/domain/model/LabTest.java) |
| Stock reservation algorithm | [backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/CarePrescriptionCreationService.java](../../../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/CarePrescriptionCreationService.java) |
| Payment algorithm | [backend/billing-service/src/main/java/com/mediflow/billing/application/service/LedgerPaymentService.java](../../../../backend/billing-service/src/main/java/com/mediflow/billing/application/service/LedgerPaymentService.java) |
| Frontend route state | [frontend/docs/frontend-workboard.md](../../../../frontend/docs/frontend-workboard.md) |
| Web API boundary | [frontend/src/lib/api.ts](../../../../frontend/src/lib/api.ts) |
| Mobile scope | [mobile/README.md](../../../../mobile/README.md) |
| Mobile routes | [mobile/lib/app/router.dart](../../../../mobile/lib/app/router.dart) |
| Review 1 baseline | [docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md](../../01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md) |

Review này đọc source/config/migrations và đối chiếu graph index; SQL có khoảng parse_partial được đọc trực tiếp từ file. Không dùng số lượng node/edge của graph làm bằng chứng tích hợp runtime. Acceptance log có ngày/profile riêng; default flag không được bật chỉ để tạo báo cáo. File source đang sửa trong working tree được giữ nguyên và không commit/push trong tác vụ này.

Tài sản: [nguồn Mermaid](assets/mermaid/), [SVG](assets/svg/), [logo](assets/logos/), [cấu hình theme](assets/mermaid-config.json). Logo là nhận diện của các dự án tương ứng, nguồn [Devicon](https://github.com/devicons/devicon/tree/v2.17.0); actor hình người là SVG nội bộ của bộ tài liệu.
