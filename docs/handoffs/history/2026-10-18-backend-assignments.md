# Bảng giao việc backend cuối — 18/10/2026

**Phân loại ngày 26/10/2026: HISTORICAL CHECKPOINT — không là backlog hiện hành.** Các trạng thái ACTIVE/OPEN, owner và test counts
bên dưới thuộc checkpoint ghi trong tài liệu, không là kết luận trên revision hiện tại.
Xem [registry hiện hành](../README.md) để biết phần cần xác minh; di chuyển không có nghĩa đã nghiệm thu.

**Checkpoint 19/10:** bảng dưới là phân công lịch sử trước Gateway fix. Hoàng Anh đã
triển khai HA-CODE-01/02 tại `2805b07c`; Vinh retest policy 11/11 và Gateway 1.449/1.449.
Không giao lại việc sửa hai route. Hai test fixes vẫn thiếu trên remote master `4c19dd4a`;
trạng thái local recheck và giới hạn nghiệm thu xem [registry hiện hành](../README.md) và
[bundle 19/10](../../acceptance/backend-recheck-2026-10-19/README.md).

## Phân công và kết quả lịch sử 18/10

Baseline `025ef39e`, sau thực chạy regression và selected packaged/recovery profiles.
**Chưa đóng backend:** Gateway refund path đang lỗi; full regression có một failure test Report; Pharmacy V1 opt-in có một error do harness thiếu publisher config.
Toàn bộ kiểm thử do Vinh phụ trách theo phân công người dùng.

| Dev | Việc cần làm tiếp | Bằng chứng / điều kiện bàn giao |
|---|---|---|
| **Hoàng Anh** | **HA-CODE-01/02:** sửa đúng hai method/path refund rules tại Gateway và fixture route-role. Bàn giao commit cho Vinh. | [Handoff Gateway](../../acceptance/backend-closeout-2026-10-27/README.md#h-04--đã-đóng); 4 positive cases đỏ, 7 deny controls xanh. Không wildcard/nới role. |
| **Vinh** | **V-TEST-BUG-01/02:** sửa clock assertion Report và publication config của Pharmacy opt-in harness; **V-TEST-03/06:** nhận fixes Gateway, retest filter/token/HTTP/refund thật, full Report/backend regression và tổng hợp acceptance trên revision cuối. Ghi rõ phần chưa chạy. | [Bug test mới](2026-10-19-test-harness-findings.md), [kết quả và queue](../HANDOFF-VINH-BACKEND-VERIFICATION.md). Không giao lại full testing cho dev khác. |
| **Huy** | Chưa phát hiện production bug mới cần code qua các tests đã chạy. Không giao lại checklist Pharmacy/Report/Support đã đóng. | Vinh sửa lỗi clock test Report và harness Pharmacy V1; nếu tái hiện lỗi nghiệp vụ độc lập sẽ mở handoff production riêng cho Huy. |
| **Lộc** | Chưa phát hiện production bug mới cần code qua các tests đã chạy. Không giao lại LOC-01–06 đã merge. | Billing 1060, Notification 279 regression pass; Billing packaged publication recovery pass. Refund public gap thuộc Gateway. |

## Kết quả làm căn cứ

Regression 6.854 tests: 6.853 pass, 1 failure Report, 0 errors/skips. Hai reruns lớp Report
đều 8/8 pass, không thay kết quả full suite. Selected acceptance và reruns không cộng vào số này.
Build fresh artifacts thành công. Clinical/Billing/Lab recovery, Support packaged và six-service
acceptance pass. Lab đủ property pass; Pharmacy opt-in 1 error cần sửa harness. Historical clone rehearsal pass,
original volumes không đổi; source Report/Notification không có business rows. Chi tiết trong
[evidence cùng ngày](../../acceptance/backend-2026-10-18/README.md).

Chỉ đóng HA-CODE-01/02 khi implementation và kiểm thử Vinh đều đạt. Chỉ ký backend closeout
sau retest revision cuối, đối soát T01–T19/M6 có evidence và ghi rõ phạm vi/giới hạn.
Inpatient/Surgery vẫn deferred; compatibility tests không tạo việc tính năng mới.
