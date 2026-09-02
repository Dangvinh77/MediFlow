# GUI Standards và wireframe — bản khung bù tiến độ

**Phiên bản:** 0.1-draft, tạo ngày 02/09/2026  
**Trạng thái:** tài liệu mới để review; không phải bằng chứng rằng Task 2.1/2.2 đã hoàn thành trước hạn.

## 1. Mục tiêu chung

Web và Mobile dùng cùng ngôn ngữ thị giác, trạng thái nghiệp vụ và thuật ngữ tiếng Việt. UI ưu tiên khả năng đọc nhanh, lỗi không mơ hồ và giảm thao tác sai trong môi trường lâm sàng. Việc ẩn nút theo role chỉ là UX; backend vẫn phải kiểm quyền.

## 2. Nền tảng và điều hướng

### Web Next.js

- Sidebar theo phân hệ, header chứa cơ sở/khoa, người dùng, role và đăng xuất.
- Nội dung ưu tiên bảng dày nhưng dễ quét, bộ lọc cố định phía trên.
- Trang route chỉ compose feature; HTTP đi qua `src/lib/api.ts`.

### Mobile Flutter

- Bottom navigation tối đa 5 mục theo role; chức năng phụ nằm trong “Thêm”.
- Mỗi màn hình có AppBar rõ tiêu đề và hành động chính.
- HTTP đi qua `core/network/api_client.dart`; JWT lưu SecureStorage.

## 3. Design tokens đề xuất để chốt

| Token | Giá trị đề xuất | Cách dùng |
|---|---|---|
| Primary | `#0F766E` | CTA chính, trạng thái đang xử lý |
| Primary dark | `#115E59` | Hover/pressed |
| Info | `#2563EB` | Thông tin, liên kết |
| Success | `#15803D` | Hoàn tất/đã thanh toán/đã xuất |
| Warning | `#B45309` | Chờ xử lý, tồn kho thấp |
| Danger | `#B91C1C` | Lỗi, hủy, thất bại |
| Surface | `#FFFFFF` | Nền nội dung |
| Canvas | `#F8FAFC` | Nền ứng dụng |
| Text | `#0F172A` | Nội dung chính |
| Muted | `#64748B` | Nội dung phụ |
| Border | `#CBD5E1` | Viền/input/table |

- Font: ưu tiên `Inter`, fallback `Arial, sans-serif`; body Web 14–16 px, Mobile 14–16 sp.
- Bo góc: 8 px; spacing cơ sở: 4 px; focus ring tối thiểu 2 px.
- Không dùng màu làm tín hiệu duy nhất; badge luôn có text/icon.

## 4. Thành phần dùng chung

- Button: primary, secondary, danger, ghost; đủ default/hover/focus/disabled/loading.
- Input/select/date picker: label luôn hiển thị; lỗi nằm sát field.
- Table/list: sort, filter, pagination, skeleton, empty state, error state.
- Status badge: map 1–1 với enum backend, không tự dịch trạng thái tùy trang.
- Dialog xác nhận cho thao tác hủy/xóa/thanh toán/xuất thuốc.
- Toast chỉ cho phản hồi ngắn; lỗi cần hành động phải hiển thị inline.

## 5. Danh sách màn hình tối thiểu

| Nhóm | Web | Mobile | Role chính |
|---|---|---|---|
| Auth | Login | Login | Tất cả |
| Tổng quan | Dashboard theo role | Home theo role | Tất cả |
| Tổ chức | Khoa, nhân viên, tài khoản | Tra cứu khoa/nhân viên | ADMIN, MANAGER |
| Bệnh nhân | Danh sách, chi tiết, tạo/sửa | Tìm kiếm, chi tiết | ADMIN, DOCTOR, NURSE |
| Lịch hẹn | Lịch/list, form, trạng thái | Lịch theo ngày, chi tiết | ADMIN, DOCTOR, NURSE |
| Hồ sơ | Timeline hồ sơ, chẩn đoán | Lịch sử, chi tiết | DOCTOR, NURSE |
| Xét nghiệm | Hàng đợi, nhập kết quả | Hàng đợi, chi tiết | LAB_TECH, DOCTOR |
| Dược | Thuốc, tồn kho, đơn, xuất thuốc | Đơn chờ xuất, chi tiết | PHARMACIST, DOCTOR |
| Viện phí | Khoản phí, hóa đơn, thanh toán | Hóa đơn bệnh nhân | CASHIER, MANAGER |
| Thông báo | Danh sách/gửi | Inbox | ADMIN, NURSE, PATIENT |
| Báo cáo | Ngày, tháng, top thuốc | Tóm tắt KPI | ADMIN, MANAGER |

## 6. Wireframe văn bản

### Web — trang danh sách chuẩn

```text
┌ Sidebar ─────┐ ┌ Header: Khoa | User | Role | Đăng xuất ─────────────┐
│ Tổng quan    │ ├───────────────────────────────────────────────────────┤
│ Bệnh nhân    │ │ Tiêu đề                         [+ Hành động chính]  │
│ Lịch hẹn     │ │ [Tìm kiếm____] [Bộ lọc] [Ngày] [Làm mới]           │
│ Hồ sơ        │ │ ┌ Bảng dữ liệu / skeleton / error / empty ───────┐ │
│ Xét nghiệm   │ │ │ cột...                                          │ │
│ Dược         │ │ └─────────────────────────────────────────────────┘ │
│ Viện phí     │ │                  Phân trang                         │
│ Báo cáo      │ └───────────────────────────────────────────────────────┘
└──────────────┘
```

### Web — chi tiết bệnh nhân theo timeline

```text
[← Danh sách]  Họ tên + mã bệnh nhân             [Sửa]
[Thông tin] [Lịch hẹn] [Hồ sơ] [XN] [Đơn thuốc] [Hóa đơn]
┌ Thông tin định danh ┐  ┌ Trạng thái gần nhất ┐
└─────────────────────┘  └─────────────────────┘
┌ Timeline sự kiện y tế, mới nhất trước ──────────────────────┐
└──────────────────────────────────────────────────────────────┘
```

### Mobile — danh sách và hành động

```text
┌ AppBar: Bệnh nhân             🔍 ┐
│ [Tìm tên/CMND________________]  │
│ Bộ lọc dạng chip                 │
│ ┌ Avatar | Họ tên | Mã | trạng ┐ │
│ ├───────────────────────────────┤ │
│ └ Tap để mở chi tiết ──────────┘ │
│                            (+)    │
├ Home ─ Lịch ─ Việc ─ Inbox ─ Thêm┤
```

## 7. Trạng thái bắt buộc cho mọi màn hình dữ liệu

1. Loading: skeleton giữ đúng kích thước layout.
2. Empty: nói rõ chưa có dữ liệu và đưa hành động phù hợp quyền.
3. Error: thông báo tiếng Việt, mã lỗi/correlation id có thể copy, nút thử lại.
4. Success: cập nhật dữ liệu tại chỗ, không để người dùng đoán thao tác đã lưu chưa.
5. Forbidden: giải thích thiếu quyền, không tự động biến 403 thành đăng xuất.

## 8. Gate hoàn thành Task GUI

- [ ] Chốt token và component states.
- [ ] Có wireframe Web + Mobile cho toàn bộ 11 nhóm màn hình.
- [ ] Có prototype click-through cho 4 luồng: tiếp nhận, khám/XN, kê đơn–thanh toán–xuất, báo cáo.
- [ ] Review responsive ở 360, 768, 1280 và 1440 px.
- [ ] Review keyboard/focus/contrast và thuật ngữ y tế.
- [ ] Đối chiếu mọi form/field/status với SRS + backend DTO/enum.
- [ ] Có người duyệt, ngày duyệt và ảnh/export lưu trong repo.

