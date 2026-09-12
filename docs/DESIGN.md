# MediFlow — Design System

Version: 1.0 · Phạm vi: web Next.js, mobile Flutter và màn hình nghiệp vụ của toàn hệ thống.

## 1. Quyết định thiết kế

MediFlow sử dụng giao diện **sáng, rõ ràng, tập trung vào công việc**. Nền trung tính,
xanh dương cho thao tác, màu ngữ nghĩa cho trạng thái. Người dùng phải nhanh chóng biết
đang xem bệnh nhân nào, dữ liệu có trạng thái gì và có thể làm gì tiếp theo.

Đây là nguồn thống nhất cho quyết định giao diện mới. Các bản trong `design_themes/`
là nguồn tham khảo, không phải những theme được chọn riêng cho từng service.
Tài liệu này mô tả thiết kế mục tiêu; không có nghĩa source hiện tại đã triển khai đầy đủ.

| Nguồn | Kế thừa | Điều chỉnh cho MediFlow |
|---|---|---|
| [IBM](design_themes/DESIGN_ibm.md) | Phân cấp rõ, bố cục nghiệp vụ, đường viền và màu ngữ nghĩa | Bo góc vừa phải; chữ đủ đậm để đọc dữ liệu |
| [Apple](design_themes/DESIGN_apple.md) | Tiết chế màu, khoảng cách nhất quán, nội dung nổi bật | Không dùng hero lớn trong màn hình làm việc |
| [Binance](design_themes/DESIGN_binance.md) | Bảng số liệu, bộ lọc, phân biệt các lớp bề mặt | Không dùng vàng làm màu thương hiệu; tăng/giảm không tự mang ý nghĩa tốt/xấu |
| [BMW](design_themes/DESIGN_bmw.md) | Xanh dương, CTA rõ, phân nhóm thông tin | Không dùng chữ mảnh hay ảnh chiếm diện tích nghiệp vụ |
| [Spotify](design_themes/DESIGN_spotify.md) | Điều hướng ổn định, bề mặt dark phân lớp, tương tác cảm ứng | Dark là tùy chọn; không dùng xanh neon, nút pill đại trà hay bóng nặng |

Thứ tự ưu tiên khi có xung đột: contract và quyền backend → blueprint kỹ thuật trong
`docs/ai/` → quy tắc giao diện tại đây → theme tham khảo. Thiết kế không tạo thêm API,
quyền, trạng thái nghiệp vụ hoặc mở rộng phạm vi sửa code của một developer.

## 2. Nguyên tắc chung

1. Mọi module dùng cùng typography, spacing, navigation và component cơ sở.
2. Dữ liệu quan trọng có nhãn và đơn vị; màu không thay thế nội dung.
3. Mỗi vùng thao tác có một hành động chính rõ ràng; thao tác nguy hiểm tách riêng.
4. Giữ ngữ cảnh bệnh nhân, bộ lọc và trang đang xem xuyên suốt một luồng.
5. Ưu tiên bảng, biểu mẫu, danh sách và nhóm thông tin; chỉ thêm biểu đồ khi phục vụ câu hỏi cụ thể.
6. Tiếng Việt là ngôn ngữ mặc định; không hiển thị enum hoặc tên trường kỹ thuật làm nhãn.

## 3. Màu và semantic tokens

Light là mặc định. Dark dùng cùng cấu trúc và ý nghĩa, không tự đảo màu bằng bộ lọc CSS.
Hex dưới đây là quyết định của MediFlow, không phải sao chép nguyên palette thương hiệu nguồn.

| Token | Light | Dark | Sử dụng |
|---|---|---|---|
| `canvas` | `#F8FAFC` | `#0F172A` | Nền trang |
| `surface` | `#FFFFFF` | `#1E293B` | Card, sidebar, form |
| `surface-subtle` | `#F1F5F9` | `#273449` | Header bảng, vùng phụ |
| `text` | `#0F172A` | `#F8FAFC` | Nội dung chính |
| `text-muted` | `#475569` | `#CBD5E1` | Nội dung phụ vẫn phải đọc được |
| `border` | `#E2E8F0` | `#475569` | Phân cách trang trí |
| `control-border` | `#64748B` | `#94A3B8` | Biên input và control cần nhận diện |
| `primary` | `#1D4ED8` | `#93C5FD` | Nút chính, link, lựa chọn |
| `primary-hover` | `#1E40AF` | `#BFDBFE` | Hover nút chính |
| `on-primary` | `#FFFFFF` | `#0F172A` | Chữ trên nút chính |
| `selected-surface` | `#DBEAFE` | `#1E3A5F` | Menu hoặc hàng được chọn |
| `focus` | `#1D4ED8` | `#93C5FD` | Vòng focus bàn phím |

Badge và thông báo dùng cặp chữ/nền dưới đây. Nút phá hủy dùng chữ trắng trên `#B91C1C`
ở light, chữ `#0F172A` trên `#FCA5A5` ở dark.

| Ý nghĩa | Light: chữ / nền | Dark: chữ / nền |
|---|---|---|
| Thông tin | `#1E40AF` / `#DBEAFE` | `#BFDBFE` / `#1E3A5F` |
| Thành công | `#166534` / `#DCFCE7` | `#BBF7D0` / `#14532D` |
| Cần chú ý | `#92400E` / `#FEF3C7` | `#FDE68A` / `#451A03` |
| Lỗi hoặc nguy hiểm | `#991B1B` / `#FEE2E2` | `#FECACA` / `#450A0A` |
| Trung tính | `#475569` / `#F1F5F9` | `#CBD5E1` / `#273449` |

Không gán màu thương hiệu riêng cho service. Không dùng gradient trang trí, glassmorphism
hoặc ảnh nền dưới bảng. Trạng thái disabled có thuộc tính disabled thực và không chỉ giảm opacity.

## 4. Chữ, số và nội dung

- Font chuẩn khi triển khai: Inter có bộ ký tự tiếng Việt, đóng gói cùng ứng dụng.
  Fallback web: `system-ui, -apple-system, "Segoe UI", sans-serif`. Flutter dùng cùng font
  hoặc system fallback trong giai đoạn chưa có asset. Không phụ thuộc font độc quyền của theme nguồn.
- Trọng lượng: 400 cho nội dung, 500 cho nhãn, 600 cho tiêu đề; 700 chỉ khi cần nhấn mạnh.
- Sentence case; không viết toàn bộ nút bằng chữ hoa. Không ép letter-spacing âm cho nội dung.

| Vai trò | Cỡ / line-height | Weight |
|---|---|---|
| Tiêu đề trang | 28 / 36; mobile 24 / 32 | 600 |
| Tiêu đề nhóm | 20 / 28 | 600 |
| Nội dung, input | 16 / 24 | 400 |
| Bảng desktop, nhãn, nút | 14 / 20 | 400–600 |
| Chú thích phụ | 12 / 18 | 400 |

Không dùng 12px cho kết quả xét nghiệm, liều lượng hoặc số tiền chính. Cỡ web tính bằng
rem tương ứng; Flutter tôn trọng text scaling. Số trong bảng dùng tabular numerals và căn phải.

- Ngày hiển thị `dd/MM/yyyy`; giờ `HH:mm`; ghi rõ múi giờ khi ngữ cảnh có thể gây nhầm.
  Ngày thuần túy không chuyển múi giờ. Payload vẫn theo contract API.
- Tiền có mã/đơn vị tiền tệ; dùng định dạng `vi-VN`, không làm tròn mất độ chính xác backend.
- Giá trị 0 khác với thiếu dữ liệu. Dùng “Chưa có” hoặc “—” cho null theo ngữ cảnh.
- Ưu tiên mã nghiệp vụ và tên đã được API cung cấp. UUID có thể xem/sao chép trong chi tiết;
  không tự suy ra tên hoặc gọi dịch vụ ngoài contract để thay thế UUID.
- Nội dung dài được xuống dòng; nội dung cần thiết không chỉ tồn tại trong tooltip.

## 5. Kích thước và bố cục

Spacing scale: **4, 8, 12, 16, 24, 32, 48**. Padding card 24 desktop, 16 mobile;
khoảng cách field 16, giữa nhóm 24–32. Bo góc: 4 cho badge, 8 cho control,
12 cho card/dialog; pill chỉ dùng cho chip hoặc avatar. Không dùng card lồng card chỉ để trang trí.

Bề mặt thường dùng border 1px, không shadow. Menu/popover dùng shadow nhẹ
`0 4px 12px rgba(15,23,42,.12)`; dialog dùng `0 12px 32px rgba(15,23,42,.20)`
và backdrop. Dark ưu tiên border và chênh lệch surface để phân lớp.

| Viewport web | Bố cục |
|---|---|
| Dưới 768px | Một cột, padding 16; navigation drawer; bộ lọc xếp dọc |
| 768–1023px | Padding 24; drawer; form tối đa hai cột khi đủ chỗ |
| Từ 1024px | Sidebar 240px, header 64px, vùng nội dung linh hoạt, padding 24–32 |

Màn hình danh sách có thứ tự: tiêu đề + hành động chính → bộ lọc → kết quả hoặc trạng thái
→ phân trang. Nội dung rộng tối đa 1600px; form đọc/nhập dài tối đa 960px.
Không giới hạn hẹp các bảng cần nhiều cột chỉ để khớp một trang landing.

Web mobile dùng drawer. Flutter dùng tối đa 3–5 đích chính ở bottom navigation theo vai trò,
phần còn lại trong menu; màn hình chi tiết có back rõ ràng và tôn trọng safe area.
Chuyển bảng thành danh sách có nhãn khi phù hợp; bảng so sánh chỉ số có thể cuộn ngang
trong vùng riêng, không làm cả trang tràn ngang. Không ẩn dữ liệu quan trọng để vừa màn hình.

## 6. Component chuẩn

| Component | Quy tắc |
|---|---|
| Button | Primary, secondary, ghost, danger; cao tối thiểu 40px desktop, vùng chạm 48px mobile; loading giữ chiều rộng và chặn gửi lặp |
| Input / Select | Nhãn luôn hiển thị; placeholder chỉ là ví dụ; viền đầy đủ, lỗi ở dưới field; giữ giá trị khi lỗi |
| Form section | Tiêu đề ngắn, field liên quan thành nhóm; dấu bắt buộc có giải thích; lỗi tổng hợp dẫn về field |
| Table | Header có nhãn, hàng tối thiểu 48px mặc định; compact desktop 40px là tùy chọn; đơn vị và căn cột nhất quán |
| Pagination | Hiện trang và tổng; giữ bộ lọc đã áp dụng khi chuyển trang; lọc mới về trang đầu; khóa biên và khi tải |
| Badge | Chữ mô tả + màu ngữ nghĩa; không làm người dùng hiểu nhầm là nút |
| Tabs | Chuyển nhóm nội dung cùng đối tượng; trạng thái active có đường viền và nhãn, không chỉ màu |
| Dialog | Một quyết định ngắn; giữ focus bên trong, trả focus khi đóng, có nút đóng; không lồng dialog |
| Drawer | Chi tiết nhanh hoặc bộ lọc; form dài chuyển thành trang riêng |
| Notification | Toast cho phản hồi ngắn; lỗi cần xử lý hoặc dữ liệu quan trọng hiển thị inline và tồn tại đủ lâu |

Hàng bảng có hành động “Xem chi tiết” rõ ràng, hỗ trợ bàn phím. Filter đang gõ khác filter
đã áp dụng; chỉ đổi kết quả sau submit. Khi lỗi tải trang tiếp theo, giữ khả năng thử lại
và quay về trang trước. Dữ liệu cũ đang hiển thị phải được đánh dấu đang cập nhật.

Xác nhận thao tác không thể hoàn tác bằng tên hành động và đối tượng cụ thể.
Không hỏi xác nhận mọi lần lưu thông thường. Không báo thành công trước khi server xác nhận.

## 7. Các trạng thái bắt buộc

- **Loading:** skeleton đúng hình dạng hoặc thông báo ngắn; vùng nội dung có trạng thái busy.
- **Empty:** phân biệt chưa có dữ liệu với bộ lọc không có kết quả; cung cấp thao tác phù hợp quyền.
- **Error:** thông báo dễ hiểu, thử lại được; không xóa form đang nhập; không hiển thị stack trace.
- **Success:** nêu hành động vừa hoàn thành và cập nhật dữ liệu liên quan.
- **Unauthorized / forbidden:** phân biệt phiên đăng nhập không hợp lệ và thiếu quyền theo contract.
  Không tự đổi cơ chế xử lý 401/403 trong blueprint; thay đổi hành vi cần task thống nhất web/mobile.
- **Offline / stale:** ghi rõ dữ liệu chưa cập nhật; không biểu diễn ghi dữ liệu thất bại như đã lưu.

Backend là nguồn quyền quyết định. Menu và nút theo role chỉ cải thiện trải nghiệm;
không phải cơ chế bảo vệ dữ liệu. Không tạo role mới trong lớp giao diện.

## 8. Áp dụng theo nghiệp vụ

| Nhóm | Trọng tâm màn hình |
|---|---|
| Organization / Patient | Danh sách, tìm kiếm, thông tin nhận diện, nhóm thông tin liên hệ |
| Clinical — Appointment | Ngày giờ, khoa, bác sĩ, bệnh nhân và trạng thái; hành động theo lifecycle backend |
| Clinical — Medical record | Ngữ cảnh bệnh nhân ở đầu, thông tin khám, chẩn đoán và các nhóm dữ liệu liên quan |
| Lab | Trạng thái xử lý tách khỏi thanh toán; chỉ số, giá trị, đơn vị, khoảng tham chiếu hiển thị cùng nhau |
| Pharmacy | Thuốc, đơn vị, số lượng, lô/hạn dùng nếu contract có; xác nhận xuất/cấp theo quy trình |
| Billing | Các khoản, tổng tiền, tiền tệ, trạng thái; căn số nhất quán; chỉ cho phép thao tác backend hỗ trợ |
| Notification | Đã đọc/chưa đọc tách khỏi mức độ ưu tiên; liên kết đúng đối tượng khi được phép |
| Report | Bộ lọc thời gian/phạm vi, chỉ số có đơn vị và thời điểm dữ liệu; bảng thay thế cho biểu đồ |

Ví dụ ánh xạ enum hiện có: Appointment `PENDING` → “Chờ tiếp nhận” (warning),
`ARRIVED` → “Đã đến” (info), `CANCELLED` → “Đã hủy” (neutral).
Lab `PENDING` → “Chờ xử lý” (warning), `IN_PROGRESS` → “Đang thực hiện” (info),
`COMPLETED` → “Hoàn tất” (success), `CANCELLED` → “Đã hủy” (neutral).
Thanh toán là badge riêng. Enum lạ hiển thị “Không xác định”, không mặc định thành công.

Trạng thái xét nghiệm hoàn tất không đồng nghĩa kết quả bình thường. Không suy luận mức độ
nguy hiểm từ chuỗi giá trị hoặc khoảng tham chiếu; chỉ biểu diễn cờ đã có contract xác định.

## 9. Accessibility, chuyển động và hình ảnh

Mục tiêu nghiệm thu: chữ thường tương phản ít nhất 4.5:1; chữ lớn và biên control/focus
cần thiết ít nhất 3:1. Đây là yêu cầu kiểm tra khi triển khai, không phải tuyên bố toàn bộ
ứng dụng đã đạt chuẩn. Kiểm tra cả hai theme và từng trạng thái thực tế.

- Focus ring 2px, offset 2px; không xóa outline nếu chưa có thay thế nhìn thấy rõ.
- Label thật, heading theo cấp, table header có scope; icon-only button có tên truy cập.
- Tab order hợp lý; không dựa vào hover, kéo thả hoặc màu làm cách tương tác duy nhất.
- Hỗ trợ zoom 200%, text scaling, bàn phím và trình đọc màn hình.
- Transition 120–180ms cho màu/opacity; drawer tối đa 240ms; tôn trọng reduced motion.
- Không autoplay, parallax, hiệu ứng nhấp nháy hoặc chuyển động trang trí trong màn hình nghiệp vụ.
- Icon thống nhất nét và kích thước 20–24; không dùng emoji làm icon hệ thống.
- Ảnh minh họa chỉ ở trang giới thiệu hoặc empty state khi hữu ích; không dùng dữ liệu bệnh nhân
  thật trong mockup và ảnh demo. Bản in dùng nền trắng, chữ tối, bỏ navigation và control.

## 10. Ánh xạ triển khai và phối hợp team

Web tuân theo [Frontend blueprint](ai/12-frontend.md): semantic tokens trong
`frontend/src/app/globals.css`, dùng Tailwind utilities; component chung trong
`frontend/src/components/ui/` và `components/layout/`; UI nghiệp vụ trong `features/`.
Không cài component library hoặc data-fetching library chỉ vì theme nguồn sử dụng chúng.

Flutter tuân theo [Mobile blueprint](ai/14-flutter.md): map cùng tên/ngữ nghĩa token sang
`mobile/lib/core/theme/`, cấu hình ThemeData và component dùng chung; giữ kiến trúc Flutter
riêng. px thiết kế được chuyển thành logical pixels, không khóa kích thước chữ khi người dùng phóng to.

| Người phụ trách | Phần nghiệp vụ tương ứng khi được giao triển khai UI |
|---|---|
| Vinh | Appointment, Medical record, Lab |
| Huy | Pharmacy, Report |
| Hoàng Anh | Organization, Patient |
| Lộc | Billing, Notification |

Auth, navigation, token và component chung phải là task shared có người phụ trách được giao
rõ ràng. Tài liệu này không tự cấp quyền sửa shared path hoặc service của người khác.
Mỗi feature tái sử dụng component chung; không fork một bộ Button/Input riêng theo dev.

## 11. Thứ tự áp dụng và nghiệm thu

1. Tạo semantic tokens và UI cơ sở; đồng bộ Pagination hiện có vào cấu trúc chuẩn.
2. Chuẩn hóa shell, navigation và trạng thái loading/error/empty.
3. Áp dụng từng feature, giữ nguyên wire contract và kiểm tra quyền hiện có.
4. Hoàn thiện responsive và dark; chưa bật dark cho màn hình chưa kiểm tra đủ trạng thái.
5. Áp dụng cùng ngôn ngữ thiết kế khi xây dựng mobile.

Một màn hình hoàn thành khi:

- Dùng đúng token, typography, spacing; không có màu thương hiệu riêng cho module.
- Có loading, empty, error, success và trạng thái quyền thích hợp.
- Filter/phân trang không mất ngữ cảnh; form lỗi không mất nội dung.
- Bệnh nhân, số liệu, đơn vị, trạng thái và hành động quan trọng đọc được đầy đủ.
- Sử dụng được ở desktop, mobile, bàn phím và text scaling; focus nhìn thấy rõ.
- DTO/enum đúng contract; không có dữ liệu suy đoán hoặc thao tác ngoài quyền backend.
- Web chạy typecheck, lint phần thay đổi và build; Flutter chạy analyze và kiểm tra phù hợp.
- Có kiểm tra hành vi cho luồng quan trọng; build thành công không thay thế kiểm tra giao diện.

## 12. Chỉ dẫn ngắn cho designer và coding agent

> Thiết kế màn hình MediFlow theo `docs/DESIGN.md`: sáng mặc định, nền trung tính,
> primary xanh dương, chữ rõ, bo góc 8–12px, bảng và form ưu tiên dữ liệu. Dùng component
> chung, giữ ngữ cảnh bệnh nhân, thể hiện đầy đủ trạng thái tải/lỗi/rỗng/quyền. UI web dùng
> Tailwind và feature-based structure; mobile dùng theme Flutter cùng semantic tokens.
> Đọc contract backend trước khi đặt nhãn trạng thái hoặc thêm hành động. Các file
> `docs/design_themes/DESIGN_*.md` chỉ là nguồn tham khảo, không ghi đè quy tắc này.
