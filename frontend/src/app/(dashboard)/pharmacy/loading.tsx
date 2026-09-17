import { MediFlowLoader } from "@mediflow/loader";

/**
 * Giao diện chờ ở cấp route của phân hệ Dược.
 *
 * Tái sử dụng MediFlowLoader hiện có thay vì tạo loader mới.
 *
 * Phân biệt với loading của dữ liệu:
 * - File này phục vụ quá trình tải/chuyển route.
 * - Khi component gọi API trong trình duyệt, component đó
 *   vẫn phải quản lý trạng thái loading riêng.
 */
export default function PharmacyLoading() {
  return (
    <div
      // Thông báo trạng thái tải cho công cụ hỗ trợ đọc màn hình.
      role="status"
      aria-live="polite"
      aria-busy="true"
      className="flex min-h-[40vh] items-center justify-center"
    >
      <MediFlowLoader
        className="flex flex-col items-center gap-2 text-sm"
        holdDuration={0.6}
        label="Đang tải phân hệ Dược..."
        size={72}
        slideDuration={1}
        speed={1.5}
        stagger={0.1}
      />
    </div>
  );
}