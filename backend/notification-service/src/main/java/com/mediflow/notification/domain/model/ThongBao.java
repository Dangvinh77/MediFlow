package com.mediflow.notification.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import com.mediflow.notification.domain.exception.NotificationAddressInvalidException;
import com.mediflow.notification.domain.exception.NotificationAlreadyFinalisedException;

import lombok.Getter;

/**
 * Một thông báo gửi tới bệnh nhân qua email/SMS/trong ứng dụng (aggregate). Service này không sở
 * hữu dữ liệu nghiệp vụ cốt lõi nào — đây thuần túy là bản ghi kết quả gửi.
 */
@Getter
public class ThongBao {

    // Regex kiểu RFC đơn giản — đủ dùng để chặn địa chỉ rõ ràng sai, không nhằm xác thực RFC 5322 đầy đủ.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern SDT_PATTERN = Pattern.compile("^0\\d{9,10}$");

    private final UUID maThongBao;
    private final UUID maBenhNhan;
    private final String tieuDe;
    private final String noiDung;
    private final LoaiThongBao loai;
    private final String diaChiNhan;
    private TrangThaiThongBao trangThai;
    private String lyDoThatBai;
    private int soLanThu;
    private final Instant ngayTao;
    private Instant ngayGui;

    private ThongBao(UUID maThongBao, UUID maBenhNhan, String tieuDe, String noiDung, LoaiThongBao loai,
                      String diaChiNhan, TrangThaiThongBao trangThai, String lyDoThatBai, int soLanThu,
                      Instant ngayTao, Instant ngayGui) {
        this.maThongBao = maThongBao;
        this.maBenhNhan = maBenhNhan;
        this.tieuDe = tieuDe;
        this.noiDung = noiDung;
        this.loai = loai;
        this.diaChiNhan = diaChiNhan;
        this.trangThai = trangThai;
        this.lyDoThatBai = lyDoThatBai;
        this.soLanThu = soLanThu;
        this.ngayTao = ngayTao;
        this.ngayGui = ngayGui;
    }

    public static ThongBao taoMoi(UUID maBenhNhan, String tieuDe, String noiDung,
                                   LoaiThongBao loai, String diaChiNhan) {
        // Bảo vệ nội bộ: tiêu đề/nội dung/kênh là bắt buộc cho mọi lối tạo, kể cả từ consumer sự
        // kiện (NotificationTemplates) không đi qua Bean Validation của SendNotificationRequest.
        if (tieuDe == null || tieuDe.isBlank()) {
            throw new IllegalArgumentException("Tiêu đề thông báo không được để trống");
        }
        if (noiDung == null || noiDung.isBlank()) {
            throw new IllegalArgumentException("Nội dung thông báo không được để trống");
        }
        if (loai == null) {
            throw new IllegalArgumentException("Phải xác định kênh gửi thông báo");
        }
        // BR-N1/BR-N2: EMAIL/SMS bắt buộc địa chỉ hợp lệ theo đúng kênh; IN_APP không cần địa chỉ.
        if (loai == LoaiThongBao.EMAIL && !emailHopLe(diaChiNhan)) {
            throw new NotificationAddressInvalidException("Địa chỉ email không hợp lệ: " + diaChiNhan);
        }
        if (loai == LoaiThongBao.SMS && !sdtHopLe(diaChiNhan)) {
            throw new NotificationAddressInvalidException("Số điện thoại không hợp lệ: " + diaChiNhan);
        }
        return new ThongBao(null, maBenhNhan, tieuDe, noiDung, loai, diaChiNhan,
                TrangThaiThongBao.PENDING, null, 0, null, null);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc tạo. */
    public static ThongBao restore(UUID maThongBao, UUID maBenhNhan, String tieuDe, String noiDung,
                                    LoaiThongBao loai, String diaChiNhan, TrangThaiThongBao trangThai,
                                    String lyDoThatBai, int soLanThu, Instant ngayTao, Instant ngayGui) {
        return new ThongBao(maThongBao, maBenhNhan, tieuDe, noiDung, loai, diaChiNhan, trangThai,
                lyDoThatBai, soLanThu, ngayTao, ngayGui);
    }

    /** Đánh dấu gửi thành công (BR-N3/BR-N4). Không cho gửi lại thông báo đã kết thúc (BR-N7). */
    public void danhDauDaGui(Instant thoiDiem) {
        if (!coTheGui()) {
            throw new NotificationAlreadyFinalisedException("Thông báo đã kết thúc, không thể gửi lại");
        }
        this.trangThai = TrangThaiThongBao.SENT;
        this.ngayGui = thoiDiem;
        this.soLanThu++;
    }

    /** Đánh dấu gửi thất bại — kết quả nghiệp vụ, không phải lỗi hạ tầng (BR-N4). */
    public void danhDauThatBai(String lyDo) {
        if (!coTheGui()) {
            throw new NotificationAlreadyFinalisedException("Thông báo đã kết thúc, không thể gửi lại");
        }
        this.trangThai = TrangThaiThongBao.FAILED;
        this.lyDoThatBai = lyDo;
        this.soLanThu++;
    }

    /** {@code true} khi còn ở {@code PENDING} — chưa từng gửi hoặc chưa kết thúc (BR-N7). */
    public boolean coTheGui() {
        return trangThai == TrangThaiThongBao.PENDING;
    }

    /** BR-N1 — kênh EMAIL chỉ hợp lệ khi địa chỉ khớp định dạng email. */
    public static boolean emailHopLe(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /** BR-N2 — kênh SMS chỉ hợp lệ khi số điện thoại có dạng {@code 0} + 9–10 chữ số. */
    public static boolean sdtHopLe(String sdt) {
        return sdt != null && SDT_PATTERN.matcher(sdt).matches();
    }
}
