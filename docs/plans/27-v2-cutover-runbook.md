# MediFlow v2.0 Đợt 5–7 — Runbook bật cờ Lab (danh mục, duyệt, đính chính, mẫu, báo giá trị nguy hiểm)

Ngày lập: 2026-11-11. Nhánh: `develop/mediflow-v2.0`.
Kế hoạch: [`2026-11-11-v2-waves-5-7-lab-and-cutover.md`](../superpowers/plans/2026-11-11-v2-waves-5-7-lab-and-cutover.md).
Trạng thái: đã triển khai sau cờ, **chưa nghiệm thu runtime**; tài liệu này là quy trình bật/tắt, không chứng nhận kết quả.

## Cờ

Tất cả mặc định `false`.

| Service | Cờ | Biến môi trường | Phạm vi |
|---|---|---|---|
| Lab | `mediflow.v2.lab-catalog` | `MEDIFLOW_V2_LABCATALOG` | Danh mục dịch vụ, template, mẫu bệnh phẩm |
| Lab | `mediflow.v2.lab-review` | `MEDIFLOW_V2_LAB_REVIEW` | Nháp, gửi duyệt, trả lại, duyệt trả kết quả, worklist, thông báo nguy hiểm |
| Lab | `mediflow.v2.lab-amendment` | `MEDIFLOW_V2_LAB_AMENDMENT` | Đính chính kết quả đã trả bằng bản sửa mới |
| Clinical | `mediflow.v2.lab-catalog` | `MEDIFLOW_V2_LABCATALOG` | Chọn dịch vụ từ danh mục khi chỉ định |
| Notification | `mediflow.notification.lab-critical.enabled` | `MEDIFLOW_NOTIFICATION_LABCRITICAL_ENABLED` (relaxed binding, chưa khai báo trong application.yml) | Tiêu thụ `lab.critical.raised`, hàng đợi riêng `notification.lab-critical-v1.q`, DLQ `notification.lab-critical-v1.dlq` |

## Thứ tự bật

1. **Consumer trước.** Clinical, Notification, Report đã chấp nhận `lab.result.amended`. Bật
   `mediflow.notification.lab-critical.enabled` và kiểm hàng đợi `notification.lab-critical-v1.q` đã được tạo cùng DLQ.
2. **`lab-catalog`**: bật Lab và Clinical cùng lúc (Clinical chỉ gửi mã dịch vụ khi Lab xác thực được).
3. **`lab-review`**. Điều kiện: Organization đã cấp năng lực duyệt trả kết quả Lab cho **ít nhất hai** tài khoản
   LAB_TECH (đọc qua `GET /api/v2/org/lab-release-authority`; cấp bằng các endpoint năng lực nhân sự hiện có),
   vì người duyệt phải khác người nhập. Lab gọi Organization qua tên dịch vụ
   `mediflow.clients.organization-service`: profile `outpatient-mvp` đặt sẵn `core-service`; triển khai khác đặt
   `MEDIFLOW_ORGANIZATION_SERVICE`. Sai tên này thì mọi lần phát hành bị 403 `LAB_RELEASE_DENIED` (fail-closed).
   Khi cờ bật, xét nghiệm đã gắn template không còn nhập kết quả qua v1 (409 `LAB_REVIEW_REQUIRED`), kể cả khi chưa có nháp.
4. **`lab-amendment`**.

## Endpoint mới (Lab, Gateway đã bảo vệ)

| Phương thức | Đường dẫn | Mục đích |
|---|---|---|
| GET / PUT / POST | `/api/v2/lab/{testId}/report` (và các hành động con) | Nháp, gửi duyệt, trả lại, duyệt trả, đính chính (amendments) |
| GET, POST | `/api/v2/lab/{testId}/specimens` | Danh sách / ghi nhận mẫu |
| PUT | `/api/v2/lab/specimens/{specimenId}/decision` | Quyết định mẫu (nhận/từ chối) |
| GET | `/api/v2/lab/worklist` | Hàng việc Lab |
| GET | `/api/v2/lab/critical-notices` | Thông báo giá trị nguy hiểm |
| POST | `/api/v2/lab/{testId}/critical-notices/{noticeId}/acknowledgement` | Bác sĩ xác nhận đã nhận |

## Sự kiện

- `lab.result.created` v1: thêm `values`; thêm `releasedBy` khi trả qua luồng duyệt.
- `lab.result.amended` v1: bản sửa mới đã duyệt.
- `lab.critical.raised` v1: giá trị nguy hiểm.

Fixture: `backend/lab-service/src/test/resources/contracts/`.

## Quan sát

- Outbox Lab: bảng `lab_outbox_event` (retry/quarantine từ migration V4); kiểm bản ghi bị cách ly.
- DLQ từng hàng đợi consumer (Clinical, Report, Notification gồm `notification.lab-critical-v1.dlq`).
- `409 LAB_REVIEW_REQUIRED` trên `PUT /api/v1/lab/{id}/results` cũ khi xét nghiệm đã vào luồng duyệt.

## Rollback

- Tắt cờ theo thứ tự ngược: `lab-amendment`, `lab-review`, `lab-catalog` (Lab + Clinical), rồi lab-critical của Notification.
- Không xóa kết quả đã trả hay các bản sửa.
- Xét nghiệm đã vào luồng (`result_workflow_version=1`) giữ nguyên báo cáo của nó.
- Khi `lab-review` tắt, endpoint báo cáo trả `404 LAB_REVIEW_DISABLED`.

## Client cũ với backend mới

Khi các cờ tắt, endpoint v1 không đổi. Khi `lab-review` bật, client cũ gọi `PUT /api/v1/lab/{id}/results`
cho xét nghiệm thuộc luồng duyệt sẽ nhận `409 LAB_REVIEW_REQUIRED`.

## Giới hạn đã biết

- Lab không có giới tính/tuổi bệnh nhân: khoảng tham chiếu phụ thuộc giới/tuổi được gắn cờ `UNDETERMINED` và không bao giờ tạo thông báo nguy hiểm.
- Xác nhận giá trị nguy hiểm do bác sĩ của khoa chỉ định thực hiện (Lab không lưu bác sĩ chỉ định).
- Tài liệu Clinical hiển thị các bản sửa nhưng không hiển thị người duyệt trả.
- Giao diện ADMIN cấp năng lực duyệt trả Lab chưa xây (thuộc chủ frontend Organization).

Xem thêm [`known-limitations/README.md`](../known-limitations/README.md).
