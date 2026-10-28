# Đóng nghiệm thu backend — 27/10/2026

Tiếp nối [audit 17](17-backend-ownership-and-blocker-audit-2026-10-27.md), theo yêu cầu
Vinh hoàn tất backend. Baseline `38f226e1` (PR #548 đã merge). Không mở rộng MVP sang
Inpatient/Surgery hoặc thay ownership frontend. Tiêu chí là 06 và SC trong 10, không
là số lượng test hay số checkbox lịch sử.

## Thứ tự thực hiện

- [x] Chạy clean verify toàn reactor với PostgreSQL/RabbitMQ và packaged Support;
  lưu exit code, XML counts và hashes trước khi các lượt sau thay báo cáo.
- [x] Bổ sung kiểm thử PostgreSQL SC-04: hai transaction tạo ca trùng doctor/room,
  chỉ một ca/audit commit; STOPPED vẫn chặn overlap, ca liền nhau hợp lệ.
  SC-06 và replay/changed input SC-07 đã có trong ClinicalIntegrationTransactionTest;
  không xây lại bằng mock.
- [x] Chạy packaged sáu deployments với thu/hoàn và Report coverage trong môi trường
  cách ly. Calendar test dùng clock chỉ ở test Report; ghi rõ synthetic, không ký
  coverage cho dataset thật hoặc đổi giờ máy.
- [x] Chạy các profile phục hồi process Clinical/Lab/Billing, Notification identity
  upgrade, Pharmacy runtime và Report packaging/source. Mỗi profile có kết quả riêng;
  không suy process recovery mới của scheduling từ recovery outbox cũ.
- [x] Đối chiếu từng SC/WF với assertion thực thi; bổ sung hoặc sửa code nếu thiếu.
  Đã đóng H-03 JWT/Core và H-04 exact routes + actual refund race/replay/roles; các handoff khác
  giữ tiêu chí consumer/dataset/human cụ thể. Mapping không thay browser acceptance.
- [x] Review diff, kiểm contract và hoàn tất hồ sơ nhánh PR để ba owner frontend nghiệm thu.
  Mở PR từ `codex/backend-acceptance-closeout` vào master; CI trên PR là gate riêng.

## Ranh giới nghiệm thu

Kiểm thử cách ly có thể đóng quy tắc kỹ thuật đã chứng minh. Xác minh danh tính người
bệnh ngoài đời, UX ba người và quay lại ngày khác thuộc runbook 16. Report dataset demo
cần năm nguồn đối soát và ngày đã kết thúc theo operator workflow; clock của test không
thay thế điều kiện này. Không gọi các bước đó là dependency về quyền sửa backend.

## Lỗi phát hiện và sửa

`ClinicalIntegrationTransactionTest` dùng clock có nanosecond và PostgreSQL thật:
acquire đồng thời trả timestamp `.123456789Z` ở response đầu, `.123457Z` khi đọc
receipt để replay. Ca đóng chưa thu cũng trả `updatedAt` khác khi replay. Cả hai
đã được quan sát fail trước sửa. Clinical chuẩn hóa `now` về microsecond ở acquire/
resolve; không đổi IDs, fence, DTO, migration hay policy thời gian giữ suất.

CI của baseline sau rerun vượt lỗi Maven HTTP 502, nhưng six-service IT dừng ở
`AUTH_SESSION_REVOKED`: fixture tự ký ADMIN với subject ngẫu nhiên không còn hợp lệ
với kiểm tra phiên hiện hành. Ba packaged IT Clinical/Lab/Support chuyển sang local
bootstrap trên database disposable và đăng nhập thật qua Gateway; bỏ helper token
ADMIN cũ. Không tắt filter, sửa quyền hoặc ghi trực tiếp account SQL để cho test qua.

Lượt clean baseline: 7.721 tests, 0 failures/errors/skipped, gồm Support packaged IT.
Sau sửa, toàn Clinical: 447 tests, 0 failures/errors/skipped. Lượt chạy lại lớp PG
bổ sung assertion exact terminal replay được lưu riêng; không cộng các lượt lặp
thành số test duy nhất. Kết quả từng profile và giới hạn nguồn được cập nhật trong
[hồ sơ closeout](../acceptance/backend-closeout-2026-10-27/README.md), không gọi các
checkpoint này là chữ ký GO demo hay chứng nhận mọi dataset vận hành.

Tiếp tục 28/10: đã fast-forward master `c9954c7c` (PR #549 chỉ thay mobile), giữ nguyên
backend source/artifact đã kiểm. Clinical/Lab packaged cuối và các profile đã qua;
manifest ghi counts/exit từng lượt, original Pharmacy failure/skip và suite rerun đã
giải quyết. Không còn checkbox thực thi local của đợt closeout này; các tiêu chí
consumer/operator/manual chưa đủ bằng chứng vẫn nằm trong registry/runbook gốc.
