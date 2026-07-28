# 09 — gateway

**Module** `gateway` · **Package** `com.mediflow.gateway` · **Cổng** 8080 · **Không có database**

Điểm vào duy nhất. Kiểm JWT một lần, định tuyến theo đường dẫn qua Eureka, và sở hữu các endpoint
đăng nhập. Nó **không lưu dữ liệu nghiệp vụ nào** — thông tin đăng nhập nằm ở `organization-service`.

> **Ngăn xếp khác hẳn.** Đây là Spring Cloud Gateway chạy trên **WebFlux**, không phải Spring MVC.
> Đừng dùng `@RestController` với kiểu servlet, đừng dùng `OncePerRequestFilter`, đừng dùng Feign
> kiểu chặn ở đây. Mọi thứ đều phản ứng: `Mono`/`Flux`, `WebFilter`, `GlobalFilter`, `WebClient`.
>
> Đây cũng chính là lý do `common` phải không phụ thuộc gì cả — gateway dùng nó song song với WebFlux.

## 1. Định tuyến

Đã cấu hình sẵn trong `src/main/resources/application.yml`. Chín route, tất cả đều dùng `lb://`
(phân giải qua Eureka — không bao giờ ghi cứng host):

| Route id | Điều kiện đường dẫn | Đích |
|----------|---------------------|------|
| `organization-service` | `/api/v1/org/**` | `lb://organization-service` |
| `patient-service` | `/api/v1/patients/**` | `lb://patient-service` |
| `clinical-service-appointments` | `/api/v1/appointments/**` | `lb://clinical-service` |
| `clinical-service-records` | `/api/v1/records/**` | `lb://clinical-service` |
| `lab-service` | `/api/v1/lab/**` | `lb://lab-service` |
| `pharmacy-service` | `/api/v1/pharmacy/**` | `lb://pharmacy-service` |
| `billing-service` | `/api/v1/billing/**` | `lb://billing-service` |
| `notification-service` | `/api/v1/notifications/**` | `lb://notification-service` |
| `report-service` | `/api/v1/reports/**` | `lb://report-service` |

`clinical-service` được tới bằng hai route — một bounded context, hai tiền tố URL.

## 2. Endpoint của chính gateway

| Method | Path | Body | Trả về | Xác thực |
|--------|------|------|--------|----------|
| POST | `/api/v1/auth/login` | `{username, password}` | `{accessToken, refreshToken, role}` | **công khai** |
| POST | `/api/v1/auth/refresh` | `{refreshToken}` | `{accessToken}` | **công khai** |
| GET | `/actuator/health` | — | `{status}` | **công khai** |

Ba đường dẫn này là **những chỗ duy nhất** không cần xác thực trong toàn hệ thống.

## 3. Luồng đăng nhập — thay thế bản stub

`AuthController` hiện tại trả token cho cặp `admin/admin123` ghi cứng. Hãy thay bằng lời gọi thật
sang `organization-service`:

```
POST /api/v1/auth/login {username, password}
  1. WebClient → lb://organization-service  POST /api/v1/org/accounts/verify
  2. organization trả về { maTaiKhoan, maNhanVien, maKhoa, vaiTro }
  3. gateway ký access token  (các claim bên dưới)
  4. gateway ký refresh token (sub + type=refresh, hạn dài hơn)
  5. → { accessToken, refreshToken, role }
```

**Gateway tuyệt đối không được tự đọc bảng `TAI_KHOAN`.** Nó không có datasource, không có JPA — thò
tay vào database của service khác là quy tắc duy nhất mà kiến trúc này không thể nhân nhượng.

Khi organization-service trả `422 AUTH_INVALID_CREDENTIALS`, gateway trả **401** với
`ApiResponse.fail(ApiError.of("AUTH_INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng"))`.
Không bao giờ phân biệt "không có user" với "sai mật khẩu" trong phản hồi.

Nếu không gọi được organization-service, trả **503** `AUTH_UPSTREAM_UNAVAILABLE`. Không được rơi về
tài khoản ghi cứng — một credential stub sống sót tới production là một lỗ hổng thật.

### Các claim của JWT

| Claim | Giá trị |
|-------|---------|
| `sub` | `maTaiKhoan` |
| `role` | `vaiTro` — một chuỗi role, phía dưới đọc thành `ROLE_<role>` |
| `maNhanVien` | id nhân viên, hoặc vắng mặt với tài khoản `PATIENT` |
| `maKhoa` | id khoa, hoặc vắng mặt |
| `maBenhNhan` | id bệnh nhân — **bắt buộc** với tài khoản `PATIENT`; việc kiểm quyền sở hữu phụ thuộc vào nó |
| `exp` | hiện tại + `mediflow.jwt.access-token-minutes` (30) |

Refresh token mang `sub`, `type=refresh` và `exp = hiện tại + refresh-token-minutes` (1440). Một
refresh token **không được** dùng thay access token — phải kiểm `type` mỗi lần xác minh.

HS256, secret lấy từ `mediflow.jwt.secret`. **Cùng một secret** phải đến được mọi service; ở môi
trường thật hãy cấp qua `MEDIFLOW_JWT_SECRET`, không bao giờ dùng giá trị dev mặc định đã commit.

## 4. Filter JWT (`GlobalFilter`)

```java
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    // 1. đường dẫn công khai (/api/v1/auth/**, /actuator/health) → chain.filter(exchange)
    // 2. thiếu hoặc sai định dạng header Authorization → 401
    // 3. chữ ký sai / hết hạn / type=refresh → 401
    // 4. hợp lệ → truyền claim xuống dưới dạng header, rồi chain
    @Override public int getOrder() { return -100; }   // chạy trước khi định tuyến
}
```

Truyền xuống dưới dạng header để các service ghi log ngữ cảnh mà không phải parse lại:

`X-User-Id`, `X-User-Role`, `X-Ma-Khoa`, `X-Correlation-Id`.

> Header được truyền xuống là **tiện lợi, không phải phân quyền**. Mỗi service vẫn tự kiểm lại JWT —
> chúng truy cập được trực tiếp qua cổng 8081–8089, nên một request chỉ vì đã đến nơi thì không có
> nghĩa là nó được phép. Xem [`docs/ai/07-security-rbac.md`](../../docs/ai/07-security-rbac.md).

**Xóa các header `X-User-*` do client gửi vào** trước khi tự đặt header của mình. Nếu không, client
có thể giả mạo `X-User-Role: ADMIN`.

## 5. Correlation id

Một `WebFilter` xếp trước filter JWT:

1. đọc `X-Correlation-Id`; nếu không có thì sinh một `UUID`
2. đưa vào reactive context và gắn lên request đi ra
3. gắn vào response

Mọi service chép nó vào `ApiResponse.correlationId` và vào log của mình. Hiện tại đây là thứ **đã
đặc tả nhưng chưa hiện thực** ở bất cứ đâu — và là khoản đầu tư rẻ nhất để gỡ lỗi trong cả dự án.

## 6. Giới hạn tần suất — chưa nối

`docs/ai/07-security-rbac.md` quy định 100 request/phút mỗi IP. Bộ `RequestRateLimiter` của Spring
Cloud Gateway cần **Redis**, mà stack hiện chưa có.

Muốn làm: thêm `spring-boot-starter-data-redis-reactive`, thêm một service Redis vào
`docker-compose.yml`, rồi:

```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 100
      redis-rate-limiter.burstCapacity: 200
      key-resolver: "#{@ipKeyResolver}"
```

Chừng nào chưa làm, hãy ghi thẳng trong báo cáo là chưa hiện thực thay vì nói khác đi.

## 7. Xử lý lỗi

Một `@Order(-2) ErrorWebExceptionHandler` để lỗi ở gateway dùng chung envelope với các service:

| Tình huống | Status | Mã |
|------------|--------|-----|
| Không có / sai token trên đường dẫn được bảo vệ | 401 | `UNAUTHORIZED` |
| Token hợp lệ nhưng không được phép vào route | 403 | `FORBIDDEN` |
| Service đích không có trong Eureka | 503 | `SERVICE_UNAVAILABLE` |
| Service đích quá thời gian chờ | 504 | `GATEWAY_TIMEOUT` |
| Sai thông tin đăng nhập | 401 | `AUTH_INVALID_CREDENTIALS` |
| Không gọi được organization-service lúc đăng nhập | 503 | `AUTH_UPSTREAM_UNAVAILABLE` |

## 8. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-G1 | Chỉ `/auth/**` và `/actuator/health` là công khai | `protectedPath_noToken_returns401` |
| BR-G2 | Token hết hạn bị từ chối | `expiredToken_returns401` |
| BR-G3 | Chữ ký bị sửa bị từ chối | `tamperedSignature_returns401` |
| BR-G4 | Refresh token không dùng thay access token được | `refreshTokenAsAccess_returns401` |
| BR-G5 | Đăng nhập ủy quyền cho organization-service | `login_callsAccountsVerify_neverTouchesDb` |
| BR-G6 | Sai mật khẩu và sai user cho phản hồi giống hệt nhau | `login_unknownUser_sameResponseAsWrongPassword` |
| BR-G7 | Header `X-User-*` do client gửi bị xóa | `forgedUserRoleHeader_isOverwritten` |
| BR-G8 | Tự sinh correlation id khi thiếu | `request_withoutCorrelationId_getsOneInResponse` |
| BR-G9 | Route phân giải qua Eureka, không host ghi cứng | `applicationYml_containsNoHttpLocalhostRoutes` |
| BR-G10 | organization-service chết ⇒ 503, không bao giờ đăng nhập stub | `login_upstreamDown_returns503NotToken` |

## 9. Điểm dễ sai

- WebFlux ≠ MVC. Có `spring-boot-starter-web` trong classpath của gateway sẽ **làm hỏng nó** — Spring Boot sẽ khởi động servlet container thay vì Netty. Giữ nguyên POM như hiện tại.
- `WebClient` gọi `lb://organization-service` cần `@LoadBalanced` trên bean builder, nếu không scheme `lb://` sẽ không phân giải được.
- Không bao giờ log request body của `/auth/login`. Nó chứa mật khẩu.
- Gateway là module duy nhất không có database. Đừng thêm Flyway, JPA hay datasource vào nó.
- Token hết hạn sau 30 phút theo cấu hình; frontend lưu nó trong `localStorage` và phải xử lý lỗi 401 bằng cách chuyển hướng về `/login` (`docs/ai/12-frontend.md`).
