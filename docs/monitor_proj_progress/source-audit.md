# Kiểm kê mã nguồn và bằng chứng kỹ thuật

## 1. Phạm vi và phương pháp

Repository đã được đọc lại bằng `codebase-memory-mcp 0.9.0` ở trạng thái index `ready`, khớp chính xác HEAD `eaccf0955ec37616dde8027efee0a721b91e20d2`. Các truy vấn đã dùng gồm `list_projects`, `index_status`, `get_graph_schema`, `get_architecture`, `query_graph`, `search_graph` và `search_code`. Do phiên Codex không nạp server thành tool trực tiếp, các tool MCP được gọi qua CLI chính thức của cùng binary.

Kết quả graph được đối chiếu bằng kiểm kê file trực tiếp, đọc toàn bộ file mã nguồn không rỗng trong `backend/src`, `frontend/src` và `mobile/lib`, rồi chạy quality gate hiện có. Build output (`target`, `.next`), dependency (`node_modules`) và file `.gitkeep` không được tính là mã nguồn.

## 2. Số liệu tổng quan từ graph

| Chỉ số | Kết quả |
|---|---:|
| Tổng node / edge | 1.670 / 2.032 |
| Backend scoped node / edge | 873 / 1.140 |
| Frontend scoped node / edge | 53 / 82 |
| Mobile scoped node / edge | 0 / 0 |
| Route graph toàn repo | 4 |
| Route backend thật | 2 (`POST /login`, `POST /refresh`) |
| HTTP cross-service edge nhận diện được | 1 |
| Event publisher/listener trong mã Java | 0 / 0 |

Graph cho thấy các cụm chạy được tập trung ở gateway, common, demo frontend và một phần domain/persistence pharmacy. Không có call graph nghiệp vụ xuyên đủ 8 service.

## 3. Kiểm kê file thực tế

| Khu vực | File không rỗng | File mã/config chính | Ghi chú |
|---|---:|---:|---|
| Backend | 90 | 61 | 50 Java, 1 migration SQL; còn lại YAML/config |
| Frontend `src` | 9 | 8 | 7 TS/TSX, 1 CSS, 1 favicon |
| Mobile `lib` | 0 | 0 | chỉ có thư mục và `.gitkeep` |

### Backend theo module

| Module | Main Java | Test Java | Mức thực tế |
|---|---:|---:|---|
| common | 8 | 0 | Có envelope, pagination, exception và role/claim constants |
| eureka-server | 1 | 0 | Có entry point + config |
| gateway | 7 | 0 | Có routing và stub JWT auth; chưa rate limit/correlation hoàn chỉnh |
| organization-service | 1 | 0 | Skeleton |
| patient-service | 0 | 0 | Không có Java source; chỉ config/HTTP template |
| clinical-service | 1 | 0 | Skeleton |
| lab-service | 1 | 0 | Skeleton |
| pharmacy-service | 25 | 1 | Có domain, out-port, JPA entity/repository và DDL; thiếu application service/web/messaging/adapters |
| billing-service | 1 | 1 | Skeleton + ArchitectureTest |
| notification-service | 1 | 0 | Skeleton |
| report-service | 1 | 1 | Skeleton + ArchitectureTest |

### Dấu hiệu triển khai quan trọng từ `search_code`

| Mẫu | Số file khớp | Kết luận |
|---|---:|---|
| `@RestController` | 1 | Chỉ có `AuthController` ở gateway |
| `@Entity` | 5 | Chỉ pharmacy có entity |
| `CREATE TABLE` trong migration thật | 5 | Tất cả nằm trong một file của pharmacy |
| `@PreAuthorize` | 0 | RBAC business endpoint chưa triển khai |
| `RabbitTemplate` | 0 | Chưa có publisher |
| `@RabbitListener` | 0 | Chưa có consumer |

## 4. Web và Mobile

### Web Next.js

Các route thực tế:

- `/` — trang giới thiệu demo;
- `/login` — đăng nhập vào gateway stub;
- `/patients` — bảng bệnh nhân đọc từ API.

Chưa có dashboard shell, auth guard tập trung, feature API/types/components, và các trang appointment, records, lab, pharmacy, billing, notification, report. Các folder mục tiêu tồn tại nhưng rỗng.

### Mobile Flutter

Không có `pubspec.yaml`, `analysis_options.yaml`, `lib/main.dart`, router, API client, theme, model, use case, widget hay test. Vì vậy chưa thể chạy `flutter pub get`, `flutter test` hoặc build APK/IPA.

## 5. Quality gate ngày 02/09

| Gate | Kết quả | Chi tiết |
|---|---|---|
| `mvn -q test` | **FAIL** | Pharmacy vi phạm clean architecture: `ProcessedEventPort` ở application trả về `ProcessedEventJpaEntity` thuộc infrastructure (2 vi phạm) |
| `pnpm typecheck` | **PASS** | TypeScript compile không lỗi |
| `pnpm lint` | **FAIL** | `frontend/src/app/patients/page.tsx`: gọi `setRole` đồng bộ trong effect |
| `pnpm build` | Chưa chạy trong chuỗi gate | Chuỗi dừng sau lint fail |
| Flutter gate | Không thể chạy | Thiếu project manifest và mã Dart |

## 6. Độ tin cậy của tài liệu tiến độ

- Script changelog báo 41 commit và một tác giả, nhưng Git thật có 56 commit và nhiều identity tác giả. Không dùng changelog summary làm nguồn duy nhất cho phân công/đóng góp.
- `docs/plan/README.md` ghi nhóm 3 người và còn placeholder; file Word ghi 4 thành viên có tên. Cần chốt roster trước khi nộp.
- README root nói patient là service có code, nhưng source hiện tại không có Java của patient; pharmacy mới là module có nhiều code nhất. README này đã lỗi thời.

## 7. Kết luận kiểm kê

Repository chứa nền tảng thiết kế tốt hơn nền tảng triển khai. Có đủ tài liệu để phục hồi SRS và Design v1.0 nhanh, nhưng chưa đủ bằng chứng để tuyên bố GUI, DDL triển khai, event flow hoặc backend đã hoàn thiện.

