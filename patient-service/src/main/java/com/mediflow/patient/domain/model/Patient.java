package com.mediflow.patient.domain.model;

import com.mediflow.patient.domain.exception.InvalidPatientDataException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Bệnh nhân — the patient aggregate.
 *
 * <p>Pure Java: no Spring, no JPA, no validation annotations. Every invariant is enforced here, in
 * the factory and in {@link #capNhat}, never in a setter — which is why there are no setters.
 * That is what lets this class be unit-tested in milliseconds with no Spring context.
 *
 * <p>{@code soCmnd} is immutable after creation: it identifies the person, and letting it change
 * would silently break the uniqueness rule the application layer enforces.
 */
public class Patient {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]{2,}$");
    private static final Pattern SO_DIEN_THOAI = Pattern.compile("^\\d{10,15}$");
    private static final Pattern BHYT = Pattern.compile("^\\d{2}-\\d{8}-\\d$");

    private final UUID maBenhNhan;
    private String hoTen;
    private LocalDate ngaySinh;
    private GioiTinh gioiTinh;
    private final String soCmnd;
    private String diaChi;
    private String soDienThoai;
    private String email;
    private String bhytSo;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Patient(UUID maBenhNhan, String hoTen, LocalDate ngaySinh, GioiTinh gioiTinh,
                    String soCmnd, String diaChi, String soDienThoai, String email, String bhytSo,
                    Instant createdAt, Instant updatedAt) {
        this.maBenhNhan = maBenhNhan;
        this.hoTen = hoTen;
        this.ngaySinh = ngaySinh;
        this.gioiTinh = gioiTinh;
        this.soCmnd = soCmnd;
        this.diaChi = diaChi;
        this.soDienThoai = soDienThoai;
        this.email = email;
        this.bhytSo = bhytSo;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * A brand-new patient. The id and timestamps are assigned by the persistence layer, so they
     * are null here — that is the difference between this and {@link #khoiPhuc}.
     */
    public static Patient taoMoi(String hoTen, LocalDate ngaySinh, GioiTinh gioiTinh, String soCmnd,
                                 String diaChi, String soDienThoai, String email, String bhytSo) {
        kiemTraHoTen(hoTen);
        kiemTraNgaySinh(ngaySinh);
        kiemTraGioiTinh(gioiTinh);
        kiemTraSoCmnd(soCmnd);
        kiemTraLienHe(soDienThoai, email, bhytSo);

        return new Patient(null, hoTen.trim(), ngaySinh, gioiTinh, soCmnd.trim(),
                diaChi, soDienThoai, email, bhytSo, null, null);
    }

    /**
     * Rebuilds an aggregate from stored data. Deliberately does <em>not</em> re-run the creation
     * rules: data already in the database was valid when written, and re-validating it would make
     * a tightened rule retroactively break reads of old rows.
     */
    public static Patient khoiPhuc(UUID maBenhNhan, String hoTen, LocalDate ngaySinh, GioiTinh gioiTinh,
                                   String soCmnd, String diaChi, String soDienThoai, String email,
                                   String bhytSo, Instant createdAt, Instant updatedAt) {
        return new Patient(maBenhNhan, hoTen, ngaySinh, gioiTinh, soCmnd,
                diaChi, soDienThoai, email, bhytSo, createdAt, updatedAt);
    }

    /**
     * Applies an update. Required fields are always replaced; optional fields keep their current
     * value when the argument is null, so a partial update cannot silently erase an address.
     * {@code soCmnd} is not a parameter — it cannot change.
     */
    public void capNhat(String hoTen, LocalDate ngaySinh, GioiTinh gioiTinh,
                        String diaChi, String soDienThoai, String email, String bhytSo) {
        kiemTraHoTen(hoTen);
        kiemTraNgaySinh(ngaySinh);
        kiemTraGioiTinh(gioiTinh);
        kiemTraLienHe(soDienThoai, email, bhytSo);

        this.hoTen = hoTen.trim();
        this.ngaySinh = ngaySinh;
        this.gioiTinh = gioiTinh;
        if (diaChi != null) this.diaChi = diaChi;
        if (soDienThoai != null) this.soDienThoai = soDienThoai;
        if (email != null) this.email = email;
        if (bhytSo != null) this.bhytSo = bhytSo;
    }

    /** Tuổi tính theo năm tròn. Convenience for the UI — not persisted. */
    public int tuoi() {
        return Period.between(ngaySinh, LocalDate.now()).getYears();
    }

    public boolean coBhyt() {
        return bhytSo != null && !bhytSo.isBlank();
    }

    // ---------- invariants ----------

    private static void kiemTraHoTen(String hoTen) {
        if (hoTen == null || hoTen.isBlank()) {
            throw new InvalidPatientDataException("PATIENT_HOTEN_REQUIRED",
                    "Họ tên không được để trống");
        }
        if (hoTen.trim().length() > 100) {
            throw new InvalidPatientDataException("PATIENT_HOTEN_REQUIRED",
                    "Họ tên không được vượt quá 100 ký tự");
        }
    }

    private static void kiemTraNgaySinh(LocalDate ngaySinh) {
        if (ngaySinh == null) {
            throw new InvalidPatientDataException("PATIENT_NGAYSINH_FUTURE",
                    "Ngày sinh không được để trống");
        }
        if (ngaySinh.isAfter(LocalDate.now())) {
            throw new InvalidPatientDataException("PATIENT_NGAYSINH_FUTURE",
                    "Ngày sinh không được ở tương lai");
        }
    }

    private static void kiemTraGioiTinh(GioiTinh gioiTinh) {
        if (gioiTinh == null) {
            throw new InvalidPatientDataException("PATIENT_GIOITINH_REQUIRED",
                    "Giới tính không được để trống");
        }
    }

    private static void kiemTraSoCmnd(String soCmnd) {
        if (soCmnd == null || soCmnd.isBlank()) {
            throw new InvalidPatientDataException("PATIENT_SOCMND_REQUIRED",
                    "Số CMND/CCCD không được để trống");
        }
        if (soCmnd.trim().length() > 20) {
            throw new InvalidPatientDataException("PATIENT_SOCMND_REQUIRED",
                    "Số CMND/CCCD không được vượt quá 20 ký tự");
        }
    }

    /** The three optional contact fields are only validated when present. */
    private static void kiemTraLienHe(String soDienThoai, String email, String bhytSo) {
        if (soDienThoai != null && !soDienThoai.isBlank() && !SO_DIEN_THOAI.matcher(soDienThoai).matches()) {
            throw new InvalidPatientDataException("PATIENT_SDT_INVALID",
                    "Số điện thoại phải gồm 10-15 chữ số");
        }
        if (email != null && !email.isBlank() && !EMAIL.matcher(email).matches()) {
            throw new InvalidPatientDataException("PATIENT_EMAIL_INVALID",
                    "Email không hợp lệ");
        }
        if (bhytSo != null && !bhytSo.isBlank() && !BHYT.matcher(bhytSo).matches()) {
            throw new InvalidPatientDataException("PATIENT_BHYT_INVALID",
                    "Số BHYT phải theo dạng XX-XXXXXXXX-X");
        }
    }

    // ---------- getters (no setters: state changes go through capNhat) ----------

    public UUID getMaBenhNhan() { return maBenhNhan; }
    public String getHoTen() { return hoTen; }
    public LocalDate getNgaySinh() { return ngaySinh; }
    public GioiTinh getGioiTinh() { return gioiTinh; }
    public String getSoCmnd() { return soCmnd; }
    public String getDiaChi() { return diaChi; }
    public String getSoDienThoai() { return soDienThoai; }
    public String getEmail() { return email; }
    public String getBhytSo() { return bhytSo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Deliberately excludes PII (CMND, BHYT, phone) — this may end up in a log. */
    @Override
    public String toString() {
        return "Patient{maBenhNhan=" + maBenhNhan + ", hoTen='" + hoTen + "'}";
    }
}
