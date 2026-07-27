# Hướng dẫn PostgreSQL cho người mới (dùng qua Docker)

Viết cho người **chưa từng dùng PostgreSQL**. Dự án đang chạy Postgres bằng Docker — bạn không cần
cài Postgres vào máy.

---

## 1. Đang có cái gì, ở đâu

Chỉ cần nhớ ba dòng này:

```
Máy bạn (Windows)
 └── Docker
      ├── mediflow-postgres   ← server database, cổng 5432
      └── mediflow-rabbitmq   ← hàng đợi tin nhắn, cổng 5672
```

Bên trong `mediflow-postgres` có **8 database**, mỗi service một cái:

`mediflow_organization` · `mediflow_patient` · `mediflow_clinical` · `mediflow_lab` ·
`mediflow_pharmacy` · `mediflow_billing` · `mediflow_notification` · `mediflow_report`

**Thông tin kết nối** (dùng chung cho mọi công cụ):

| | |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Username | `postgres` |
| Password | `postgres` |

> Mật khẩu `postgres` chỉ dùng cho máy cá nhân lúc học. Đừng dùng ở nơi thật.

---

## 2. Bật, tắt, kiểm tra

Mở terminal tại thư mục gốc dự án (`D:\HK4\EProject\MediFlow`).

**Xem đang chạy chưa:**

```bash
docker compose ps
```

Thấy cả hai dòng có chữ `healthy` là ổn.

**Bật lên:**

```bash
docker compose up -d
```

Hai container có `restart: unless-stopped`, nên sau khi khởi động lại máy chúng **tự chạy lại**
(miễn là Docker Desktop đã mở). Chỉ khi nào bạn chủ động `docker compose down` thì chúng mới nằm im
cho tới lần `up` tiếp theo.

**Tắt đi** (dữ liệu vẫn còn):

```bash
docker compose down
```

**Xóa sạch làm lại từ đầu** — mất toàn bộ dữ liệu, tạo lại 8 database rỗng:

```bash
docker compose down -v
```

Sau lệnh trên nhớ `docker compose up -d` lại. Dùng khi database rối quá muốn làm lại.

---

## 3. Cách xem dữ liệu — dùng DBeaver (khuyên dùng)

Với người mới, giao diện dễ hơn dòng lệnh nhiều.

> **DBeaver không ảnh hưởng gì tới Docker.** Nó thuần là *client* — giống trình duyệt với website:
> cài trình duyệt không tạo ra website nào, chỉ để mở cái đã có. DBeaver không cài PostgreSQL
> server, không chiếm cổng 5432 (nó **kết nối tới** cổng đó), không sửa gì trong container. Gỡ ra
> lúc nào cũng được, dữ liệu vẫn nguyên trong Docker volume.
>
> ⚠️ **Đừng tải bộ cài PostgreSQL từ trang chủ chỉ để lấy pgAdmin** — bộ đó kèm cả server, cài vào
> sẽ tranh cổng 5432 với container và hỏng cả hai. DBeaver tải riêng từ dbeaver.io thì không dính
> vấn đề này.

1. Tải **DBeaver Community** (miễn phí): https://dbeaver.io/download/
2. Mở lên → **Database** → **New Database Connection** → chọn **PostgreSQL** → Next
3. Điền:
   - Host: `localhost`
   - Port: `5432`
   - Database: `mediflow_patient` *(chọn tạm một cái, lát nữa xem được hết)*
   - Username: `postgres`
   - Password: `postgres`
4. Bấm **Test Connection**. Lần đầu DBeaver hiện hộp thoại *"Download driver files — PostgreSQL"* →
   bấm **Download**. Đây chỉ là file `.jar` vài MB để DBeaver nói chuyện được với Postgres,
   **không phải** đang cài PostgreSQL server.
5. Thấy "Connected" → bấm **Finish**

Sau đó ở khung bên trái: mở rộng kết nối → **Databases** → thấy đủ 8 database. Vào một database →
**Schemas** → **public** → **Tables** là thấy bảng.

Muốn chạy SQL: bấm **SQL Editor** → **New SQL script**, gõ câu lệnh, nhấn **Ctrl+Enter**.

> Hiện các database **đang rỗng, chưa có bảng nào** — đó là bình thường. Bảng sẽ do Flyway tự tạo
> khi bạn chạy service Spring Boot lần đầu.

---

## 4. Cách xem dữ liệu — dùng dòng lệnh

Không cần cài gì, `psql` nằm sẵn trong container.

**Vào chế độ tương tác:**

```bash
docker exec -it mediflow-postgres psql -U postgres
```

Dấu nhắc đổi thành `postgres=#` là bạn đang ở trong psql. Gõ `\q` rồi Enter để thoát.

**Chạy một câu lệnh rồi thoát luôn** (tiện khi chỉ cần xem nhanh):

```bash
docker exec mediflow-postgres psql -U postgres -c "SELECT version();"
```

---

## 5. Lệnh psql cần biết

Các lệnh bắt đầu bằng `\` là lệnh riêng của psql, **không phải SQL**, và không cần dấu `;`.

| Lệnh | Tác dụng |
|------|----------|
| `\l` | liệt kê tất cả database |
| `\c mediflow_patient` | chuyển sang database `mediflow_patient` |
| `\dt` | liệt kê các bảng trong database hiện tại |
| `\d benh_nhan` | xem cấu trúc bảng `benh_nhan` (cột, kiểu, index) |
| `\du` | liệt kê user |
| `\x` | bật/tắt chế độ hiển thị dọc — rất hữu ích khi bảng nhiều cột |
| `\q` | thoát |

**Ví dụ một phiên làm việc điển hình:**

```
docker exec -it mediflow-postgres psql -U postgres

postgres=# \l                          -- xem có những database nào
postgres=# \c mediflow_patient         -- vào database bệnh nhân
mediflow_patient=# \dt                 -- xem có bảng nào
mediflow_patient=# \d benh_nhan        -- xem cấu trúc bảng
mediflow_patient=# SELECT * FROM benh_nhan LIMIT 10;
mediflow_patient=# \q
```

---

## 6. SQL cơ bản

Postgres dùng SQL chuẩn, giống MySQL/SQL Server ở phần lớn cú pháp. **Câu lệnh SQL phải kết thúc
bằng dấu `;`** — thiếu dấu này psql sẽ chờ mãi không chạy.

```sql
-- Xem toàn bộ dữ liệu một bảng
SELECT * FROM benh_nhan;

-- Chỉ lấy vài cột, giới hạn số dòng
SELECT ho_ten, ngay_sinh FROM benh_nhan LIMIT 10;

-- Lọc
SELECT * FROM benh_nhan WHERE gioi_tinh = 'F';

-- Đếm
SELECT count(*) FROM benh_nhan;

-- Sắp xếp
SELECT * FROM benh_nhan ORDER BY ho_ten;
```

Vài điểm khác biệt của Postgres so với MySQL bạn sẽ gặp:

| | MySQL | PostgreSQL |
|---|---|---|
| Nháy tên bảng/cột | `` `ten_bang` `` | `"ten_bang"` |
| Chuỗi | `'abc'` hoặc `"abc"` | **chỉ** `'abc'` — nháy kép là tên định danh |
| Phân biệt hoa thường | tên bảng không phân biệt | không phân biệt **trừ khi** để trong nháy kép |
| Kiểu tự tăng | `AUTO_INCREMENT` | dự án này dùng `UUID` nên không cần |

> Trong dự án này tên bảng và cột đều là **tiếng Việt không dấu, viết thường**: `benh_nhan`,
> `ma_benh_nhan`, `ho_ten`. Cứ gõ thường, đừng thêm nháy kép.

---

## 7. Bảng sẽ xuất hiện khi nào

Bạn **không cần tự tạo bảng**. Mỗi service có sẵn file SQL trong
`<service>/src/main/resources/db/migration/V1__init.sql`. Khi bạn chạy service lần đầu, **Flyway**
tự đọc file đó và tạo bảng.

Thứ tự để thấy bảng xuất hiện:

```bash
docker compose up -d
```

rồi chạy `eureka-server`, sau đó chạy service bạn muốn (ví dụ `patient-service`). Xong quay lại
DBeaver bấm refresh (F5) là thấy bảng.

---

## 8. Lỗi thường gặp

**`Connection refused` / DBeaver không kết nối được**
→ Container chưa chạy. Chạy `docker compose ps` kiểm tra; nếu trống thì `docker compose up -d`.

**`database "mediflow_xxx" does not exist`**
→ Tên database sai, hoặc volume được tạo trước khi danh sách database thay đổi. Kiểm bằng `\l`.
Nếu thiếu thật thì `docker compose down -v && docker compose up -d` để tạo lại từ script.

**`port 5432 is already allocated`**
→ Có một Postgres khác đang giữ cổng 5432 (thường do đã cài bản native). Tắt service đó đi, hoặc
đổi cổng trong `docker-compose.yml`.

**Gõ SQL xong nhấn Enter mà không thấy gì, dấu nhắc thành `postgres-#`**
→ Bạn quên dấu `;`. Gõ `;` rồi Enter.

**Lỡ tay xóa nhầm dữ liệu**
→ Không sao, dữ liệu hiện chỉ là dữ liệu thử. `docker compose down -v && docker compose up -d` là
sạch sẽ như mới.

---

## 9. Những thứ chưa cần quan tâm

Người mới hay bị rối vì đọc phải mấy thứ này. Dự án đã lo hết, bạn bỏ qua được:

- **Tự tạo bảng bằng tay** — Flyway lo
- **Tuning, index, vacuum** — không cần ở quy mô đồ án
- **Phân quyền user** — mọi thứ dùng chung user `postgres`
- **Backup** — dữ liệu là dữ liệu thử, xóa làm lại lúc nào cũng được

---

## Tóm tắt: bốn lệnh dùng hằng ngày

```bash
docker compose up -d
```

```bash
docker compose ps
```

```bash
docker exec -it mediflow-postgres psql -U postgres
```

```bash
docker compose down
```

Nhớ bốn lệnh này cộng với DBeaver là đủ dùng cho cả dự án.
