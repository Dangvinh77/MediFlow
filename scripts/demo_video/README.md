# Khung quay demo MediFlow bằng HyperFrames

Khung nguồn được giữ sau khi dọn bản quay thử. **Không chứa video hoàn chỉnh hoặc dữ liệu phiên demo cũ.**

## Khởi động lần sau

1. Chạy `scripts/run/start-backend.bat` trong **07:00–16:45 giờ Việt Nam** nếu cần cả lượt nguồn tái khám. Chờ `FOLLOW-UP SOURCE READY`, `CALENDAR READY` và `READY`.
2. Chạy `scripts/run/start-frontend.bat`. Kịch bản nghiệp vụ: [DEMO_UI_TUNG_BUOC.md](../demo_25_10/DEMO_UI_TUNG_BUOC.md). Dùng ngày thực tế D; các UUID phải lấy lại từ UI của phiên mới.
3. Chạy **start-video.bat** trong thư mục này. Nó sao chép khung vào `target/mediflow-demo-video`, chỉ khởi tạo khi thư mục đó chưa tồn tại, rồi mở preview cổng 3017. Nó không khởi động lại dữ liệu bệnh viện.
4. Ghi các đoạn thao tác bằng bộ điều khiển trình duyệt đang có, theo ví dụ dưới.
5. Tại thư mục bản làm việc: `python assemble.py`, `npm run check`, kiểm ảnh với `npx hyperframes@0.9.2 snapshot --review`, rồi `npm run render -- --quality delivery -o demo.mp4` khi đủ các chương.
6. Đối chiếu `coverage-template.json` với đoạn đã ghi; không coi một đoạn có khung hình là nghiệp vụ đã thành công. Khi xong dừng preview bằng `npx hyperframes@0.9.2 preview --stop`, dừng frontend và chạy `scripts/run/clean-docker.bat` từ gốc dự án.

## Dành cho trợ lý ở lần quay tiếp theo

- Đọc skill `hyperframes`, dùng API điều khiển UI đã được công cụ cung cấp; không tự kết nối một thư viện trình duyệt khác.
- Khởi tạo/khôi phục trình duyệt theo tài liệu công cụ, chọn tab HTTP localhost hợp lệ. Không dùng tab lỗi có URL `data:`.
- Lấy capability `cdp`, đọc `documentation()` trước khi dùng. Nếu còn screencast từ phiên trước, dừng nó trước khi tạo recorder mới.
- Import `recorder.mjs` từ **bản làm việc** rồi gọi:

```js
var recorderModule = await import('file:///E:/DEV/Coding_Resource/Project/e_PROJECT/Semester4/Source/target/mediflow-demo-video/recorder.mjs');
var recorder = await recorderModule.createRecorder(tab, cdp,
  'E:/DEV/Coding_Resource/Project/e_PROJECT/Semester4/Source/target/mediflow-demo-video');
await recorder.segment('A · tên bước đang quay', async () => {
  // Chỉ thao tác các điều khiển đã quan sát được trên UI hiện tại.
  // Chờ kết quả thành công bằng điều khiển/thông báo thật trước khi kết thúc đoạn.
});
await recorder.close();
```

- Recorder thêm con trỏ trắng và vòng tròn đỏ qua `pointermove`/`pointerdown` thật. Đây là lớp đánh dấu phục vụ quay, không thay đổi nghiệp vụ. Gọi `close()` và tải lại tab khi kết thúc để gỡ cả listener.
- Bộ đọc khung hình phải có `await` nhường **250ms** giữa vòng lặp. Vòng lặp không nhường đã gây treo. Chỉ chạy pump trong một lời gọi đang được await; không để timer chạy nền qua các lượt công cụ.
- Nếu kết nối công cụ sinh hàng loạt cảnh báo đo đạc, lưu đoạn đang quay rồi làm mới phiên điều khiển và lấy lại tab. Recorder khôi phục manifest và số thứ tự ảnh; không ghi đè bộ ảnh trước.
- Với ô ngày, `locator.fill('YYYY-MM-DD')` từng không giữ được giá trị. `tab.setValue(index, 'YYYY-MM-DD')` trên index vừa quan sát đã hoạt động; kiểm lại ngày hiển thị trước lưu.
- Một số `summary` được AX trình bày thành button/checkbox nhưng DOM không có role đó. Quan sát lại rồi dùng index AX hiện hành; không bấm lại lệnh ghi khi chưa biết kết quả.
- `ffmpeg gdigrab` theo HWND từng chỉ thu khung đen; `gfxcapture` từng không xuất được frame. Không tốn thời gian lặp lại hai cách đó. CDP screencast đã thu được UI thật và vòng tròn click.
- Giữ cửa sổ thường, không toàn màn hình. Không đổi đồng hồ hay bỏ kiểm tra quyền/tài chính để quay cho đủ bước. Bước thay mật khẩu cần người dùng tự thao tác theo quy định của công cụ.
- Nếu nguồn tái khám lỗi `Billing execution authority could not be verified`, kiểm log trước. Trong phiên JAR cách ly, cấu hình môi trường **chỉ của phiên demo** sau đã giúp Clinical tìm Billing mà vẫn giữ xác thực:
  `SPRING_APPLICATION_JSON={"spring.cloud.openfeign.client.config.clinicalBillingExecutionClient.url":"http://127.0.0.1:28086"}`.
  Đây là cách khắc phục đã dùng khi quay thử, chưa phải sửa bền vững launcher. Không đưa địa chỉ này vào cấu hình chạy thật.
- Không dựng màn hình thành công để thay đoạn thiếu. Ba hành trình A/B/C, patient, admin và trưởng khoa đều phải có bằng chứng UI mới. Kịch bản có các bước tùy chọn; đánh dấu rõ bước nào được quay.

## Giữ gì, sinh gì

`scripts/demo_video` chỉ giữ mã khung, cấu hình, component con trỏ, đặc tả và hướng dẫn này. Tất cả ảnh, clip, manifest phiên, ID bệnh nhân, cache và MP4 sinh trong `target/mediflow-demo-video`; không commit chúng. Giữ các công cụ dùng chung Node/Python/FFmpeg/HyperFrames để lần sau không phải tải lại.
