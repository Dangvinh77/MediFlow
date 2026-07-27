# 08 — report-service

**Module** `report-service` · **Package** `com.mediflow.report` · **Cổng** 8088 · **DB** `mediflow_report` · **Tiền tố** `/api/v1/reports`

Đây là một **read model**. Nó không sở hữu dữ liệu giao dịch nào: mọi con số nó trả về đều là số liệu
tổng hợp dựng thuần từ event. Nó không bao giờ truy vấn database của service khác và không bao giờ
gọi REST sang service khác.

Nếu service này có lúc nào đó cần một JDBC URL hoặc một Feign client trỏ sang service khác, nghĩa là
thiết kế đã bị vi phạm.

## 1. Lược đồ — `V1__init.sql`

```sql
-- Bộ đếm theo ngày, khóa theo (ngay, ma_khoa). ma_khoa NULL = số liệu toàn viện.
CREATE TABLE BAO_CAO_KHAM (
    ma_bao_cao      UUID          PRIMARY KEY,
    ngay            DATE          NOT NULL,
    ma_khoa         UUID,                                 -- tham chiếu organization-service KHOA
    so_luong_kham   INT           NOT NULL DEFAULT 0,
    so_luong_xn     INT           NOT NULL DEFAULT 0,
    so_don_thuoc    INT           NOT NULL DEFAULT 0,
    doanh_thu       DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);
-- NULLS NOT DISTINCT để dòng toàn viện (ma_khoa IS NULL) cũng là duy nhất mỗi ngày
CREATE UNIQUE INDEX uq_bao_cao_kham_ngay_khoa
    ON BAO_CAO_KHAM (ngay, ma_khoa) NULLS NOT DISTINCT;

CREATE TABLE BAO_CAO_DOANH_THU (
    ma_bao_cao      UUID          PRIMARY KEY,
    thang           INT           NOT NULL,
    nam             INT           NOT NULL,
    ma_khoa         UUID,
    tong_doanh_thu  DECIMAL(15,2) NOT NULL DEFAULT 0,
    so_hoa_don      INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    CONSTRAINT ck_thang CHECK (thang BETWEEN 1 AND 12)
);
CREATE UNIQUE INDEX uq_bao_cao_doanh_thu
    ON BAO_CAO_DOANH_THU (nam, thang, ma_khoa) NULLS NOT DISTINCT;

CREATE TABLE THONG_KE_THUOC (
    ma_thong_ke   UUID          PRIMARY KEY,
    ma_thuoc      UUID          NOT NULL,
    ten_thuoc     VARCHAR(150)  NOT NULL,
    ngay          DATE          NOT NULL,
    ma_khoa       UUID,
    so_luong_xuat INT           NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ,
    CONSTRAINT uq_thong_ke_thuoc UNIQUE (ma_thuoc, ngay, ma_khoa)
);
CREATE INDEX idx_thong_ke_thuoc_ngay ON THONG_KE_THUOC (ngay);

-- Bắt buộc ở đây: thiếu nó thì một lần gửi lại làm hỏng mọi bộ đếm vĩnh viễn.
CREATE TABLE SU_KIEN_DA_XU_LY (
    event_id     UUID          PRIMARY KEY,
    routing_key  VARCHAR(100)  NOT NULL,
    xu_ly_luc    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
```

> `NULLS NOT DISTINCT` cần **PostgreSQL 15 trở lên**. Stack đang dùng PG 16 (`docker-compose.yml`)
> nên dùng được. Không có nó thì `(ngay, NULL)` sẽ không bị coi là trùng và bạn sẽ tích lũy hàng loạt
> dòng toàn viện trùng lặp.

## 2. Domain model

**`BaoCaoKham`** — `maBaoCao`, `ngay`, `maKhoa` (cho phép null), `soLuongKham`, `soLuongXn`,
`soDonThuoc`, `doanhThu`.

```java
public static BaoCaoKham khoiTao(LocalDate ngay, UUID maKhoa);
public void tangSoKham(int delta);
public void tangSoXetNghiem(int delta);
public void tangSoDonThuoc(int delta);
public void congDoanhThu(BigDecimal soTien);   // có thể âm khi bù trừ
```

**`BaoCaoDoanhThu`** — `thang`, `nam`, `maKhoa`, `tongDoanhThu`, `soHoaDon`.
**`ThongKeThuoc`** — `maThuoc`, `tenThuoc`, `ngay`, `maKhoa`, `soLuongXuat`.

Mọi bộ đếm đều phải chịu được **delta âm** — `payment.failed` sẽ đảo ngược doanh thu.

## 3. Port

```java
// out
public interface BaoCaoKhamRepositoryPort {
    /** Tìm-hoặc-tạo dòng (ngay, maKhoa). Phải an toàn khi nhiều consumer chạy đồng thời. */
    BaoCaoKham findOrCreate(LocalDate ngay, UUID maKhoa);
    BaoCaoKham save(BaoCaoKham b);
    Optional<BaoCaoKham> find(LocalDate ngay, UUID maKhoa);
    List<BaoCaoKham> findRange(LocalDate tu, LocalDate den, UUID maKhoa);
}
public interface BaoCaoDoanhThuRepositoryPort {
    BaoCaoDoanhThu findOrCreate(int nam, int thang, UUID maKhoa);
    BaoCaoDoanhThu save(BaoCaoDoanhThu b);
    List<BaoCaoDoanhThu> findByMonth(int nam, int thang);
}
public interface ThongKeThuocRepositoryPort {
    ThongKeThuoc findOrCreate(UUID maThuoc, String tenThuoc, LocalDate ngay, UUID maKhoa);
    ThongKeThuoc save(ThongKeThuoc t);
    List<ThongKeThuoc> topThuoc(LocalDate tu, LocalDate den, UUID maKhoa, int limit);
}
public interface ProcessedEventPort { boolean alreadyProcessed(UUID id); void markProcessed(UUID id, String rk); }

// in
public interface UpdateAggregateUseCase {
    void onMedicalRecordCreated(UUID eventId, LocalDate ngay, UUID maKhoa);
    void onLabResultCreated(UUID eventId, LocalDate ngay, UUID maKhoa);
    void onPrescriptionFilled(UUID eventId, LocalDate ngay, UUID maKhoa, List<DispensedItem> items);
    void onPaymentCompleted(UUID eventId, LocalDate ngay, UUID maKhoa, BigDecimal soTien);
    void onPaymentFailed(UUID eventId, LocalDate ngay, UUID maKhoa, BigDecimal soTien);   // delta âm
    void onStaffDepartmentChanged(UUID eventId, UUID maNhanVien, UUID cu, UUID moi);
}
public interface ReadReportUseCase {
    DailyReportDTO daily(LocalDate date, UUID maKhoa);
    MonthlyReportDTO monthly(int thang, int nam, UUID maKhoa);
    List<TopMedicineDTO> topMedicines(LocalDate tu, LocalDate den, UUID maKhoa, int limit);
}
```

## 4. DTO

```java
public record DailyReportDTO(LocalDate ngay, UUID maKhoa, int soLuongKham, int soLuongXn,
                             int soDonThuoc, BigDecimal doanhThu) {}

public record MonthlyReportDTO(int thang, int nam, UUID maKhoa, BigDecimal tongDoanhThu,
                               int soHoaDon, List<DailyReportDTO> chiTietTheoNgay) {}

public record TopMedicineDTO(UUID maThuoc, String tenThuoc, int tongSoLuong) {}
```

## 5. Luồng tổng hợp

Mọi consumer đều theo cùng bốn bước, **tất cả trong một transaction**:

```java
@RabbitListener(queues = "report.q")
@Transactional
public void on(Message msg) {
    UUID eventId = ...;
    if (processed.alreadyProcessed(eventId)) return;      // 1. khử trùng lặp TRƯỚC TIÊN
    var row = repo.findOrCreate(ngay, maKhoa);            // 2. tìm-hoặc-tạo
    row.tangSoKham(1);                                    // 3. cộng delta
    repo.save(row);
    processed.markProcessed(eventId, routingKey);         // 4. cùng transaction
}
```

Mỗi event cập nhật **hai dòng**: dòng của khoa (`ma_khoa = X`) và dòng toàn viện (`ma_khoa = NULL`).
Chỉ làm một trong hai sẽ khiến tổng số liệu không khớp nhau.

| Event | Tác động |
|-------|----------|
| `medicalrecord.created` | `so_luong_kham += 1` |
| `lab.result.created` | `so_luong_xn += 1` |
| `prescription.filled` | `so_don_thuoc += 1`; mỗi mặt hàng `THONG_KE_THUOC.so_luong_xuat += soLuong` |
| `payment.completed` | `doanh_thu += totalAmount`; theo tháng `tong_doanh_thu += totalAmount`, `so_hoa_don += 1` |
| `payment.failed` | `doanh_thu -= totalAmount`; theo tháng tương tự, `so_hoa_don -= 1` |
| `staff.department.changed` | chỉ cập nhật ảnh chụp nhân sự; V1 không đổi bộ đếm nào |

### `findOrCreate` khi nhiều consumer chạy song song

Hai event cho cùng `(ngay, maKhoa)` có thể đến cùng lúc. Hãy dựa vào unique index thay vì
kiểm-rồi-chèn:

```sql
INSERT INTO BAO_CAO_KHAM (ma_bao_cao, ngay, ma_khoa) VALUES (:id, :ngay, :maKhoa)
ON CONFLICT (ngay, ma_khoa) DO NOTHING;
```

rồi `SELECT ... FOR UPDATE`. Kiểu `findById` rồi `save` thông thường sẽ sinh lỗi trùng khóa khi tải cao.

## 6. Endpoint

| Method | Path | Trả về | Role |
|--------|------|--------|------|
| GET | `/api/v1/reports/daily?date&maKhoa` | `DailyReportDTO` | ADMIN, MANAGER |
| GET | `/api/v1/reports/monthly?month&year&maKhoa` | `MonthlyReportDTO` | ADMIN, MANAGER |
| GET | `/api/v1/reports/top-medicines?tuNgay&denNgay&maKhoa&limit` | `List<TopMedicineDTO>` | ADMIN, MANAGER |

`maKhoa` là tùy chọn ở cả ba: bỏ trống để lấy số liệu toàn viện, truyền vào để lấy của một khoa.
`limit` mặc định 10, tối đa 50.

## 7. Event

**Publish:** không có.

**Subscribe** — queue `report.q`, bind vào:
`medicalrecord.created` · `lab.result.created` · `prescription.filled` · `payment.completed` ·
`payment.failed` · `staff.department.changed`

## 8. Business rule → test

| ID | Quy tắc | Test |
|----|---------|------|
| BR-R1 | Số liệu tổng hợp theo ngày và theo khoa | `onMedicalRecordCreated_updatesDeptAndHospitalRows` |
| BR-R2 | Gửi lại không làm đếm hai lần | `onPaymentCompleted_sameEventTwice_revenueCountedOnce` |
| BR-R3 | `payment.failed` đảo ngược doanh thu | `onPaymentFailed_subtractsRevenue` |
| BR-R4 | Không bao giờ truy vấn service khác | `reportService_hasNoFeignClientOrExternalDataSource` |
| BR-R5 | Ngày không có dữ liệu trả về số 0, không phải 404 | `daily_noData_returnsZeroedReport` |
| BR-R6 | Bỏ trống `maKhoa` ⇒ trả dòng toàn viện | `daily_noMaKhoa_returnsNullKhoaRow` |
| BR-R7 | Top thuốc tôn trọng khoảng ngày và limit | `topMedicines_respectsDateRangeAndLimit` |
| BR-R8 | Nhiều event đầu tiên cùng ngày chỉ tạo một dòng | `findOrCreate_concurrent_createsSingleRow` |

BR-R4 đáng viết hẳn một test ArchUnit:

```java
noClasses().that().resideInAPackage("..report..")
    .should().dependOnClassesThat().resideInAnyPackage("..openfeign..", "..client..")
```

## 9. Điểm dễ sai

- Số liệu tổng hợp là **dữ liệu dẫn xuất**, không bao giờ là nguồn chuẩn. Nếu chúng lệch, cách sửa là phát lại event chứ không phải sửa tay con số. Hãy ghi rõ điều này trong báo cáo.
- Báo cáo là dữ liệu tham khảo — tuyệt đối không được quay ngược lại chi phối một quyết định nghiệp vụ (`docs/ai/services/report.md`).
- `so_hoa_don` bị trừ khi `payment.failed`; hãy chặn để nó không xuống dưới 0 nếu event tới không đúng thứ tự.
- **Không cần** job chạy cuối ngày: bộ đếm được duy trì tăng dần theo từng event. Chỉ thêm job khi về sau cần backfill dữ liệu cũ.
