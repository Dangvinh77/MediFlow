package com.mediflow.patient.domain.model;

import com.mediflow.patient.domain.exception.InvalidPatientDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit tests: no Spring, no mocks, no database.
 *
 * <p>This is the payoff of clean architecture — every business invariant of the patient aggregate
 * is verified here in milliseconds. If a rule needs a Spring context to test, it is in the wrong
 * layer (docs/ai/09-testing.md).
 */
class PatientTest {

    private static Patient hopLe() {
        return Patient.taoMoi("Nguyễn Văn A", LocalDate.of(1990, 1, 1), GioiTinh.M,
                "012345678", "Hà Nội", "0901234567", "a@example.com", null);
    }

    @Nested
    @DisplayName("taoMoi")
    class TaoMoi {

        @Test
        void taoMoi_duLieuHopLe_khongNemLoi() {
            assertThatCode(PatientTest::hopLe).doesNotThrowAnyException();
        }

        @Test
        void taoMoi_idVaTimestampLaNull_vìPersistenceSeGan() {
            Patient p = hopLe();
            assertThat(p.getMaBenhNhan()).isNull();
            assertThat(p.getCreatedAt()).isNull();
        }

        @Test
        void taoMoi_hoTenRong_throwsInvalidPatientData() {
            assertThatThrownBy(() -> Patient.taoMoi("  ", LocalDate.of(1990, 1, 1), GioiTinh.M,
                    "012345678", null, null, null, null))
                    .isInstanceOf(InvalidPatientDataException.class)
                    .hasMessageContaining("Họ tên");
        }

        // BR-P4
        @Test
        void taoMoi_ngaySinhTuongLai_throwsInvalidPatientData() {
            LocalDate mai = LocalDate.now().plusDays(1);
            assertThatThrownBy(() -> Patient.taoMoi("Nguyễn Văn A", mai, GioiTinh.M,
                    "012345678", null, null, null, null))
                    .isInstanceOf(InvalidPatientDataException.class)
                    .hasMessageContaining("tương lai");
        }

        @Test
        void taoMoi_ngaySinhHomNay_duocChapNhan() {
            assertThatCode(() -> Patient.taoMoi("Bé sơ sinh", LocalDate.now(), GioiTinh.F,
                    "999888777", null, null, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void taoMoi_gioiTinhNull_throwsInvalidPatientData() {
            assertThatThrownBy(() -> Patient.taoMoi("Nguyễn Văn A", LocalDate.of(1990, 1, 1), null,
                    "012345678", null, null, null, null))
                    .isInstanceOf(InvalidPatientDataException.class);
        }

        @Test
        void taoMoi_soCmndRong_throwsInvalidPatientData() {
            assertThatThrownBy(() -> Patient.taoMoi("Nguyễn Văn A", LocalDate.of(1990, 1, 1), GioiTinh.M,
                    "", null, null, null, null))
                    .isInstanceOf(InvalidPatientDataException.class)
                    .hasMessageContaining("CMND");
        }

        // BR-P2
        @Test
        void taoMoi_emailSaiDinhDang_throwsInvalidPatientData() {
            assertThatThrownBy(() -> Patient.taoMoi("Nguyễn Văn A", LocalDate.of(1990, 1, 1), GioiTinh.M,
                    "012345678", null, null, "khong-phai-email", null))
                    .isInstanceOf(InvalidPatientDataException.class)
                    .hasMessageContaining("Email");
        }

        // BR-P5
        @Test
        void taoMoi_soDienThoaiQuaNgan_throwsInvalidPatientData() {
            assertThatThrownBy(() -> Patient.taoMoi("Nguyễn Văn A", LocalDate.of(1990, 1, 1), GioiTinh.M,
                    "012345678", null, "0901", null, null))
                    .isInstanceOf(InvalidPatientDataException.class)
                    .hasMessageContaining("điện thoại");
        }

        // BR-P3
        @Test
        void taoMoi_bhytSaiDinhDang_throwsInvalidPatientData() {
            assertThatThrownBy(() -> Patient.taoMoi("Nguyễn Văn A", LocalDate.of(1990, 1, 1), GioiTinh.M,
                    "012345678", null, null, null, "123456789"))
                    .isInstanceOf(InvalidPatientDataException.class)
                    .hasMessageContaining("BHYT");
        }

        @Test
        void taoMoi_bhytDungDinhDang_duocChapNhan() {
            assertThatCode(() -> Patient.taoMoi("Nguyễn Văn A", LocalDate.of(1990, 1, 1), GioiTinh.M,
                    "012345678", null, null, null, "01-23456789-0"))
                    .doesNotThrowAnyException();
        }

        @Test
        void taoMoi_truongTuyChonNull_duocChapNhan() {
            assertThatCode(() -> Patient.taoMoi("Nguyễn Văn A", LocalDate.of(1990, 1, 1), GioiTinh.M,
                    "012345678", null, null, null, null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("capNhat")
    class CapNhat {

        // BR-P6 — the rule that most needs a test, because nothing in the type system enforces it
        @Test
        void capNhat_khongLamDoiSoCmnd() {
            Patient p = hopLe();
            String truoc = p.getSoCmnd();

            p.capNhat("Tên Mới", LocalDate.of(1985, 5, 5), GioiTinh.F,
                    "TP.HCM", "0912345678", "moi@example.com", null);

            assertThat(p.getSoCmnd()).isEqualTo(truoc);
        }

        @Test
        void capNhat_truongBatBuoc_luonDuocThayThe() {
            Patient p = hopLe();

            p.capNhat("Tên Mới", LocalDate.of(1985, 5, 5), GioiTinh.F, null, null, null, null);

            assertThat(p.getHoTen()).isEqualTo("Tên Mới");
            assertThat(p.getGioiTinh()).isEqualTo(GioiTinh.F);
        }

        /** Partial update must not silently erase an address that was already there. */
        @Test
        void capNhat_truongTuyChonNull_giuNguyenGiaTriCu() {
            Patient p = hopLe();

            p.capNhat("Tên Mới", LocalDate.of(1990, 1, 1), GioiTinh.M, null, null, null, null);

            assertThat(p.getDiaChi()).isEqualTo("Hà Nội");
            assertThat(p.getEmail()).isEqualTo("a@example.com");
        }

        @Test
        void capNhat_emailSai_throwsVaKhongLamHongTrangThai() {
            Patient p = hopLe();

            assertThatThrownBy(() -> p.capNhat("Tên Mới", LocalDate.of(1990, 1, 1), GioiTinh.M,
                    null, null, "sai-email", null))
                    .isInstanceOf(InvalidPatientDataException.class);

            assertThat(p.getHoTen()).isEqualTo("Nguyễn Văn A");   // chưa bị ghi đè
        }
    }

    @Nested
    @DisplayName("khoiPhuc")
    class KhoiPhuc {

        /**
         * Rebuilding from storage must not re-run creation rules: a row written before a rule was
         * tightened is still readable.
         */
        @Test
        void khoiPhuc_duLieuCuKhongHopLeTheoLuatMoi_vanDocDuoc() {
            assertThatCode(() -> Patient.khoiPhuc(UUID.randomUUID(), "Tên Cũ",
                    LocalDate.of(1970, 1, 1), GioiTinh.M, "001", null,
                    "0123", "email-cu-khong-hop-le", null, null, null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringKhongLoPii {

        /** toString() ends up in logs. CMND, BHYT and phone must never appear there. */
        @Test
        void toString_khongLoPii() {
            Patient p = hopLe();
            assertThat(p.toString())
                    .doesNotContain("012345678")      // CMND
                    .doesNotContain("0901234567")     // số điện thoại
                    .contains("Nguyễn Văn A");        // tên thì được, để còn lần ra bản ghi
        }
    }
}
