# 03 — Checklist xác nhận đề xuất hoàn thiện Clinical và tương tác Lab

> Cập nhật: **10/11/2026** · Baseline source đã đối chiếu: **3f3dcb8a**.
> Phạm vi: **Clinical ngoại trú và phần Lab liên quan: danh mục, mẫu kết quả, nhận mẫu, duyệt/trả kết quả về hồ sơ**, trong bối cảnh cơ sở y tế Việt Nam.
> Mục đích: **để bạn chọn từng đề xuất có tiến hành sửa hay không**.
> Ngày **10/11/2026**, người dùng đồng ý **toàn bộ 93 đề xuất phần 1–17**. Cùng ngày, người dùng đồng ý thêm **24 mục ảnh hưởng liên service ở phần 18–21**, chốt **cấm tự duyệt kết quả Lab** và **xây quá trình điều trị đầy đủ (REC-04)**. Kế hoạch: [roadmap v2.0](../superpowers/plans/2026-11-10-mediflow-v2-roadmap.md).

## Phạm vi và cách đọc

Tài liệu tập trung vào các nghiệp vụ thuộc Clinical service, từ tổ chức lịch và tiếp nhận
đến khám, kết luận và quản lý hồ sơ. Hồ sơ bệnh nhân là điểm tra cứu lâu dài; hồ sơ khám
là nội dung chuyên môn của từng lượt, có người chịu trách nhiệm và vòng đời riêng.
Định danh bệnh nhân vẫn do Patient/Core làm chủ; việc Clinical hiển thị thông tin không
chuyển quyền quản lý định danh sang Clinical.

Các quyết định tập trung vào:

- Ca làm việc, buổi nhận khám, sức chứa, quyền quản lý và lịch cá nhân bác sĩ.
- Đăng ký tại quầy/đặt trước, giữ suất, có mặt, hàng chờ và đổi lịch/bác sĩ.
- Điều kiện bắt đầu khám, chỉ định, theo dõi kết quả và kết thúc lượt tại Clinical.

- Cấu trúc lịch sử, liên kết lượt khám/tái khám và nhóm quá trình điều trị khi cần.
- Quyền đọc liên khoa, quyền ghi của từng bác sĩ và tài liệu của đồng nghiệp.
- Hồ sơ đã hoàn tất, bổ sung/đính chính và giữ nguyên nội dung gốc.
- Bàn giao bác sĩ, hội chẩn và ghi nhận nhiều lần khám trong ngày.
- Hiển thị đơn thuốc, kết quả và giấy hẹn **như tài liệu liên quan trong hồ sơ**.
- Chuyển đổi dữ liệu hồ sơ hiện có khi triển khai các mục bạn chọn.
- Phân loại xét nghiệm, mẫu nhập/trả kết quả, xử lý bệnh phẩm và luồng Lab → Clinical theo yêu cầu bổ sung.

Thu/hoàn tiền, tính BHYT, quản lý kho/cấp thuốc, báo cáo và vận hành hạ tầng không được mở
rộng thành checklist riêng. Clinical sử dụng hợp đồng với các nguồn đó để kiểm điều kiện
hoặc hiển thị dữ liệu; phần 13 mô tả các điểm phối hợp. Riêng Lab được mở rộng có chủ đích
ở phần 14–17 theo yêu cầu của bạn: tập trung nhập liệu và trao đổi kết quả đúng nghiệp vụ,
chưa xây một LIS đầy đủ, kết nối máy xét nghiệm hoặc hệ thống quản lý chất lượng toàn diện.

### Checklist là quyết định của bạn

Các lựa chọn dùng task list chuẩn Markdown (`- [ ]` / `- [x]`) ngay trước mỗi bảng,
để editor có hỗ trợ task list có thể bấm chọn. Checkbox trong ô bảng không được nhiều
editor hỗ trợ, nên bảng giữ mã để đối chiếu nội dung; trạng thái chỉ nằm ở task list.

- Checkbox chưa chọn (`- [ ]`): chưa quyết định, đang chờ bạn xác nhận.
- Checkbox đã chọn (`- [x]`): bạn đồng ý tiến hành đề xuất; **không có nghĩa đã sửa xong hay kiểm thử đạt**.
- Nếu không đồng ý làm, ghi “Không làm” kèm mã; không dùng checkbox trống để biểu thị từ chối.
- Nếu muốn hoãn, ghi “Hoãn” kèm mã; checkbox giữ chưa chọn.

Bạn có thể phản hồi bằng mã: **“Đồng ý REC-01, ACL-03, AMD-02; không làm REC-04; hoãn
COL-02.”** Có thể xác nhận kèm điều kiện. Mục bổ sung sau lần xác nhận toàn bộ vẫn chờ quyết định;
mức ưu tiên không tự được hiểu là đồng ý triển khai.

Giữ mã các đề xuất hồ sơ đã có, bổ sung các nhóm CL-SCH, CL-REG, CL-QUE, CL-EXM và CL-INT
cho lịch và luồng Clinical. Những mục ngoài Clinical và phần Lab được bổ sung vẫn được loại khỏi tài liệu. Không
có mục nào được tự đánh dấu đồng ý khi mở rộng phạm vi tài liệu.

### Hiện trạng và mức ưu tiên

- **[SRC]**: đã đối chiếu source của đường đi liên quan, không đồng nghĩa vừa chạy runtime.
- **[DOC]**: được ghi trong tài liệu hiện hành; không tự chứng minh đã triển khai.
- **[AUDIT]**: cần rà soát thêm trước khi kết luận thiếu và sửa; nếu đã đúng thì giữ nguyên.
- **[ĐỀ XUẤT]**: cách tổ chức khuyến nghị, chưa phải entity/API hoặc quy tắc đã được duyệt.

**P0:** giữ đúng bệnh nhân, quyền thao tác, nội dung gốc và điều kiện khám/giữ suất.
**P1:** hoàn thiện các nghiệp vụ Clinical thường sử dụng. **P2:** tăng chiều sâu sau khi nền tảng ổn định. Đây là khuyến nghị thứ tự để bạn
chọn, không phải phân hạng pháp lý hoặc yêu cầu làm toàn bộ.

Các đoạn “Kết quả mong đợi sau khi sửa” giúp bạn hình dung hành vi được đề xuất. Chúng
không đổi ý nghĩa checkbox thành nghiệm thu. Tiến độ và kết quả kiểm tra sẽ theo dõi riêng.

### Những nguyên tắc dùng để đánh giá đề xuất

Đọc lịch sử của đồng nghiệp có thể phục vụ điều trị, nhưng không tạo quyền sửa nội dung
của đồng nghiệp. Sau khi kết thúc lượt, diễn biến mới cần được ghi đúng lần tiếp nhận;
sai sót của bản cũ cần cách đính chính có lưu vết. Không đồng nhất hoàn tất lượt khám với
đã hoàn thiện mọi bước xác nhận và lưu trữ pháp lý. [Luật KBCB][L01], [TT13][L02].

Các mô hình Episode, timeline và phân chia dữ liệu dưới đây là lựa chọn thiết kế phù hợp
với dự án, không phải cấu trúc phần mềm bắt buộc theo luật. Ví dụ quy chế bệnh viện chỉ
minh họa thực tế tại cơ sở đó. Tài liệu không thay thế đánh giá tuân thủ khi triển khai thật.

## 1. Cấu trúc hồ sơ bệnh nhân và lịch sử điều trị

**Kết quả cần đạt:** một trang xem lịch sử lâu dài nhưng không làm mất ranh giới từng lần
khám, từng chỉ định và người chịu trách nhiệm.

**Chọn đề xuất để tiến hành:**

- [x] **REC-01**
- [x] **REC-02**
- [x] **REC-03**
- [x] **REC-04**
- [x] **REC-05**
- [x] **REC-06**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| REC-01 | Đặt hồ sơ bệnh nhân làm điểm tra cứu; hiển thị dòng thời gian các lượt theo ngày, khoa, bác sĩ, loại lượt và kết quả. | [SRC] Trang records lấy danh sách theo patientId; bảng đang phẳng, chưa có nhóm quá trình điều trị/cột khoa theo rà soát hiện tại. | [Bảng hồ sơ][C08], [Giới hạn quyền đọc][D10] | P1 |
| REC-02 | Giữ một MedicalRecord cho nội dung lượt; không biến thành một tài liệu duy nhất được các bác sĩ sửa xuyên nhiều năm. | [SRC] Record có patientId, doctorId, departmentId, appointmentId, OPEN/COMPLETED; đây là nền tảng cần bảo toàn. | [Record][C01], [Giới hạn đa bác sĩ][D11] | P0 |
| REC-03 | Dùng liên kết lượt trước/lời hẹn cho MVP; chỉ bổ sung CareEpisode khi cần nhóm một vấn đề điều trị qua nhiều lượt. | [SRC/DOC] Có tái khám và nguồn lời hẹn; chưa có CareEpisode nghiệp vụ như đề xuất cũ. Không suy tên field episode ở contract đồng nghĩa aggregate đã tồn tại. | [Registration][C04], [Giới hạn đa bác sĩ][D11], [Contract][D05] | P1 |
| REC-04 | Nếu có Episode: ghi lý do mở, vấn đề theo dõi, người phụ trách, các lượt và quyết định kết thúc; cho nhiều vấn đề song song. | [ĐỀ XUẤT] Chưa triển khai. Không gộp mọi bệnh của một bệnh nhân cùng khoa thành một đợt duy nhất. | [Giới hạn đa bác sĩ][D11], [Business flows][D03] | P2 |
| REC-05 | Bỏ ý tưởng tự kết thúc quá trình chỉ vì 180 ngày không tái khám; dùng nhắc rà soát và quyết định có lý do. | [DOC] Tài liệu 01 có phương án lịch sử cần rà lại khi chốt thiết kế; chưa là căn cứ cho job production. Không có căn cứ được kiểm chứng ở đây cho mốc chung 180 ngày. | [Giới hạn đa bác sĩ][D11] | P1 |
| REC-06 | Rà nội dung khám: lý do, tiền sử, dị ứng, dấu hiệu sinh tồn, khám, chẩn đoán, chỉ định, kết luận, dặn dò và người thực hiện; bổ sung có cấu trúc theo phạm vi chuyên khoa. | [SRC] Record đã xem có symptoms/diagnoses/disposition, chưa đại diện toàn bộ bộ biểu mẫu. [AUDIT] Một số thông tin có thể nằm ở module khác; cần inventory trước thêm field. | [Record][C01], [TT13][L02] | P1 |

**Mô hình khuyến nghị:** Patient giữ định danh; các lượt giữ nội dung chuyên môn và nghĩa
vụ riêng; timeline là góc nhìn tổng hợp. Nhóm lần đến và nhóm quá trình điều trị là hai
nhu cầu khác nhau, có thể bổ sung từng bước, không bắt buộc tạo thêm service.

## 2. Quyền xem liên khoa và quyền ghi của từng bác sĩ

**Kết quả cần đạt:** bác sĩ có đủ lịch sử để điều trị nhưng không được biến ghi chép của
đồng nghiệp thành ghi chép của mình. Theo yêu cầu mới nhất, không khóa đọc tuyệt đối theo khoa.

**Chọn đề xuất để tiến hành:**

- [x] **ACL-01**
- [x] **ACL-02**
- [x] **ACL-03**
- [x] **ACL-04**
- [x] **ACL-05**
- [x] **ACL-06**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| ACL-01 | Xác lập chính sách đọc liên khoa theo nhiệm vụ; áp dụng cho cả danh sách và chi tiết, kiểm backend và lưu vết. | [SRC] Hai GET record cho ADMIN/DOCTOR/NURSE, chưa kiểm quan hệ điều trị/khoa ở use case. Đọc rộng hiện có chưa chứng minh kiểm soát mục đích. | [Record service][C02], [Đối chiếu quyền][D10] | P0 |
| ACL-02 | Bảo toàn quyền ghi theo tác giả/phân công lượt đang mở; không thay guard thành “cùng khoa thì sửa được”. | [SRC] Có guard bác sĩ được phân công và trạng thái lượt; cần regression bác sĩ cùng/khác khoa và bác sĩ từng khám bệnh nhân trước đây. | [Record service][C02], [Record][C01] | P0 |
| ACL-03 | Thống nhất đọc hồ sơ với giấy hẹn, đơn, kết quả và tệp liên quan; mỗi context kiểm quyền phù hợp, không chuyển toàn bộ sang public. | [SRC] Giấy hẹn có kiểm tác giả riêng nên khác quyền đọc record. [AUDIT] Chưa kết luận toàn bộ tài liệu con có cùng vấn đề. | [Follow-up service][C03], [Đối chiếu quyền][D10] | P1 |
| ACL-04 | ADMIN đọc toàn cơ sở trong demo phải đại diện nhiệm vụ được giao; tách quản trị kỹ thuật khỏi khai thác hồ sơ khi tiến tới thực tế. | [SRC/DOC] ADMIN đang có quyền đọc rộng, nghiệp vụ quản lý và kỹ thuật chưa được mô tả thành ma trận riêng trong lần rà soát này. | [Đối chiếu quyền][D10], [Luật KBCB][L01] | P1 |
| ACL-05 | Chốt NURSE xem những gì theo nhiệm vụ tiếp nhận/chăm sóc; bệnh nhân chỉ xem dữ liệu của mình qua API đã nghiệm thu. | [SRC] NURSE cũng đọc hai GET record. [AUDIT] Không suy PATIENT có cùng endpoint hoặc mọi điều dưỡng cần cùng phạm vi. | [Đối chiếu quyền][D10], [Bảo mật][D04] | P0 |
| ACL-06 | Từ chối khi authority lỗi/không đủ; không dùng khoa được gửi từ client làm bằng chứng quyền; nhật ký có actor, hồ sơ, hành động, thời điểm và căn cứ. | [DOC] Fail-closed là quy tắc dự án. [AUDIT] Cần chứng minh nhất quán cho đọc/xuất và chuyển khoa/khóa nhân viên. | [Bảo mật][D04], [Luật dữ liệu][L03] | P0 |

**Kết quả mong đợi sau khi sửa:** B đọc được record của C khi có nhiệm vụ, nhưng không sửa được; đổi recordId
trên URL không vượt quyền; nhân viên bị vô hiệu hóa không còn quyền chỉ vì token còn chứa role.

## 3. Hồ sơ đã hoàn tất và bổ sung, đính chính

**Kết quả cần đạt:** không sửa đè bản hoàn tất, đồng thời vẫn xử lý được sai sót hợp lệ.
Không đồng nhất trạng thái kỹ thuật với toàn bộ vòng đời pháp lý.

**Chọn đề xuất để tiến hành:**

- [x] **AMD-01**
- [x] **AMD-02**
- [x] **AMD-03**
- [x] **AMD-04**
- [x] **AMD-05**
- [x] **AMD-06**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| AMD-01 | Giữ khóa sửa trực tiếp COMPLETED cho mọi người, kể cả tác giả; API và UI phải phản ánh cùng quy tắc. | [SRC] requireOpen đã chặn sửa record hoàn tất. Đây là hành vi cần giữ, không phải lỗi cần nới. | [Record][C01] | P0 |
| AMD-02 | Có quy trình đính chính/bổ sung: nội dung gốc, nội dung bổ sung, lý do, tác giả, thời điểm thực tế, người duyệt theo quy chế cơ sở và liên kết tài liệu bị ảnh hưởng. | [SRC] Model record đã kiểm chưa có addendum hoặc quy trình phê duyệt này; [ĐỀ XUẤT] bổ sung riêng, không bật lại sửa tự do. | [Record][C01], [Quy chế BV Quy Nhơn][L04] | P1 |
| AMD-03 | Phân biệt hoàn tất khám, ký/xác nhận và chuyển lưu trữ; mô tả đúng mức hỗ trợ của demo. | [SRC] Record chỉ biểu diễn OPEN/COMPLETED. Chưa có bằng chứng nó bao phủ vòng đời ký và lưu trữ pháp lý. | [Record][C01], [TT13][L02] | P0 |
| AMD-04 | Nếu triển khai xác nhận điện tử: xác định ai xác nhận phần nào, giá trị tài liệu đã xác nhận và kiểm tra thay đổi; không gọi JWT/timestamp là chữ ký hợp pháp. | [AUDIT] Chưa nghiệm thu cơ chế ký/xác nhận hợp lệ trong lượt này. Không mặc định chỉ có một hình thức chữ ký được phép. | [TT13][L02] | P2 |
| AMD-05 | Hồ sơ đã hoàn tất nhưng có thêm tài liệu liên quan hợp lệ: hiển thị bổ sung từ nguồn, không sửa nội dung chuyên môn đã chốt. | [DOC] Business flows đã phân biệt bản chuyên môn hoàn tất với các tài liệu/facts liên quan phát sinh sau đó. [AUDIT] Cần test UI/projection cập nhật đúng khi record đã COMPLETED. | [Business flows][D03], [Data reliability][D08] | P1 |
| AMD-06 | Khi tác giả nghỉ việc: xử lý sai sót theo phân công/phê duyệt cơ sở, giữ danh tính người thực hiện thật và bản gốc; không giả danh người cũ. | [ĐỀ XUẤT] Ngoại lệ vận hành cần đặc tả nếu làm đính chính; không mở quyền cho admin sửa nội dung thay bác sĩ. | [Quy chế BV Quy Nhơn][L04], [Bảo mật][D04] | P2 |

Ví dụ Quy Nhơn yêu cầu nhân viên đề nghị chỉnh sửa hồ sơ lưu trữ trình phê duyệt và lưu vết.
MediFlow nên học nguyên tắc kiểm soát thay đổi; cấp phê duyệt cụ thể cần dựa vào quy chế cơ
sở triển khai. Đây không phải yêu cầu mọi chỉnh sửa bản nháp đều phải trình ban giám đốc.

## 4. Bàn giao, hội chẩn và hồ sơ tái khám

**Kết quả cần đạt:** phân biệt thay bác sĩ của lượt mới với bàn giao một lượt đang khám;
thêm người tham gia không làm mất tác giả và trách nhiệm trước đó.

**Chọn đề xuất để tiến hành:**

- [x] **COL-01**
- [x] **COL-02**
- [x] **COL-03**
- [x] **COL-04**
- [x] **COL-05**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| COL-01 | Bàn giao lượt mở có bác sĩ giao/nhận, thời điểm, lý do, việc đang chờ và xác nhận; quyết định giữ/gia hạn quyền ghi phải rõ. | [SRC] Record gắn một doctorId; guard theo bác sĩ phân công. Chưa có mô hình bàn giao/đa tác giả đầy đủ ở đường đi đã kiểm. | [Record][C01], [Record service][C02] | P1 |
| COL-02 | Lưu nhận định hội chẩn theo từng tác giả, liên kết lượt nguồn; kết luận điều trị cuối có người chịu trách nhiệm. | [AUDIT] Chưa xác minh có phiếu hội chẩn outpatient. [ĐỀ XUẤT] Không giải quyết bằng cách cho cả khoa sửa một ô diagnosis. | [Luật KBCB Điều 64][L01], [Giới hạn đa bác sĩ][D11] | P2 |
| COL-03 | Tái khám tạo lượt mới cùng patientId, liên kết nguồn đúng người; không copy tự động chẩn đoán/đơn cũ thành chỉ định mới. | [SRC/DOC] Có FOLLOW_UP, lời hẹn và kiểm liên kết. Cần nghiệm thu nguồn khác patient và dữ liệu quá hạn. | [Registration][C04], [Business flows][D03] | P0 |
| COL-04 | Khi bác sĩ cũ không có lịch, cho người thay phù hợp; giữ bác sĩ nguồn và bác sĩ tiếp nhận, lý do/xác nhận. | [SRC] Validate follow-up đã có nhánh thay bác sĩ. Việc này không chuyển quyền sửa record nguồn. | [Registration][C04], [Đối chiếu quyền][D10] | P1 |
| COL-05 | Lời hẹn trong hồ sơ ghi ngày/khoảng ngày, mục tiêu và chuẩn bị; phân biệt lời hẹn chuyên môn với phiếu đăng ký đã xác nhận. | [SRC/DOC] Có FollowUpRecommendation. [AUDIT] Cần rà cách hiển thị lời hẹn và liên kết lượt tái khám trên trang hồ sơ. | [Follow-up service][C03], [Business flows][D03] | P1 |

**Kết quả mong đợi sau khi sửa:** A khám buổi sáng, B nhận buổi chiều không thể sửa ghi chép của A; B ghi
phần của mình và tiếp nhận việc đang chờ. Nếu chưa làm được trong MVP, ghi rõ giới hạn và
không dùng thao tác đổi lịch tái khám làm demo thay thế cho bàn giao đang khám.

## 5. Ghi nhận hồ sơ khi cùng ngày có nhiều lần khám hoặc nhiều bác sĩ

**Kết quả cần đạt:** phân loại theo lý do và diễn biến của lượt, không theo ngày hoặc số ca
làm việc. Đây là phần cần làm rõ nhất khi hội đồng hỏi tình huống quay lại trong ngày.

**Chọn đề xuất để tiến hành:**

- [x] **DAY-01**
- [x] **DAY-02**
- [x] **DAY-03**
- [x] **DAY-04**
- [x] **DAY-05**
- [x] **DAY-06**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| DAY-01 | Sáng khám, chiều nhận kết quả khi lượt còn mở: tiếp tục cùng lượt/hồ sơ; không tạo record mới chỉ vì đổi buổi. | [DOC] Quy tắc canonical đã rõ. [AUDIT] Cần kiểm cách mở lại đúng màn hình ghi tiếp và giữ liên kết kết quả trong cùng hồ sơ. | [Business flows mục 5–7][D03], [Bạch Mai][L06] | P0 |
| DAY-02 | Sang ngày mới nhưng chưa hoàn tất chuyên môn: cho tiếp tục lượt nếu hợp lệ, không tự đổi thành tái khám theo đồng hồ. | [DOC] Business flows đã quy định kết quả muộn tiếp tục lượt mở. [AUDIT] Cần kiểm guard ngày và ca tại command thật. | [Business flows][D03], [Hướng dẫn thanh toán][L05] | P1 |
| DAY-03 | Đã hoàn tất, về nhà rồi quay lại do bất thường: tiếp nhận/đánh giá lần quay lại có liên kết; không sửa bản cũ để ghi diễn biến mới. | [SRC] COMPLETED bất biến là nền tảng đúng; [AUDIT] Chưa chứng minh toàn bộ luồng đăng ký lại cùng ngày hoạt động nhất quán. | [Record][C01], [Business flows][D03], [Hướng dẫn thanh toán][L05] | P0 |
| DAY-04 | Khi bệnh nhân đã có lượt đang mở cùng vấn đề, màn hình hồ sơ cần chỉ rõ để bác sĩ xác định ghi tiếp, bàn giao hay ghi nhận một lượt độc lập có lý do. | [SRC] Registration chỉ chặn trùng đang hoạt động trong cùng session. Không thể dùng sessionId để kết luận hai hồ sơ khác nhau là trùng nghiệp vụ. | [Registration][C04], [Migration V9][C05] | P1 |
| DAY-05 | Cùng bác sĩ nhiều buổi trong ngày: ca thay đổi không tự tạo lượt; lượt đã kết thúc rồi quay lại không tự bị gộp. | [SRC] Session tách khỏi record; [ĐỀ XUẤT] bổ sung phân loại lý do và xác nhận thay vì suy theo doctor/date. | [Registration][C04], [Record][C01] | P1 |
| DAY-06 | Hai chuyên khoa cùng lần đến: giữ nội dung và bác sĩ của từng lượt, hiển thị cạnh nhau trong lịch sử; chỉ thêm nhóm lần đến khi có nhu cầu rõ. | [AUDIT] Chưa chứng minh có grouping một lần đến đa chuyên khoa. Không bắt buộc thêm aggregate mới ngay nếu link hiện có đáp ứng được MVP. | [Business flows][D03], [Hướng dẫn thanh toán][L05] | P2 |

**Kết quả mong đợi sau khi sửa:** nhìn lịch sử phân biệt được khám tiếp, lần quay lại sau
hoàn tất, hội chẩn và lượt độc lập. Không gộp hồ sơ chỉ vì cùng ngày hoặc cùng bác sĩ; không
tách hồ sơ chỉ vì thay ca. Điều chỉnh lịch được xem riêng ở phần 9–11; cách tính phí vẫn do Billing làm chủ.

## 6. Tài liệu liên quan được xem từ hồ sơ

**Mục tiêu:** bác sĩ xem được tài liệu đúng người, đúng lượt và biết nguồn phát sinh;
phần hồ sơ không trở thành nơi sửa kết quả Lab hoặc đơn thuốc của phân hệ nguồn.

**Chọn đề xuất để tiến hành:**

- [x] **DOC-01**
- [x] **DOC-02**
- [x] **DOC-03**
- [x] **DOC-04**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| DOC-01 | Hiển thị đơn thuốc, kết quả và giấy hẹn theo đúng lượt nguồn; mỗi tài liệu có loại, tác giả/đơn vị, thời điểm và trạng thái nguồn. | [SRC/DOC] Record và các context đã có liên kết tài liệu/facts. [AUDIT] Chưa kiểm toàn bộ tính đầy đủ của màn hình hồ sơ; không suy đã xem được tất cả chỉ vì GET record thành công. | [Đối chiếu quyền][D10], [Contract][D05] | P1 |
| DOC-02 | Phân biệt chưa có tài liệu, đang chờ, lỗi tải và không có quyền; không hiện “không có” khi nguồn chưa truy cập được. | [SRC] Panel giấy hẹn có thể diễn giải RESOURCE_NOT_FOUND như chưa có đề xuất; cần rà lại cả quyền lẫn cách hiển thị. | [Đối chiếu quyền][D10], [Follow-up service][C03] | P1 |
| DOC-03 | Giữ quyền sở hữu ở nguồn: hồ sơ chỉ hiển thị kết quả/đơn và dẫn tới thao tác nguồn khi được cấp quyền; không thêm nút sửa đè từ timeline. | [DOC] Dự án quy định mỗi context làm chủ dữ liệu. [AUDIT] Cần kiểm các thao tác được mở từ trang hồ sơ, không đổi quy trình thực hiện/cấp phát. | [Thiết kế][D02], [Contract][D05] | P0 |
| DOC-04 | Đánh dấu nội dung lịch sử và nội dung lượt hiện tại; khi bác sĩ tham khảo chẩn đoán/đơn cũ, không tự sao thành chỉ định hoặc kết luận mới. | [DOC] Business flows đã yêu cầu bác sĩ xác nhận quyết định mới. [AUDIT] Cần rà màn hình hồ sơ và thao tác tái khám. | [Business flows][D03], [Record][C01] | P1 |

**Kết quả mong đợi sau khi sửa:** từ một lượt, xem được đúng kết quả và đơn liên quan;
đi tới lượt khác không làm tài liệu cũ bị hiển thị như mới. Tài liệu được bổ sung sau khi
hoàn tất vẫn giữ nguồn và thời điểm thật, không làm thay đổi ghi chép cũ.

## 7. Giao diện tra cứu và tránh ghi nhầm hồ sơ

**Mục tiêu:** chỉ cần nhìn trang hồ sơ là biết đang xem người nào, lượt nào, của bác sĩ nào
và có được ghi hay không. Phần này dành cho workspace hồ sơ; giao diện lịch và hàng chờ được đề xuất riêng ở phần 9–11.

**Chọn đề xuất để tiến hành:**

- [x] **UX-01**
- [x] **UX-02**
- [x] **UX-04**
- [x] **VIEW-01**
- [x] **VIEW-02**
- [x] **VIEW-03**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| UX-01 | Timeline có khoa, bác sĩ, loại lượt, trạng thái và nguồn tái khám; thêm bộ lọc và phân trang phù hợp contract. | [SRC] MedicalRecordTable hiện hiển thị danh sách phẳng theo bệnh nhân; chưa nhóm quá trình, chưa có cột khoa theo lần rà soát. | [Bảng hồ sơ][C08], [Đối chiếu quyền][D10] | P1 |
| UX-02 | Tách vùng lịch sử chỉ đọc với vùng ghi lượt đang mở; hiển thị rõ tác giả, người đang tiếp nhận và thời điểm. | [SRC] Đã có trang danh sách/chi tiết. [AUDIT] Cần đối chiếu điều hướng giữa lượt cũ và lượt đang khám để tránh ghi nhầm. | [Record service][C02], [Record][C01] | P0 |
| UX-04 | Thể hiện loading, rỗng, lỗi, bị từ chối và nút thử lại; không biến lỗi giấy hẹn thành kết luận không có giấy hẹn. | [SRC] Đã xác định chênh lệch quyền record và giấy hẹn. [AUDIT] Cần rà các trạng thái hiển thị còn lại trong workspace hồ sơ. | [Đối chiếu quyền][D10] | P1 |
| VIEW-01 | Giữ nhận diện bệnh nhân và lượt đang xem ở vị trí rõ; đối chiếu bằng thông tin cần thiết, không chỉ tên hoặc số phòng. | [SRC] Patient/record có ID liên kết. [AUDIT] Chưa kiểm đầy đủ trường hợp nhiều tab, phản hồi cũ về muộn và hai người trùng tên. | [Record][C01], [Bảng hồ sơ][C08] | P0 |
| VIEW-02 | Chỉ hiển thị nút ghi, hoàn tất, đề nghị đính chính theo capability và trạng thái; backend vẫn kiểm cùng điều kiện. | [SRC] Có guard ghi và khóa COMPLETED; [AUDIT] Chưa đối chiếu mọi nút trên UI với khả năng thực tế. | [Record service][C02], [Quy tắc][D04] | P0 |
| VIEW-03 | Nếu có bản in/xuất hồ sơ, phân biệt một lượt với lịch sử tổng hợp; giữ tác giả, thời điểm và bản đính chính, kiểm quyền xuất riêng. | [AUDIT] Chưa xác minh đầy đủ bản in/xuất hiện tại. Đề xuất chỉ áp dụng tài liệu từ phần hồ sơ, không mở rộng hóa đơn/đơn thuốc nguồn. | [Luật KBCB][L01], [TT13][L02] | P2 |

**Kết quả mong đợi sau khi sửa:** mở lịch sử bác sĩ khác không xuất hiện vùng sửa của mình;
chuyển bệnh nhân/lượt không giữ nội dung của màn hình trước. Bộ lọc giao diện không thay
thế kiểm quyền ở backend.

## 8. Chuyển đổi dữ liệu hồ sơ khi thực hiện các mục được chọn

**Mục tiêu:** cải thiện cấu trúc hồ sơ nhưng vẫn đọc được lịch sử đã có; không tự dựng tác
giả, quá trình điều trị hoặc quyền truy cập từ dữ liệu không đủ căn cứ.

**Chọn đề xuất để tiến hành:**

- [x] **MIG-01**
- [x] **MIG-02**
- [x] **MIG-03**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| MIG-01 | Lập bản đồ patientId, appointmentId, recordId, doctorId và departmentId trước khi thêm nhóm/tài liệu/bàn giao. Dữ liệu thiếu được gắn nhãn cần rà soát, không tự đoán. | [SRC] Record hiện giữ các liên kết nêu trên. [AUDIT] Chưa kiểm chất lượng toàn bộ dữ liệu đang lưu. | [Record][C01], [Data reliability][D08] | P1 |
| MIG-02 | Nếu thêm Episode, ghi chú hoặc đính chính: bổ sung cấu trúc tương thích, giữ recordId/tác giả và nội dung gốc; không gộp hồi tố chỉ vì cùng khoa/ngày. | [ĐỀ XUẤT] Các cấu trúc này chưa được xác nhận triển khai. Chỉ cần làm khi bạn chọn đề xuất có dependency tương ứng. | [Giới hạn đa bác sĩ][D11], [Contract][D05] | P1 |
| MIG-03 | Đối chiếu contract và quyền của Clinical với frontend/tài liệu liên quan; thay đổi cần dữ liệu nguồn khác phải mô tả dependency trước khi sửa. | [DOC] Kiến trúc cấm truy vấn chéo DB và yêu cầu khóa contract. Không tạo bảng bệnh nhân trung tâm chứa bản sao có quyền sửa mọi dữ liệu. | [Thiết kế][D02], [Contract][D05], [Quy tắc][D04] | P0 |

Đây vẫn là các đề xuất để bạn chọn. Nếu một mục đã được chọn cần thêm dependency chưa
được duyệt, phải nêu phạm vi bổ sung cụ thể thay vì mở rộng sang cả phân hệ khác.

## 9. Ca làm việc và buổi nhận khám

**Mục tiêu:** phân biệt ca được phân công với buổi thực sự mở nhận bệnh nhân. Một ca có
thể liên quan nhiều buổi, nhưng mỗi buổi phải phù hợp cấu hình ca và nguồn lực được cấp.
Không gọi lịch ngoại trú này là phân hệ chấm công, tính lương hoặc trực nội trú/cấp cứu.

**Chọn đề xuất để tiến hành:**

- [x] **CL-SCH-01**
- [x] **CL-SCH-02**
- [x] **CL-SCH-03**
- [x] **CL-SCH-04**
- [x] **CL-SCH-05**
- [x] **CL-SCH-06**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| CL-SCH-01 | Chuẩn hóa quan hệ ca/buổi: tạo buổi từ ca đúng bác sĩ, khoa, phòng, ngày và khoảng giờ; làm rõ trường hợp tạo buổi độc lập có được phép hay không. | [SRC] Có DoctorWorkShift và ConsultationSession; createSession từ ca gọi requireContains và linkSession. Đồng thời có đường tạo buổi độc lập ở ConsultationSessionApplicationService. [AUDIT] Cần chốt chính sách nhất quán cho hai đường, không kết luận mọi buổi đều đã bắt buộc thuộc ca. | [WorkShift service][C12], [Session service][C13] | P1 |
| CL-SCH-02 | Khi sửa/hủy ca đã có buổi liên kết, kiểm tác động và báo các buổi/phiếu ảnh hưởng; không tạo tình trạng ca bị hủy nhưng buổi vẫn nhận người trái chính sách. | [SRC] Có update/cancel ca và liên kết ca–buổi. [AUDIT] Chưa đủ bằng chứng về mọi guard/cascade ở domain/persistence và màn hình; cần rà trước chọn cách xử lý. | [WorkShift service][C12], [Scheduling contract][D06] | P0 |
| CL-SCH-03 | Kiểm chồng lịch bác sĩ/phòng, giờ nghỉ, capacity và xung đột cập nhật ở backend; cho ca/buổi liền kề hợp lệ theo quy tắc đã chốt. | [SRC/DOC] Session có cấu hình giờ/nghỉ/sức chứa, revision và contract chống overlap. [AUDIT] Cần kiểm constraint, các đường tạo/sửa và xung đột ca với buổi; không suy dropdown đúng là backend đủ. | [Session service][C13], [Scheduling contract][D06] | P0 |
| CL-SCH-04 | Người quản lý tạo/sửa/ngừng lịch theo khoa được giao; bác sĩ thông thường xem lịch mình, không tự có quyền sửa lịch vì role DOCTOR. | [SRC] requireScheduleScope kiểm authority hiện hành; nhánh DOCTOR đòi canManageSchedules và staff hợp lệ. [AUDIT] Cần đối chiếu UI, endpoint và thu hồi bổ nhiệm. | [Session service][C13], [WorkShift service][C12], [Business flows][D03] | P0 |
| CL-SCH-05 | Lịch ngày/tuần thể hiện khoa/phòng, ca/buổi, thời gian và số suất; hiển thị khung dự kiến, phân biệt hết suất với chưa mở hoặc lỗi tải. | [SRC/DOC] Có mine/calendar và quy tắc ngày/tuần Việt Nam. [AUDIT] Cần kiểm mapping UI và boundary ngày/tuần. Thời hạn giữ suất không phải thời lượng khám mỗi bệnh nhân. | [Session service][C13], [Business flows][D03] | P1 |
| CL-SCH-06 | Ngừng nhận mới giữ nguyên trách nhiệm với phiếu cũ; sửa bác sĩ/giờ khi đã có phiếu phải qua xử lý ảnh hưởng, không cập nhật ngầm. | [SRC/DOC] Session có update/status và quy tắc SESSION_HAS_REGISTRATIONS; stop không tương đương hủy toàn bộ. [AUDIT] Cần kiểm các trạng thái phiếu và trường được phép sửa. | [Session service][C13], [Scheduling contract][D06] | P1 |

**Kết quả mong đợi sau khi sửa:** người quản lý thấy rõ thay đổi lịch tác động tới ai;
người bệnh không mất phiếu do sửa một dòng ca; hai buổi chồng nguồn lực không cùng được chấp nhận.

## 10. Đăng ký, giữ suất và tiếp nhận tại Clinical

**Mục tiêu:** nối đúng bệnh nhân với buổi khám và lượt, phân biệt đặt trước với đã có mặt,
giữ suất với đã xác nhận và điều kiện vào khám. Clinical không tự tính lại tiền của Billing.

**Chọn đề xuất để tiến hành:**

- [x] **CL-REG-01**
- [x] **CL-REG-02**
- [x] **CL-REG-03**
- [x] **CL-REG-04**
- [x] **CL-REG-05**
- [x] **CL-REG-06**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| CL-REG-01 | Chọn bệnh nhân đã có, khoa và buổi hợp lệ; kiểm lại eligibility và sức chứa khi xác nhận, không tin danh sách đã tải trước. | [SRC] Registration create kiểm actor, patient, session và giữ suất dưới khóa. [AUDIT] Cần regression hai quầy tranh suất cuối và authority thay đổi sau lúc mở màn hình. | [Registration][C04], [Session service][C13] | P0 |
| CL-REG-02 | Tách đăng ký tại quầy đã ghi nhận có mặt với đặt trước cần xác nhận đến; không coi phiếu đã thu là bệnh nhân đã đến. | [DOC] Contract có hai hình thức và arrival riêng cho đặt trước. [AUDIT] Cần đối chiếu nhãn UI và start-exam trên cấu hình runtime đang dùng. | [Scheduling contract][D06], [Business flows][D03] | P1 |
| CL-REG-03 | Hiển thị hạn giữ và số suất đúng trạng thái; không giải phóng phiếu đang có giao dịch chưa rõ kết quả chỉ vì hết TTL. | [SRC/DOC] Có holds/payment locks/reconciliation trong luồng V2. [AUDIT] Cần giữ và kiểm cơ chế đã có khi sửa lịch/tiếp nhận; không xây lại bằng bộ đếm frontend. | [Registration][C04], [Scheduling contract][D06] | P0 |
| CL-REG-04 | Nhấn lại hoặc mất phản hồi đăng ký trả lại cùng phiếu theo intent/key; cùng key khác bệnh nhân/buổi phải bị từ chối. | [SRC/DOC] Có idempotency và tiêu chí exact replay; [AUDIT] Cần kiểm UI có giữ key trong lần thử lại và không tự tạo intent mới. | [Registration][C04], [Scheduling contract][D06] | P0 |
| CL-REG-05 | Phân biệt hủy phiếu, hết hạn, không đến và chưa rõ kết quả xác nhận; giải phóng suất đúng tình huống và không làm mất lượt đã phục vụ. | [DOC] Contract tách holds, locked, confirmed và consumed. [AUDIT] Chưa xác minh đủ chính sách no-show/cancel ở mọi màn hình; không tự thêm enum live từ nhãn đề xuất. | [Scheduling contract][D06], [Business flows][D03] | P1 |
| CL-REG-06 | Khám mới/tái khám chọn đúng nguồn; đăng ký khác ca cùng ngày cần phân biệt trùng ý định với nhu cầu hợp lệ, không cấm cứng theo patient–ngày. | [SRC] Active duplicate hiện theo patientId + sessionId, có validate follow-up. [ĐỀ XUẤT] Bổ sung nhận biết lượt đang mở theo DAY-04 mà không chặn mọi lần quay lại. | [Registration][C04], [Migration V9][C05], [Đối chiếu hồ sơ][D10] | P1 |

**Kết quả mong đợi sau khi sửa:** từ phiếu biết người bệnh có mặt chưa, còn giữ suất không,
đủ điều kiện vào khám chưa và thuộc lượt nào. Đăng ký thành công không tự tạo kết luận đã khám.

## 11. Hàng chờ, lịch cá nhân và đổi bác sĩ

**Mục tiêu:** bác sĩ làm việc trên đúng lượt được giao, người tiếp nhận biết lượt đang chờ
ở đâu và thay đổi người khám có căn cứ. Đổi lịch lượt chưa khám khác bàn giao lượt đang khám.

**Chọn đề xuất để tiến hành:**

- [x] **CL-QUE-01**
- [x] **CL-QUE-02**
- [x] **CL-QUE-03**
- [x] **CL-QUE-04**
- [x] **CL-QUE-05**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| CL-QUE-01 | Lịch cá nhân và danh sách bệnh nhân chỉ phản ánh phạm vi được giao; quyền xem lịch sử liên khoa không tạo quyền bắt đầu khám mọi lượt. | [SRC] Có currentDoctorId và requireAssignedDoctor ở startExam. [AUDIT] Cần đối chiếu truy vấn danh sách, URL và mở workspace từ lịch. | [Session service][C13], [Clinical examination][C14] | P0 |
| CL-QUE-02 | Tách danh sách đã đăng ký với hàng chờ được phép khám; thể hiện có mặt, chờ đủ điều kiện, sẵn sàng, đang khám, chờ kết quả và hoàn tất. | [DOC] Business flows đã định nghĩa các chiều trạng thái. [AUDIT] Cần kiểm dữ liệu nguồn và không suy trạng thái khám chỉ từ badge thanh toán. | [Business flows][D03], [Registration][C04] | P1 |
| CL-QUE-03 | Người đến trễ, gọi không có mặt hoặc cần ưu tiên được xử lý theo quy tắc có người chịu trách nhiệm; ghi nhận điều chỉnh thứ tự, không sửa ngầm. | [DOC] Có nguyên tắc hàng chờ và không ưu tiên tái khám vô điều kiện. [AUDIT] Chưa chứng minh đã có toàn bộ thao tác gọi lại/ưu tiên/audit; cần chốt phạm vi trước sửa. | [Business flows][D03] | P1 |
| CL-QUE-04 | Đổi bác sĩ/phòng/giờ trước khám phải kiểm đủ điều kiện, lưu lịch cũ/mới, người đổi, lý do và xác nhận phù hợp; không tự sửa tác giả hồ sơ đã tạo. | [SRC/DOC] Tái khám có nhánh đổi bác sĩ với lý do/xác nhận; không đại diện đầy đủ chuyển phiếu đã xác nhận. [AUDIT] Cần rà khả năng reschedule thực tế và dependency liên quan. | [Registration][C04], [Scheduling contract][D06] | P1 |
| CL-QUE-05 | Bác sĩ hết ca khi còn lượt mở: chỉ rõ các lượt cần tiếp tục/bàn giao; nhận kết quả cùng lượt không bị đưa thành phiếu mới chỉ để vào hàng chờ chiều. | [SRC] Record vẫn một bác sĩ, chưa có bàn giao đa tác giả đầy đủ. [ĐỀ XUẤT] Kết hợp COL-01 và DAY-01, không dùng quyền sửa lịch thay bàn giao. | [Record][C01], [Business flows][D03] | P1 |

**Kết quả mong đợi sau khi sửa:** mở đúng lượt từ hàng chờ, không ghi nhầm record; đổi bác
sĩ trước khám và bàn giao trong khám có đường đi khác nhau, cùng giữ được lịch sử trách nhiệm.

## 12. Bắt đầu, tiếp tục và hoàn tất lượt khám

**Mục tiêu:** mỗi lượt có một quá trình chuyên môn giải thích được, không phụ thuộc việc
người dùng tự sửa status để vượt qua bước còn thiếu.

**Chọn đề xuất để tiến hành:**

- [x] **CL-EXM-01**
- [x] **CL-EXM-02**
- [x] **CL-EXM-03**
- [x] **CL-EXM-04**
- [x] **CL-EXM-05**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| CL-EXM-01 | Bắt đầu khám đúng bác sĩ và đủ điều kiện của luồng đang dùng; thao tác lặp không tạo nhiều record cho một lượt. | [SRC] startExam kiểm phân công, xử lý scheduled lifecycle và tìm/tạo lại record theo appointmentId. [AUDIT] Cần kiểm replay/race và đường legacy/V2 trên runtime đã chọn. | [Clinical examination][C14], [Record][C01] | P0 |
| CL-EXM-02 | Lượt chờ kết quả vẫn được nhận diện là đang xử lý; bác sĩ tiếp tục đúng record mà không gọi lại bước bắt đầu hoặc đăng ký mới. | [DOC] Business flows đã yêu cầu nhận kết quả trong lượt mở. [AUDIT] Cần kiểm khả năng quay lại workspace và pending indicators trên lịch/hồ sơ. | [Business flows][D03], [Clinical examination][C14] | P1 |
| CL-EXM-03 | Hoàn tất có chẩn đoán, hướng xử trí và thời điểm; chỉ cho trạng thái/hướng xử trí thuộc ngoại trú đã kích hoạt. | [SRC] Record.complete kiểm diagnosis/disposition; service có guard admission/transfer theo flag. [AUDIT] Cần rà UI không quảng bá nội trú/chuyển viện đã triển khai chỉ vì enum tồn tại. | [Record][C01], [Clinical examination][C14] | P0 |
| CL-EXM-04 | Từ chối chỉ định, bỏ về hoặc chưa giải quyết xong cần quyết định/lý do và dặn dò phù hợp, không nhập chẩn đoán/kết quả giả để kết thúc. | [AUDIT] Chưa xác minh đủ các ngoại lệ trong completion/disposition hiện tại. Đề xuất chốt luồng tối thiểu ở Clinical trước, không mở thêm phân hệ. | [Clinical examination][C14], [Business flows][D03] | P1 |
| CL-EXM-05 | Đồng bộ trạng thái appointment, registration và record theo command nghiệp vụ; từ chối tình trạng record hoàn tất nhưng lượt vẫn có thể bắt đầu/cập nhật tùy ý. | [SRC/DOC] Start và complete phối hợp nhiều aggregate tại Clinical. [AUDIT] Cần kiểm toàn bộ chuyển trạng thái, rollback và thao tác lặp; không thêm API sửa status tổng quát để chữa dữ liệu lệch. | [Clinical examination][C14], [Registration][C04], [Contract][D05] | P0 |

**Kết quả mong đợi sau khi sửa:** bắt đầu một lần, ghi tiếp đúng lượt, hoàn tất theo quyết
định chuyên môn và giữ bản gốc; các màn hình lịch, hàng chờ và hồ sơ phản ánh cùng trạng thái.

## 13. Điểm phối hợp của Clinical với các service khác

**Mục tiêu:** Clinical đủ thông tin để quyết định được phép làm gì, nhưng không sửa dữ liệu
nguồn hoặc giả facts của Lab, Pharmacy hay Billing. Chỉ các thao tác phía Clinical thuộc bảng này; đề xuất thay đổi phía Lab nằm riêng ở phần 14–17.

**Chọn đề xuất để tiến hành:**

- [x] **CL-INT-01**
- [x] **CL-INT-02**
- [x] **CL-INT-03**
- [x] **CL-INT-04**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| CL-INT-01 | Gửi chỉ định có đúng patient/record/lượt và bác sĩ; hiển thị trạng thái chờ/thực hiện/có kết quả từ nguồn, không coi gửi thành công là đã có kết quả. | [SRC] Có ClinicalLabOrderApplicationService. [AUDIT] Cần kiểm binding, actor và trạng thái nhận lại trên workspace Clinical; không sửa quy trình lấy mẫu/trả kết quả tại Lab. | [Clinical Lab orders][C15], [Contract][D05] | P0 |
| CL-INT-02 | Trước hoàn tất, kiểm các chỉ định bắt buộc còn chờ; nếu hủy/hoãn phải có quyết định phù hợp và lý do, không sửa trạng thái Lab từ Clinical để vượt guard. | [DOC] Là quy tắc đích trong business flows. [SRC] Record.complete tự kiểm chẩn đoán/hướng xử trí, chưa tự chứng minh orchestration đã chặn mọi pending test. | [Record][C01], [Clinical examination][C14], [Business flows][D03] | P0 |
| CL-INT-03 | Clinical sử dụng clearance đúng lượt/target và nguồn; mất kết nối hoặc chưa rõ kết quả không tự cấp quyền khám bằng suy đoán. | [SRC/DOC] Có startExam/clearance và luồng scheduled riêng; exception khẩn cấp có ràng buộc, không áp giống nhau cho mọi profile. [AUDIT] Cần rà cấu hình thực tế; không mở bypass mới hoặc sửa ledger Billing trong checklist này. | [Clinical examination][C14], [Scheduling contract][D06], [Contract][D05] | P0 |
| CL-INT-04 | Phân biệt bác sĩ đã kết luận/kê đơn với dược sĩ đã cấp và nghĩa vụ đã xử lý; hồ sơ nhận fact liên quan mà không mở lại phần chuyên môn. | [DOC] Business flows đã tách các chiều trạng thái. [AUDIT] Cần kiểm nhãn tại Clinical và liên kết đơn/fill; không triển khai cấp thuốc, kho hoặc hoàn tiền ở Clinical. | [Business flows][D03], [Contract][D05] | P1 |

**Kết quả mong đợi sau khi sửa:** Clinical hiển thị đúng việc đang chờ và nguồn xác nhận;
phạm vi sửa các service khác chỉ phát sinh khi được nêu và đồng ý riêng.

## 14. Danh mục và phân loại xét nghiệm

**Hiện trạng đã đọc:** LabTest dùng `labType` dạng chuỗi, có patientId, recordId,
sourceOrderId, priceCode và vòng đời riêng. LabResult có các trường chuỗi indicator,
value, unit và referenceRange. Form LabActions cho thêm/xóa dòng và tự nhập bốn trường này.
Đường source đã kiểm chưa thể hiện liên kết với một catalogue/template có phiên bản.
Kết luận này không chứng minh không có danh mục giá hoặc tham chiếu ở service khác.

**Hướng đề xuất:** phân loại theo nhiều chiều, thay vì tạo một enum trộn tất cả:

- **Nhóm chuyên môn/nhóm hiển thị:** huyết học–đông máu, sinh hóa, miễn dịch–nội tiết,
  vi sinh–ký sinh trùng; phân tích nước tiểu có thể là nhóm tiện dụng riêng trên UI.
- **Dịch vụ được chỉ định:** xét nghiệm đơn hoặc panel/bộ xét nghiệm cụ thể.
- **Loại bệnh phẩm:** máu toàn phần, huyết thanh, huyết tương, nước tiểu, phân, dịch...
  Loại bệnh phẩm và yêu cầu dụng cụ lấy mẫu phải phù hợp kỹ thuật của từng cơ sở.
- **Kiểu kết quả:** định lượng, định tính, bán định lượng, mô tả hoặc cấu trúc nhiều tầng.

Danh mục thực tế của Từ Dũ và cơ cấu xét nghiệm Bạc Liêu là ví dụ về các nhóm đang sử dụng,
không phải danh sách nhóm bắt buộc cho mọi bệnh viện. Nước tiểu không nên được dùng thay
cho trường loại bệnh phẩm chỉ vì đã là tên nhóm hiển thị. [Từ Dũ][L07], [Bạc Liêu][L08].

**Chọn đề xuất để tiến hành:**

- [x] **LAB-CAT-01**
- [x] **LAB-CAT-02**
- [x] **LAB-CAT-03**
- [x] **LAB-CAT-04**
- [x] **LAB-CAT-05**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| LAB-CAT-01 | Có danh mục dịch vụ xét nghiệm với mã ổn định, tên, nhóm, trạng thái hoạt động, bệnh phẩm phù hợp, template và phiên bản; không nhập tên tự do cho mọi chỉ định mới. | [SRC] LabTest.labType là chuỗi; chưa thấy catalogue/template binding trong model/form đã đọc. [ĐỀ XUẤT] Rà nguồn danh mục hiện có trước thêm authority mới. | [LabTest][C16], [Form Lab][C20], [Contract][D05] | P1 |
| LAB-CAT-02 | Cho cấu hình các nhóm hiển thị huyết học–đông máu, sinh hóa, miễn dịch–nội tiết, nước tiểu, vi sinh–ký sinh trùng; lọc hàng việc theo nhóm nhưng không suy quyền từ tên nhóm. | [SRC] Có labType nhưng chưa có taxonomy có cấu trúc trong model đã kiểm. Tên nhóm là lựa chọn UI, không tự tương đương khoa tổ chức hoặc chuyên môn được phép thực hiện. | [LabTest][C16], [Danh mục Từ Dũ][L07], [Khoa XN Bạc Liêu][L08] | P1 |
| LAB-CAT-03 | Tách dịch vụ đơn với panel: một chỉ định công thức máu có nhiều chỉ số; số chỉ số kết quả không tự tạo thêm nhiều charge hoặc lượt khám. | [SRC] LabTest chứa danh sách LabResult, phù hợp làm nền tảng nhiều chỉ số; chưa có định nghĩa panel và bộ chỉ số bắt buộc gắn catalogue. | [LabTest][C16], [LabResult][C17], [FHIR DiagnosticReport][L10] | P1 |
| LAB-CAT-04 | Gắn yêu cầu bệnh phẩm, chuẩn bị, phương pháp khi cần và thời gian trả dự kiến do cơ sở cấu hình; không mặc định mọi xét nghiệm là máu hoặc đều cần nhịn ăn. | [AUDIT] Chưa kiểm đầy đủ nguồn metadata chuẩn bị/bệnh phẩm. Model LabTest đã đọc chưa có thông tin bệnh phẩm riêng. | [LabTest][C16], [FHIR Specimen][L11] | P1 |
| LAB-CAT-05 | Giữ mã dịch vụ, mã chỉ số và priceCode là các khái niệm khác nhau; Clinical chọn mã dịch vụ, Lab xác thực template, Billing giữ giá qua mapping chính thức. | [SRC] Có sourceOrderId/priceCode/labType. [AUDIT] Chưa đủ bằng chứng về authority catalogue và version mapping end-to-end; không suy mã giá từ tên xét nghiệm. | [LabTest][C16], [Clinical Lab][C15], [Contract][D05] | P0 |

**Kết quả mong đợi sau khi sửa:** bác sĩ chọn một dịch vụ rõ ràng; Lab nhận đúng nhóm công
việc và mở đúng mẫu, không phải tự đoán các chỉ số cần nhập từ tên yêu cầu.

## 15. Template kết quả: loại mẫu, trường dữ liệu và quản lý phiên bản

**Phân biệt:** template nhập liệu xác định trường/validation; mẫu phiếu trả xác định cách
trình bày. Cả hai dùng cùng dữ liệu kết quả đã phát hành, không tạo hai bản có nội dung khác nhau.

Các ví dụ dưới đây là đề xuất cấu trúc phần mềm, chưa phải bộ chỉ số, đơn vị hoặc ngưỡng
tham chiếu chuẩn cho triển khai thật. Tên mã ví dụ không phải ID hiện có để gửi vào API.

**Chọn đề xuất để tiến hành:**

- [x] **LAB-TPL-01**
- [x] **LAB-TPL-02**
- [x] **LAB-TPL-03**
- [x] **LAB-TPL-04**
- [x] **LAB-TPL-05**
- [x] **LAB-TPL-06**
- [x] **LAB-TPL-07**
- [x] **LAB-TPL-08**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| LAB-TPL-01 | Mẫu huyết học/CBC dạng bảng nhiều chỉ số: mã chỉ số, tên, giá trị, đơn vị, khoảng tham chiếu, cờ và ghi chú. Ví dụ WBC, RBC, HGB, HCT, PLT; danh sách thực tế do phòng xét nghiệm duyệt. | [SRC] Có thể nhập nhiều dòng nhưng chưa khóa theo bộ chỉ số/template đã chọn. Người nhập có thể tự đặt tên và số dòng. | [LabResult][C17], [Form Lab][C20], [FHIR Observation][L09] | P1 |
| LAB-TPL-02 | Mẫu sinh hóa định lượng: một/nhiều chỉ số; giá trị có số và toán tử khi cần, ví dụ nhỏ hơn giới hạn đo. Giữ đơn vị và phương pháp phù hợp, không ép mọi giá trị thành số thuần. | [SRC] value hiện là string, đã cho phép văn bản như “< ...”; chưa có kiểu dữ liệu/operator có cấu trúc. Giữ tương thích dữ liệu cũ khi bổ sung. | [LabResult][C17], [FHIR Observation][L09] | P1 |
| LAB-TPL-03 | Mẫu định tính: lựa chọn được cấu hình theo phép thử như âm/dương, phản ứng/không phản ứng và chưa xác định khi phù hợp; có ghi chú. Không tự đổi kết quả sàng lọc thành chẩn đoán xác định. | [SRC] Hiện nhập value tự do; chưa có bộ giá trị theo assay/template trong DTO đã kiểm. | [Result input][C18], [FHIR Observation][L09] | P1 |
| LAB-TPL-04 | Mẫu nước tiểu hỗn hợp: mỗi chỉ số có kiểu riêng, có thể số, lựa chọn hoặc mức bán định lượng; không dùng một input số cho cả phiếu. | [SRC] Danh sách kết quả dạng chuỗi chứa được nội dung nhưng chưa có schema hỗn hợp hoặc validation theo chỉ số. | [LabResult][C17], [Form Lab][C20], [Từ Dũ][L07] | P2 |
| LAB-TPL-05 | Mẫu vi sinh cơ bản trước mắt hỗ trợ soi/nhuộm, phát hiện tác nhân và nhận xét; nuôi cấy nhiều tác nhân/kháng sinh đồ là cấu trúc riêng cho pha sau, không gom vào một ô số. | [SRC] result rows và conclusion còn phẳng. [ĐỀ XUẤT] Không tuyên bố hỗ trợ vi sinh đầy đủ khi chỉ có bảng chung. | [LabResult][C17], [FHIR diagnostics][L12] | P2 |
| LAB-TPL-06 | Template có version, mã chỉ số, thứ tự, required/optional, kiểu giá trị, đơn vị cho phép và quy tắc hợp lệ. Khi sửa template, phiếu đang làm giữ phiên bản đã gán; muốn chuyển cần thao tác có kiểm soát. | [SRC] LabTest có resultVersion nhưng đó không tự là templateVersion. Chưa thấy template snapshot/version trong đường model đã kiểm. | [LabTest][C16], [Result input][C18] | P1 |
| LAB-TPL-07 | Khoảng tham chiếu/cờ bất thường lấy theo cấu hình áp dụng cho tuổi, đặc điểm liên quan, bệnh phẩm/phương pháp khi cần; lưu snapshot đã dùng. Thiếu cấu hình thì ghi chưa xác định, không tự tô bình thường. | [SRC] referenceRange là chuỗi do người nhập gửi; chưa có cấu hình khoảng tham chiếu có cấu trúc trong result model. [ĐỀ XUẤT] Cơ sở chuyên môn duyệt cấu hình; không hardcode ngưỡng chung từ ví dụ. | [LabResult][C17], [Form Lab][C20], [FHIR Observation][L09] | P0 |
| LAB-TPL-08 | Server kiểm đủ chỉ số bắt buộc, mã trùng, sai kiểu/đơn vị và lý do không có kết quả; không coi thiếu kết quả là 0 hoặc âm tính. Chỉ số tùy chọn không bắt nhập giả để phát hành. | [SRC] DTO yêu cầu indicator/value không trống; model kiểm danh sách không rỗng. Chưa có validation theo template ở đường addResults đã kiểm. | [Result input][C18], [Lab service][C19], [FHIR Observation][L09] | P0 |

**Ví dụ màn hình nhập tối thiểu đề xuất:**

- Đầu phiếu: bệnh nhân, mã chỉ định, lượt/hồ sơ, dịch vụ, mã bệnh phẩm và phiên bản template.
- Thân phiếu: danh sách trường sinh từ template; đơn vị/tham chiếu không phải nhập lại tùy ý.
- Cuối phiếu: nhận xét, phương pháp khi cần, người thực hiện, người duyệt, thời điểm và trạng thái.
- Nút riêng: **Lưu nháp → Gửi duyệt → Phát hành**, nếu bạn chọn LAB-WF-03/04 bên dưới.

Đây là đề xuất mới. Nút “Lưu kết quả” hiện tại gọi addResults và hoàn tất xét nghiệm;
không được đổi nhãn thành “Lưu nháp” mà vẫn phát cùng fact kết quả hoàn tất.

## 16. Luồng tương tác tại Lab: tiếp nhận, bệnh phẩm, thực hiện và phát hành

**Luồng đề xuất:** nhận chỉ định đúng nguồn → kiểm điều kiện thực hiện → nhận diện người
bệnh/bệnh phẩm → lấy/nhận mẫu và đánh giá mẫu → thực hiện → nhập nháp → kiểm tra/duyệt →
phát hành → Clinical đọc kết quả. Bệnh phẩm, tiến độ kỹ thuật, điều kiện tài chính và báo
cáo kết quả là các chiều khác nhau; không nhồi tất cả vào một enum tuyến tính duy nhất.

Không ấn định trong tài liệu rằng lấy mẫu luôn phải xảy ra trước hoặc sau bước thu ở mọi
cơ sở. Cần chốt thứ tự áp dụng và đối chiếu clearance hiện tại. Những trạng thái đề xuất
không phải enum/API live; thêm trạng thái cần sửa hợp đồng có kiểm soát.

**Chọn đề xuất để tiến hành:**

- [x] **LAB-WF-01**
- [x] **LAB-WF-02**
- [x] **LAB-WF-03**
- [x] **LAB-WF-04**
- [x] **LAB-WF-05**
- [x] **LAB-WF-06**
- [x] **LAB-WF-07**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| LAB-WF-01 | Hàng việc Lab có bệnh nhân/lượt, khoa–bác sĩ chỉ định, dịch vụ, nhóm, ưu tiên được cấp, trạng thái mẫu, điều kiện và thời gian dự kiến; thiếu điều kiện không biến thành mất chỉ định. | [SRC] Có LabTest và lifecycle lookup theo sourceOrderId. [AUDIT] Cần đối chiếu hàng việc UI và các chiều dữ liệu, không suy từ status hiện tại. | [LabTest][C16], [Lifecycle lookup][C21] | P1 |
| LAB-WF-02 | Quản lý specimenId/accession, loại mẫu, người/thời điểm lấy và nhận, chấp nhận/từ chối mẫu, lý do và lần lấy lại; cho một mẫu phục vụ nhiều test khi phù hợp, không phải một test luôn bằng một ống. | [SRC] Model LabTest đã đọc chưa có specimen entity/reference riêng. [ĐỀ XUẤT] Lấy lại mẫu do không đạt không tự là chỉ định mới hoặc charge mới; chính sách ngoại lệ xét riêng. | [LabTest][C16], [FHIR Specimen][L11] | P0 |
| LAB-WF-03 | Tách Nháp → Chờ duyệt → Đã phát hành; có gửi duyệt, trả lại kèm lý do và gửi lại sau sửa. Nháp sửa được theo quyền, có kiểm soát xung đột; chưa là kết quả chính thức. | [SRC] addResults hiện ghi kết quả, chuyển COMPLETED và phát result event; chưa là luồng lưu nháp/gửi duyệt riêng. | [Lab service][C19], [LabTest][C16] | P1 |
| LAB-WF-04 | Phân biệt quyền nhập/gửi duyệt với quyền duyệt/phát hành theo phân công chuyên môn; lưu actor từng bước. Không mặc định LAB_TECH được phát hành hoặc được tự duyệt; điều kiện kiêm nhiệm phải được chốt theo quy trình cơ sở. ADMIN kỹ thuật không tự là người duyệt chuyên môn. | [SRC] Nhánh careContractVersion 1 lấy verifiedBy từ staff khi lưu kết quả; UI cho ADMIN/LAB_TECH thao tác. Chưa đủ bằng chứng có bước duyệt tách biệt hoặc capability chuyên môn ở mọi endpoint. | [Lab service][C19], [Form Lab][C20], [Tiêu chí 9.2][L14], [Quy trình Ninh Hòa][L15] | P0 |
| LAB-WF-05 | Chỉ hiển thị kết quả chính thức sau thao tác Duyệt và phát hành; chặn thiếu nội dung bắt buộc theo cấu hình được chọn. Kết quả tạm thời/từng phần và thông báo nguy cấp là nhánh riêng, không tự bật chỉ bằng luồng duyệt; không trình bày nháp như final. | [SRC] Enum hiện PENDING/AWAITING_PAYMENT/READY/IN_PROGRESS/COMPLETED/CANCELLED, chưa thể hiện kết quả từng phần hoặc giai đoạn duyệt. | [Lab status][C22], [Lab service][C19], [FHIR DiagnosticReport][L10] | P1 |
| LAB-WF-06 | Kết quả phát hành bị khóa; đính chính tạo revision có lý do/người duyệt, giữ bản cũ và chỉ rõ bản hiện hành. Chạy lại test hoặc lấy lại mẫu phải phân biệt đính chính văn bản. | [SRC] Có resultVersion và guard chặn kết quả terminal. Chưa có quy trình amended/corrected được chứng minh; không nới guard để sửa đè. | [LabTest][C16], [Lab service][C19], [FHIR DiagnosticReport][L10] | P1 |
| LAB-WF-07 | Giá trị nguy cấp cần đường thông báo có người nhận, thời điểm và xác nhận; cờ cao/thấp thông thường khác ngưỡng nguy cấp, không tự kết luận bệnh. | [AUDIT] Chưa xác minh luồng critical-result và acknowledgment. [ĐỀ XUẤT] Ngưỡng/đường báo do chuyên môn cấu hình, không dùng thông báo in-app gửi được làm bằng chứng bác sĩ đã nhận. | [FHIR Observation][L09], [QĐ2429][L13], [Business flows][D03] | P2 |

### Quyết định đã xác nhận ngày 10/11/2026: luồng duyệt kết quả Lab

Người dùng đã đồng ý bổ sung luồng duyệt vào checklist sau khi đối chiếu nguồn.
Chọn checkbox **LAB-WF-03/04/05** là đồng ý triển khai trong phạm vi dưới đây;
**chưa phải đã triển khai hoặc kiểm thử**. Sau đó, người dùng đồng ý toàn bộ 93 mục
phần 1–17 cùng ngày; xem quyết định tổng hợp ở phần 18.

**Luồng phần mềm được chọn:**

```text
NHÁP → CHỜ DUYỆT → ĐÃ PHÁT HÀNH
          │
          └── Trả lại kèm lý do → NHÁP → Gửi duyệt lại
```

- Lưu nháp giữ công việc đang nhập; không chuyển xét nghiệm thành COMPLETED và không
  phát sự kiện kết quả chính thức sang Clinical.
- Gửi duyệt xác nhận bản nhập đã sẵn sàng kiểm tra. Khóa sửa trực tiếp bản đang chờ;
  muốn sửa phải qua trả lại hoặc cơ chế thu hồi có kiểm soát khi chưa có quyết định duyệt.
- Gộp **Duyệt và phát hành** trong một thao tác cho MVP. Chỉ khi thành công mới ghi nhận
  kết quả chính thức và chuyển sang Clinical; chưa thêm trạng thái đã duyệt/chưa phát hành.
- Ghi nhận người nhập, người gửi, người duyệt/phát hành, thời điểm và lý do trả lại.
  Kiểm tra quyền tại backend. Chưa quyết định thêm role mới: ưu tiên quyền thao tác
  trong nhóm Lab; danh tính/chuyên môn người được cấp quyền phải được đối chiếu.
- Không suy cơ sở nhỏ là được tự duyệt. Điều kiện cho phép hoặc chặn người nhập trùng
  người duyệt cần được chốt và ghi thành quy tắc trước triển khai.
- Việc trả lại để sửa là xử lý bản chưa phát hành; không cho dùng đường này để sửa đè
  bản đã phát hành. Giữ guard hiện tại; quy trình hiệu chỉnh đầy đủ vẫn ở LAB-WF-06.

**Căn cứ và ranh giới suy luận:**

- [Bộ tiêu chí kèm QĐ2429/QĐ-BYT][L14], mục **8.18, trang 29**: rà soát trước trả;
  **8.21(m,n), trang 30**: người xác nhận, ngày duyệt/thời gian ban hành;
  **8.22, trang 30**: thẩm quyền ban hành/nhận; **9.2, trang 32**: quyền nhập,
  sửa và ban hành. Đây là tiêu chí chất lượng, không quy định ba trạng thái phần mềm.
- [BV ĐKKV Ninh Hòa, XN-QTQL 5.8.7, ngày 25/7/2024][L15], **5.1–5.3.2, trang 2–3/7**:
  người thực hiện kiểm tra, người có thẩm quyền xem xét/ký, rồi trả khoa lâm sàng.
  **5.3.6, trang 4–5/7**: người ký giờ trực thuộc danh sách được phép. Đây là ví dụ
  của một bệnh viện, không áp nguyên chức danh cho mọi cơ sở.
- Nguồn Ninh Hòa **5.3.5, trang 4/7** có thông báo tạm thời/qua điện thoại, ghi nhận
  và trả phiếu chính thức sau đó. Vì vậy, chưa chính thức không có nghĩa cấm mọi
  thông báo lâm sàng; không trộn nhánh đó với nháp nội bộ.

Nháp, Chờ duyệt, trả lại và gộp Duyệt/phát hành là **thiết kế được chọn cho MediFlow**
để thể hiện nghiệp vụ, không phải tên trạng thái bắt buộc do Bộ Y tế quy định.
Quyết định này không tự chọn toàn bộ template, nhận mẫu, hiệu chỉnh, cảnh báo nguy cấp,
hoặc các mục LAB-INT tại thời điểm xác nhận riêng luồng duyệt. Quyết định toàn bộ sau đó
đã bao gồm các mục này; vẫn cần chốt các lựa chọn còn điều kiện khi lập kế hoạch.

**Kết quả mong đợi sau khi sửa:** người làm Lab biết đúng mẫu và đúng việc; dữ liệu nháp
không trở thành kết quả chính thức của bệnh nhân; sai sót được xử lý mà không mất bản gốc.

## 17. Kết quả Lab đi về Clinical và tương thích dữ liệu

**Ranh giới:** Lab làm chủ mẫu, template và kết quả đã phát hành; Clinical làm chủ chỉ định
và diễn biến điều trị. Đây là thiết kế đề xuất về authority danh mục cần đối chiếu thêm
contract hiện tại, không tự gộp các DB hoặc tạo hai bản template cùng authoritative.

**Chọn đề xuất để tiến hành:**

- [x] **LAB-INT-01**
- [x] **LAB-INT-02**
- [x] **LAB-INT-03**
- [x] **LAB-INT-04**
- [x] **LAB-INT-05**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| LAB-INT-01 | Mỗi kết quả liên kết đúng sourceOrderId/testId/patientId/recordId và lượt; panel nhiều chỉ số vẫn truy về đúng chỉ định, không ghép theo tên xét nghiệm hoặc cùng ngày. | [SRC] LabTest và event có binding hiện có; [AUDIT] Cần khóa cả event version hiện hành và các consumer trước thêm catalogue/template/specimen. | [LabTest][C16], [Result event][C23], [Clinical Lab][C15] | P0 |
| LAB-INT-02 | Clinical hiển thị theo report/result version: trạng thái, mẫu/phương pháp khi có, giá trị–đơn vị–tham chiếu–cờ và người/thời điểm phát hành; không lấy template hiện tại để diễn giải lại bản cũ. | [SRC] Kết quả hiện là các chuỗi và conclusion; [AUDIT] Cần kiểm DTO/event/projection từng profile. Mở rộng phải có fixtures producer/consumer, giữ giá trị legacy. | [Result event][C23], [LabResult][C17], [Contract][D05] | P1 |
| LAB-INT-03 | Phát hành/đính chính có fact riêng, replay không nhân đôi; bản nháp không phát result-completed; sự kiện đến sai thứ tự không làm bản cũ ghi đè bản mới. | [SRC] Có event kết quả hoàn tất/outbox ở nhánh đã đọc; resultVersion không tự chứng minh thứ tự revision end-to-end. [ĐỀ XUẤT] Khóa schema và bổ sung quy tắc version/dedupe khi làm đính chính. | [Lab service][C19], [Result event][C23], [Data reliability][D08] | P0 |
| LAB-INT-04 | Dữ liệu cũ giữ nguyên tên/giá trị/đơn vị/tham chiếu và nhãn chưa gắn template; chỉ ánh xạ catalogue khi được xác minh, không suy loại/mẫu từ chuỗi tên mơ hồ. | [SRC] Legacy dùng labType và result text; chưa có template binding trong model đã kiểm. Không bắt chuyển hết thành số hoặc gán tham chiếu mới hồi tố. | [LabTest][C16], [LabResult][C17] | P1 |
| LAB-INT-05 | Bắt đầu bằng nhập tay có kiểm soát và template; kết nối máy, mã chuẩn liên thông, QC chuyên sâu, nuôi cấy/kháng sinh đồ đầy đủ là pha sau có hợp đồng riêng. | [AUDIT] Chưa xác minh tích hợp máy/LIS. Tham khảo FHIR để phân biệt order–specimen–observation–report, không tuyên bố dự án đã tương thích FHIR. | [FHIR diagnostics][L12], [QĐ2429][L13], [Contract][D05] | P2 |

### Gói Lab tối thiểu nên ưu tiên triển khai trước

1. LAB-CAT-01/02/03/05: danh mục, nhóm và panel rõ ràng.
2. LAB-TPL-01/02/03/06/07/08: ba kiểu mẫu đầu tiên và bộ quy tắc/phiên bản.
3. LAB-WF-02/03/04/05: nhận mẫu, nháp, duyệt và phát hành đầy đủ; nếu chưa làm nháp/duyệt,
   phải giữ cách gọi hiện tại là ghi nhận kết quả hoàn tất, không trình bày như đã có kiểm duyệt.
4. LAB-INT-01/02/03/04: trả đúng kết quả về đúng hồ sơ và giữ tương thích dữ liệu cũ.

Nhóm nước tiểu/vi sinh sâu, đính chính và giá trị nguy cấp có thể chọn sau theo nhu cầu.
Các mục P0 trong những nhánh được chọn vẫn cần được xử lý; việc để nhánh mở rộng ở pha
sau không cho phép công bố một khả năng đã đầy đủ khi source chưa hỗ trợ.

**Các ca nên dùng để hình dung đề xuất:** CBC thiếu chỉ số bắt buộc bị chặn phát hành;
xét nghiệm định tính có kết quả chưa xác định không bị ép âm/dương; mẫu không đạt được
lấy lại có liên kết; đổi template không đổi phiếu cũ; nháp không xuất hiện như final;
đính chính kết quả giữ bản cũ và không bị replay bản cũ ghi đè. Đây là mô tả hành vi để
bạn lựa chọn, chưa là kết quả đã kiểm thử.

## Quyết định toàn bộ ngày 10/11/2026 và các mục ảnh hưởng bổ sung

Người dùng xác nhận **“tôi đồng ý mọi checklist”**: 93 mục phần 1–17 được chọn.
Dấu chọn là đồng ý tiến hành, chưa phải đã sửa hoặc nghiệm thu. Với nội dung có điều
kiện/lựa chọn (Episode, tự duyệt, mức vi sinh, trả từng phần, pha sau), vẫn phải chốt
phương án cụ thể khi thiết kế; không mặc định bật mọi biến thể hoặc một LIS đầy đủ.

24 mục mới phần 18–21 đã được người dùng đồng ý cùng ngày 10/11/2026. Đây là công việc
thích nghi với thay đổi Clinical/Lab, không mở backlog chức năng độc lập cho các bên.
Một đánh giá có thể kết luận service giữ nguyên và chỉ cần kiểm tương thích, không
bắt mọi service phải sửa. Organization/Patient trong Core và Notification/Report trong
Support vẫn có authority/store riêng; không đọc chéo DB để xử lý phụ thuộc.

Các dòng dưới nêu thay đổi gốc → bên ảnh hưởng → công việc thích nghi. [SRC]/[DOC]
không chứng minh runtime đã bật; [AUDIT] còn cần kiểm nhánh/profile. Bằng chứng source
bổ sung ngày 10/11/2026 chỉ là đọc tĩnh, chưa chạy API/UI hoặc integration test.

## 18. Ảnh hưởng định danh, quyền và dữ liệu nền: Core, Gateway, Clinical, Lab

**Chọn đề xuất để tiến hành:**

- [x] **IMP-ID-01**
- [x] **IMP-ID-02**
- [x] **IMP-ID-03**
- [x] **IMP-ID-04**
- [x] **IMP-ID-05**
- [x] **IMP-ID-06**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| IMP-ID-01 | REC-03/04 → Clinical, Lab, Billing, Pharmacy, Report: dùng mã riêng cho quá trình theo dõi dài hạn; giữ mã lượt khám và khóa thanh toán hiện tại, không đổi nghĩa careEpisodeId đã phát hành thành mã nhóm dài hạn. | [DOC] registrationId = appointmentId = careEpisodeId = EXAM sourceId; [SRC] Lab và Pharmacy đã dùng careEpisodeId. Tên này không chứng minh REC-04 đã có. | [Contract][D05], [Lab payload][C24], [Pharmacy creation][C26] | P0 |
| IMP-ID-02 | ACL/COL/LAB-WF-04 → Organization/Core, Clinical, Lab: chốt nơi làm chủ quyền duyệt/phân công, API cấp–thu hồi và kiểm tra chuyên môn; cập nhật client/snapshot nếu mở contract. Không suy LAB_TECH hay trưởng khoa là được duyệt mọi test. | [SRC] Có SchedulingAuthority cho lịch và role LAB_TECH trong common. [AUDIT] Chưa chứng minh authority duyệt Lab hiện hữu; không dùng capability của lĩnh vực khác thay thế. | [Scheduling authority][C30], [Roles][C29], [System design][D02] | P0 |
| IMP-ID-03 | Endpoint mới về Episode/đính chính/duyệt → Gateway và service đích: thêm route chính xác theo method/path, kiểm quyền tại backend, phân biệt người bệnh tự xem với staff/service identity. Nếu thêm role/claim thì cập nhật nơi phát, xác minh và sử dụng. | [SRC] Gateway có route scheduling riêng và OrganizationAuthClient; role chung ở common. [AUDIT] Chưa có hợp đồng live được khóa cho endpoint mới. | [Gateway routes][C31], [Gateway auth][C32], [Roles][C29] | P0 |
| IMP-ID-04 | REC-06 và template tham chiếu → Patient/Core, Clinical, Lab: inventory tiền sử/dị ứng/dấu hiệu sinh tồn, chọn một authority mỗi trường. Lab nhận dữ liệu liên quan đủ dùng và chụp thông tin áp dụng lúc phát hành; thiếu dữ liệu không suy bình thường. | [SRC] Record tham chiếu patientId. [AUDIT] Chưa kiểm toàn bộ nơi lưu dữ liệu Patient; không kết luận phải chuyển mọi tiền sử về Core hoặc thêm field trùng. | [Record][C01], [LabResult][C17], [System design][D02] | P0 |
| IMP-ID-05 | CL-SCH/CL-QUE và đổi bác sĩ/phòng → Organization/Core và Clinical: cập nhật eligibility/authority theo revision, kiểm lại thao tác nhạy cảm khi nhân sự/phòng đổi trạng thái; xử lý buổi có bệnh nhân. Không sửa hồi tố tác giả/khoa hồ sơ cũ theo hồ sơ nhân sự mới. | [SRC] Có SchedulingAuthority và session/shift. [AUDIT] Cần trace refresh/sự kiện thu hồi trước mở contract mới, chưa kết luận cache hiện tại sai. | [Scheduling authority][C30], [Session][C13], [Shift][C12] | P0 |
| IMP-ID-06 | ACL-01/03 → Clinical, Lab, Pharmacy, Gateway: thống nhất bằng chứng quyền đọc tài liệu liên quan, kiểm từng context; thêm API xác minh tối thiểu nếu cần, xử lý timeout an toàn. Quyền đọc lịch sử không cấp quyền ghi; service token không thay quyền người dùng. | [SRC] GET record đọc rộng, follow-up kiểm tác giả; Pharmacy có prescription-context lookup. [AUDIT] Cần kiểm đủ print/download/patient-self. | [Record service][C02], [Follow-up][C03], [Pharmacy client][C25], [Contract][D05] | P0 |

## 19. Ảnh hưởng Billing và Pharmacy khi đổi lượt, chỉ định và kết quả

**Chọn đề xuất để tiến hành:**

- [x] **IMP-FIN-01**
- [x] **IMP-FIN-02**
- [x] **IMP-FIN-03**
- [x] **IMP-FIN-04**
- [x] **IMP-RX-01**
- [x] **IMP-RX-02**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| IMP-FIN-01 | DAY/REC/CL-REG → Billing và Clinical: phân biệt lượt chuyên môn và nguồn tính phí. Episode, ghi chú, bàn giao hoặc xem lại không tự tạo EXAM charge; khóa issuer và dedupe khi sửa lifecycle lượt. | [DOC] Manifest tách appointment.status.changed và medicalrecord.created theo mode, không double-charge. [AUDIT] Phải xác minh mode/issuer đang bật. | [Contract][D05], [Exam lifecycle][C14] | P0 |
| IMP-FIN-02 | LAB-CAT panel → Lab, Clinical, Billing: mapping serviceCode/panelCode với priceCode và đơn vị charge; nhiều analyte không sinh charge theo số dòng kết quả. Billing giữ quyền giá, danh mục chuyên môn không tự ghi đè giá. | [SRC] LabTest có priceCode. [DOC] lab.request.created tạo planned charge ở mode tương ứng. [AUDIT] Chưa chứng minh mapping panel đầy đủ. | [LabTest][C16], [Billing intake][C33], [Contract][D05] | P0 |
| IMP-FIN-03 | Nháp/duyệt/chạy lại/hiệu chỉnh → Lab và Billing: giữ nguồn LAB_TEST, không thu thêm chỉ vì resultVersion thay đổi. Dịch vụ phát sinh thật phải có chỉ định và chính sách riêng; clearance/override vẫn ràng buộc đúng nguồn. | [SRC] Ghi kết quả có guard clearance theo nhánh. [DOC] Charge từ request, không phải mỗi lần lưu kết quả. [AUDIT] Rà soát bindings trước đổi trạng thái. | [LabTest][C16], [Lab service][C19], [Billing intake][C33], [Contract][D05] | P0 |
| IMP-FIN-04 | Hủy/đổi buổi/bác sĩ hoặc test đã nhận → Clinical, Lab, Billing: đối chiếu trạng thái, gửi exact-source/actor/reason; tách void chưa thu và nghĩa vụ hoàn đã thu. Không ngầm chuyển clearance/thanh toán sang chỉ định mới. | [DOC] Cancellation có exact-source và khoản hoàn riêng. [SRC] Lab lookup theo sourceOrderId. [AUDIT] Cần kiểm từng nhánh đã nhận mẫu/đã chạy/đã phát hành. | [Lifecycle lookup][C21], [Contract][D05], [Reliability][D08] | P0 |
| IMP-RX-01 | REC-03/04/COL và đổi bác sĩ → Pharmacy và Clinical: giữ prescription-context đúng lượt/patient/doctor/department/quyền kê; đổi DTO/decoder cùng lúc nếu cần. Không thay recordId bằng mã quá trình hoặc tự sao chép đơn cũ. | [SRC] Pharmacy gọi /records/{id}/prescription-context; creation kiểm context/identity. [AUDIT] Khóa fixture khi đổi ACL/context proof. | [Pharmacy client][C25], [Pharmacy creation][C26], [Contract][D05] | P0 |
| IMP-RX-02 | AMD và kết quả Lab hiệu chỉnh → Clinical/Pharmacy: hiển thị thông tin mới để bác sĩ đánh giá, không tự sửa đơn đã ký/cấp hoặc hoàn kho. Nếu đổi điều trị, dùng quy trình đơn thay thế/hủy phù hợp và giữ liên kết đơn gốc. | [SRC] Có creation theo context. [AUDIT] Chưa trace đủ cancel/dispense khi có hiệu chỉnh; đây là đề xuất giữ ranh giới, chưa phải tính năng phản ứng amendment đã có. | [Pharmacy creation][C26], [Contract][D05], [Record][C01] | P0 |

## 20. Ảnh hưởng trả kết quả, Notification và Report

**Chọn đề xuất để tiến hành:**

- [x] **IMP-LAB-01**
- [x] **IMP-NOT-01**
- [x] **IMP-NOT-02**
- [x] **IMP-REP-01**
- [x] **IMP-REP-02**
- [x] **IMP-DOC-01**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| IMP-LAB-01 | LAB-WF-03/05/06 → Lab producer và Clinical consumer: chốt snapshot report/template/specimen/actor theo version; nháp không phát fact chính thức. Nâng bên nhận khi hỗ trợ hiệu chỉnh, không chỉ tăng resultVersion tại Lab. | [SRC] Clinical consumer bắt resultVersion = 1 khi sourceVersion = 1; revision lớn hơn hiện sẽ bị từ chối. Lab payload có verifiedBy/resultVersion nhưng chưa là workflow duyệt. | [Clinical consumer][C27], [Lab payload][C24], [Lab service][C19] | P0 |
| IMP-NOT-01 | Dời mốc sang phát hành → Notification trong Support: thông báo có kết quả sau phát hành; thêm thông báo hiệu chỉnh dẫn đến bản hiện hành, dedupe theo mục đích/revision. Nháp hoặc trả lại không báo đã có kết quả cho người bệnh. | [SRC] PatientResultNoticeDecoder nhận lab.result.created, kiểm envelope version 1/completedAt và dựng thông báo sẵn sàng; TYPES đã đọc chưa có hiệu chỉnh. | [Notice decoder][C28], [Contract][D05] | P0 |
| IMP-NOT-02 | Đổi lịch/bác sĩ/lời hẹn và nguy cấp → Clinical/Lab/Notification: thông báo đúng người/nội dung/link theo trạng thái. Lời hẹn không tự đặt suất; critical cần xác nhận người nhận chuyên môn riêng, notification.sent không chứng minh đã xử trí. | [SRC] Decoder follow-up hướng người bệnh liên hệ đăng ký. [AUDIT] Chưa chứng minh đủ nhắc lịch/đổi lịch/critical acknowledgment. | [Notice decoder][C28], [Contract][D05], [Business flows][D03] | P1 |
| IMP-REP-01 | REC-04 và DAY/CL-EXM → Report trong Support: tách người bệnh duy nhất, lượt hoàn tất/tái khám và quá trình theo dõi; chốt ngày/khoa ghi nhận. Episode không tăng lượt khám, đổi bác sĩ không nhân đôi đóng góp. | [DOC] medicalrecord.completed dùng outpatient count. [SRC] Có OutpatientReportFactConsumer; ReportEventConsumer legacy tách profile. [AUDIT] Chưa kiểm đủ aggregation active. | [Outpatient report][C34], [Legacy report][C35], [Contract][D05] | P1 |
| IMP-REP-02 | Panel, ngày thực hiện/phát hành, hiệu chỉnh → Report: chốt đếm test dịch vụ hay analyte và cách đo thời gian chờ. Revision không thành test mới; điều chỉnh đóng góp phải có nguồn và replay an toàn, không đếm lặp số lượng/doanh thu. | [SRC] Legacy report dùng labId/performedDate/departmentId và bị loại khỏi profile MVP. [AUDIT] Không suy active projection có cùng logic; kiểm cả mode thực dùng. | [Legacy report][C35], [Outpatient report][C34], [Contract][D05] | P1 |
| IMP-DOC-01 | Template/version và đính chính → Clinical/Lab, nơi in/xuất, kênh người bệnh: dùng snapshot đúng bản, trạng thái/người xác nhận; bản sửa dẫn chiếu bản trước. Link cũ không âm thầm đổi tài liệu; tải/in kiểm quyền như đọc. | [SRC] LabResult là chuỗi, form generic. [AUDIT] Cần inventory PDF/print/download/storage hiện có, không mặc định tồn tại dịch vụ tệp riêng. | [LabResult][C17], [Form Lab][C20], [Lab payload][C24], [Contract][D05] | P1 |

## 21. Tương thích hợp đồng, ứng dụng và thứ tự chuyển đổi

**Chọn đề xuất để tiến hành:**

- [x] **IMP-CON-01**
- [x] **IMP-CON-02**
- [x] **IMP-APP-01**
- [x] **IMP-APP-02**
- [x] **IMP-REL-01**
- [x] **IMP-REL-02**

| Mã checklist | Nội dung thay đổi | Nội dung hiện tại | Nguồn tham khảo | Mức độ ưu tiên |
|---|---|---|---|---|
| IMP-CON-01 | Mọi DTO/event đổi → producer/consumer: lập bản đồ method/path/type/version/profile/flag và khóa fixture trước sửa. Phân loại thêm field, enum, đổi nghĩa; nêu bên phải sửa và bên giữ nguyên sau đối chiếu. | [DOC] Canonical yêu cầu source lock M6-02. [SRC] Clinical/Notification kiểm version; Report legacy từ chối envelope mới. Thêm field không đảm bảo tương thích ngữ nghĩa. | [Contract][D05], [Clinical consumer][C27], [Notice decoder][C28], [Legacy report][C35] | P0 |
| IMP-CON-02 | Fact phát hành/hiệu chỉnh/đổi lịch → consumer: thống nhất eventId/sourceId/revision/thứ tự, giữ outbox/inbox/retry/DLQ. Không cho bản cũ ghi đè hoặc charge/notice lặp; contract không hiểu cần xử lý an toàn, không ACK-drop chứng cứ tài chính. | [DOC] Có reliability và manifest deferred. [SRC] Consumer có reject contract sai. [AUDIT] Chưa có chứng cứ end-to-end cho revision mới. | [Reliability][D08], [Contract][D05], [Clinical consumer][C27], [Outpatient report][C34] | P0 |
| IMP-APP-01 | API/trạng thái/quyền đổi → web Clinical/Lab và màn hình liên quan Billing/Pharmacy/Organization: sửa types/API/form/filter/error theo contract, hiển thị nháp/chờ/chính thức/bản sửa; backend vẫn kiểm quyền. Phối hợp đúng ownership frontend. | [SRC] Form Lab hiện lưu trực tiếp, các dòng nhập text. [AUDIT] Inventory route trước sửa; phụ thuộc backend không tự cấp quyền sửa feature frontend của người khác. | [Form Lab][C20], [Frontend ownership][D12], [Contract][D05] | P1 |
| IMP-APP-02 | API hồ sơ/kết quả/lịch đổi → mobile: inventory repository/DTO và màn hình gọi Gateway, sửa parsing/trạng thái/quyền theo slice thực có; chọn tương thích client cũ hoặc kế hoạch nâng bắt buộc. Không tự mở mọi workspace chưa hoàn thiện. | [SRC] Có GatewayMedicalRecordRepository/GatewayPatientLabResults. [DOC] Mobile giới hạn theo slice. [AUDIT] Chưa chạy native/web hoặc kiểm mọi màn hình. | [Mobile records][C36], [Mobile Lab][C37], [Mobile scope][D13] | P1 |
| IMP-REL-01 | Mở template/Episode/specimen/revision → store chủ quản và projection: migration giữ UUID nguồn/text legacy, không biến kết quả cũ hoàn tất thành nháp hoặc gửi thông báo hàng loạt. Backfill phân biệt business event; mapping dữ liệu phải có nguồn. | [SRC] Lab lưu text và resultVersion. [AUDIT] Chưa khảo sát dữ liệu môi trường; không tự nhóm mọi record cùng khoa hoặc suy specimen theo tên test. | [LabTest][C16], [LabResult][C17], [Record][C01], [Reliability][D08] | P0 |
| IMP-REL-02 | Triển khai liên service → lập thứ tự schema/adapter và consumer hiểu bản mới trước bật producer; có flag, quan sát lỗi và phương án dừng/khôi phục. Kiểm luồng chéo context, client cũ–mới và rollback; không xóa kết quả đã phát hành để rollback. | [DOC] Có acceptance runbook và reliability. [AUDIT] Chưa có cutover plan cho gói này; xác định queue/profile bật, không giả định dual-write chống lặp hoặc bật Inpatient/Surgery deferred. | [Acceptance][D14], [Reliability][D08], [System design][D02] | P0 |

### Thứ tự phối hợp đề xuất

1. Chốt mã quá trình/mã lượt, quyền và bản đồ hợp đồng trước thay đổi model/API.
2. Chốt danh mục–giá, context kê đơn; không đổi khóa tài chính chỉ vì đổi hiển thị.
3. Làm Lab cùng khả năng nhận của Clinical, rồi nối Notification/Report theo version.
4. Thích nghi web/mobile; chuyển dữ liệu và bật theo gói có quan sát, có phương án khôi phục.

Kế hoạch triển khai phải chỉ rõ producer/consumer, mode/profile và bằng chứng tích hợp.
Thứ tự này là đề xuất phối hợp, không phải kết quả nghiệm thu.

## Thứ tự lựa chọn khuyến nghị

1. **Cốt lõi hồ sơ:** REC-01/02/03, ACL-01/02/03, AMD-01/03, DOC-01/03 và UX-02.
   Làm rõ lịch sử, tác giả, quyền ghi và tính bất biến trước.
2. **Các lần khám liên quan:** DAY-01/02/03/05, COL-03/04/05 và DOC-04.
   Phân biệt khám tiếp, khám lại và nhận định mới của bác sĩ khác.
3. **Nghiệp vụ bổ sung:** COL-01, AMD-02 và REC-06 nếu muốn hỗ trợ bàn giao, đính chính
   và nội dung khám đầy đủ hơn.
4. **Tăng chiều sâu khi cần:** REC-04, COL-02, AMD-04/06 và VIEW-03.
   Không cần làm tất cả để sửa cách xem lịch sử nhiều bác sĩ.

5. **Lịch và tiếp nhận:** CL-SCH-01–06, CL-REG-01–06 và CL-QUE-01–05; ưu tiên làm rõ quan
   hệ ca/buổi và ảnh hưởng đến phiếu cũ trước thêm chức năng tự động phân lịch.
6. **Vòng đời khám xuyên Clinical:** CL-EXM-01–05 và CL-INT-01–04; phối hợp với phần hồ sơ
   để không tạo hai bộ trạng thái khác nhau cho cùng một lượt.

7. **Lab theo yêu cầu bổ sung:** chọn gói tối thiểu ở phần 17; thống nhất danh mục/template
   trước thay đổi luồng nháp–duyệt và event trả Clinical.

93 mục phần 1–17 đã được đồng ý; thứ tự này là gợi ý triển khai, không phải đã hoàn thành. Mục đang hoạt động đúng có thể chỉ cần giữ
nguyên và kiểm tra tương thích khi sửa phần phụ thuộc.

## Cách bạn xác nhận

Bạn có thể chọn theo mã hoặc theo phần, kèm ngoại lệ. Chỉ cập nhật trạng thái khi có quyết
định của bạn; hiện toàn bộ 117 mục phần 1–21 đã được chọn. Ví dụ:

- “Đồng ý phần 1 và 2, trừ REC-04 và ACL-04.”
- “Làm AMD-02 nhưng chỉ bổ sung bản đính chính, chưa làm ký số.”
- “Chưa làm bàn giao; trước mắt cho bác sĩ khác đọc lịch sử và ghi lượt mới của mình.”

Sau khi xác nhận, phạm vi sửa là các mục bạn đã chọn cùng điều kiện được ghi lại. Việc
triển khai, kiểm tra và cập nhật quy tắc canonical sẽ có kế hoạch riêng; không dùng dấu
checkbox đã chọn ở đây làm bằng chứng đã hoàn thành.

## Nguồn tham khảo và phạm vi áp dụng

### Căn cứ Việt Nam

- **[Luật 15/2023/QH15][L01]:** hồ sơ, trách nhiệm, hội chẩn và khai thác; không quy định
  phần mềm phải dùng tên role hoặc aggregate cụ thể.
- **[Thông tin chính thức về TT13/2025/TT-BYT][L02]:** hồ sơ điện tử, xác nhận và lưu trữ;
  một nút hoàn tất không chứng minh đã đáp ứng toàn bộ quy định.
- **[Luật 91/2025/QH15 tại Bộ Công an][L03]:** tham chiếu bảo vệ dữ liệu cá nhân khi xác
  định quyền khai thác; không suy role là quyền xem vô hạn.
- **[Quy chế BV Lao và Bệnh phổi Quy Nhơn][L04]:** ví dụ thực tế về đính chính hồ sơ lưu
  trữ, không áp nguyên quy trình phê duyệt của cơ sở này cho mọi phòng khám.
- **[Hướng dẫn thanh toán theo TT39/2024][L05]:** chỉ tham khảo tình huống quay lại trong
  ngày/khám nhiều chuyên khoa; tài liệu này không đề xuất sửa cách tính phí.
- **[Bạch Mai: đủ kết quả quay lại bác sĩ][L06]:** minh họa việc tiếp tục khám sau xét nghiệm,
  không có nghĩa phải tạo một hồ sơ mới khi nhận kết quả.

### Nguồn bổ sung cho Lab

- [Danh mục xét nghiệm Từ Dũ][L07] và [Khoa xét nghiệm Bạc Liêu][L08]: ví dụ phân nhóm
  tại cơ sở Việt Nam, không dùng giá/danh mục cũ làm cấu hình hiện hành của MediFlow.
- [FHIR R4 Observation][L09], [DiagnosticReport][L10], [Specimen][L11] và [Diagnostics][L12]:
  nguồn tham khảo mô hình dữ liệu. Các field/enum đề xuất là suy luận thiết kế cho MediFlow,
  không yêu cầu triển khai nguyên một FHIR server và không phải quy định pháp luật Việt Nam.
- [Cục Quản lý KCB: QĐ2429/QĐ-BYT][L13]: tham khảo định hướng quản lý chất lượng xét nghiệm;
  cần đối chiếu bộ tiêu chí và quy chế cơ sở khi lập yêu cầu tuân thủ cụ thể, không dùng tài
  liệu này như chứng nhận phòng xét nghiệm đã đạt chất lượng. Bản tiêu chí cụ thể và quy trình
  Ninh Hòa đã đối chiếu thêm ngày 10/11/2026 tại [L14] và [L15].

### Tài liệu và source

Các mã D/C trong bảng dẫn trực tiếp tới tài liệu hoặc file source. Hiện trạng kế thừa lần
đối chiếu đã ghi tại đầu file; lần mở rộng tài liệu này đã đọc thêm source lịch/ca, start-exam, model/DTO/service Lab
và form nhập kết quả, nhưng không chạy lại
API/UI hoặc kiểm thử nghiệp vụ. Có source không đồng nghĩa flag đã bật hoặc luồng đã được
nghiệm thu trên môi trường của bạn.
Đặc biệt, những mục [AUDIT] chưa phải kết luận source đang thiếu chức năng.

## Ghi chú thực hiện Đợt 4 — 11/11/2026

Đã làm trên nhánh `develop/mediflow-v2.0`, nghiệm thu ở [`26-v2-wave-4-acceptance.md`](../plans/26-v2-wave-4-acceptance.md):
REC-01, REC-03, REC-04, REC-05, REC-06, AMD-02, AMD-04, AMD-05, AMD-06, COL-01, COL-02, DOC-01..04, UX-01, UX-04,
VIEW-03, MIG-02, IMP-ID-04, IMP-RX-01, IMP-RX-02, IMP-REP-01. Quá trình điều trị sau cờ `mediflow.v2.treatment-course`;
bàn giao, hội chẩn và đính chính sau cờ `mediflow.v2.record-amendment`; cả hai mặc định tắt.

[L01]: https://xaydungchinhsach.chinhphu.vn/toan-van-luat-15-2023-qh15-kham-benh-chua-benh-119231127164453959.htm
[L02]: https://baochinhphu.vn/huong-dan-moi-nhat-trien-khai-ho-so-benh-an-dien-tu-102250610172135659.htm
[L03]: https://bocongan.gov.vn/chinh-sach-phap-luat/co-so-du-lieu-van-ban/luat-bao-ve-du-lieu-ca-nhan-1753688803
[L04]: https://benhvienlaophoiquynhon.com.vn/wp-content/uploads/2025/09/427_QD-BVLPQN_26092025-signed_01.pdf
[L05]: https://baochinhphu.vn/bo-y-te-huong-dan-thanh-toan-tien-kham-benh-trong-mot-so-truong-hop-102241122160114514.htm
[L06]: https://bachmai.gov.vn/bai-viet/app-bach-mai-care-ra-mat-tinh-nang-moi-co-du-ket-qua-dien-thoai-tu-bao-nguoi-benh-khong-con-phai-cho-doi?id=5eddba7e-d16d-4a11-8a6d-3b95202070f5
[D02]: ../plans/01-system-design.md
[D03]: ../plans/02-business-flows.md
[D04]: ../ai/README.md
[D05]: ../plans/03-contracts.md
[D08]: ../plans/04-data-reliability.md
[D10]: 02-medical-record-read-access.md
[D11]: 01-multi-doctor-medical-record.md
[C01]: ../../backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/MedicalRecord.java
[C02]: ../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/MedicalRecordApplicationService.java
[C03]: ../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/FollowUpRecommendationApplicationService.java
[C04]: ../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/OutpatientRegistrationApplicationService.java
[C05]: ../../backend/clinical-service/src/main/resources/db/migration/V9__outpatient_scheduling_v2.sql
[C08]: ../../frontend/src/features/medical-record/components/MedicalRecordTable.tsx
[D06]: ../plans/10-scheduling-contract-acceptance.md
[C12]: ../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/DoctorWorkShiftApplicationService.java
[C13]: ../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/ConsultationSessionApplicationService.java
[C14]: ../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/ClinicalCareFinanceApplicationService.java
[C15]: ../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/ClinicalLabOrderApplicationService.java
[L07]: https://tudu.com.vn/vn/huong-dan-dich-vu/huong-dan/kham-benh/danh-muc-xet-nghiem/
[L08]: https://bvdkbaclieu.gov.vn/gioi-thieu/don-vi-truc-thuoc/khoa-can-lam-san/khoa-xet-nghiem
[L09]: https://hl7.org/fhir/R4/observation.html
[L10]: https://hl7.org/fhir/R4/diagnosticreport.html
[L11]: https://hl7.org/fhir/R4/specimen.html
[L12]: https://fhir.hl7.org/fhir/diagnostics-module.html
[L13]: https://kcb.vn/van-ban/quyet-dinh-so-2429-qd-byt-ngay-12-6-2017-ban-hanh-tieu-chi-danh-gia-muc-chat-luong-phong-xet-nghiem-y-hoc.html
[C16]: ../../backend/lab-service/src/main/java/com/mediflow/lab/domain/model/LabTest.java
[C17]: ../../backend/lab-service/src/main/java/com/mediflow/lab/domain/model/LabResult.java
[C18]: ../../backend/lab-service/src/main/java/com/mediflow/lab/application/dto/request/LabResultItem.java
[C19]: ../../backend/lab-service/src/main/java/com/mediflow/lab/application/service/LabApplicationService.java
[C20]: ../../frontend/src/features/lab/components/LabActions.tsx
[C21]: ../../backend/lab-service/src/main/java/com/mediflow/lab/application/service/LabOrderLifecycleService.java
[C22]: ../../backend/lab-service/src/main/java/com/mediflow/lab/domain/model/LabTestStatus.java
[C23]: ../../backend/lab-service/src/main/java/com/mediflow/lab/application/event/LabResultCreatedEvent.java

[L14]: https://kcb.vn/upload/2005611/20210723/3d486c540e73ba776981b50506ad7526Tieu-chi-danh-gia-muc-chat-luong-phong-xet-nghiem_final.pdf
[L15]: https://benhvienninhhoa.khanhhoa.gov.vn/Portals/0/Quy%20tr%C3%ACnh%20b%E1%BB%87nh%20vi%E1%BB%87n/XN-QTQL%205.8.7%20QT%20tr%E1%BA%A3%20k%E1%BA%BFt%20qu%E1%BA%A3%20x%C3%A9t%20nghi%E1%BB%87m.pdf

[C24]: ../../backend/lab-service/src/main/java/com/mediflow/lab/application/event/LabResultV2Payload.java
[C25]: ../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/infrastructure/client/PharmacyClinicalFeignClient.java
[C26]: ../../backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/application/service/CarePrescriptionCreationService.java
[C27]: ../../backend/clinical-service/src/main/java/com/mediflow/clinical/messaging/consumer/LabResultCreatedConsumer.java
[C28]: ../../backend/notification-service/src/main/java/com/mediflow/notification/infrastructure/messaging/PatientResultNoticeDecoder.java
[C29]: ../../backend/common/src/main/java/com/mediflow/common/security/Roles.java
[C30]: ../../backend/core-service/src/main/java/com/mediflow/organization/application/dto/response/SchedulingAuthority.java
[C31]: ../../backend/gateway/src/main/java/com/mediflow/gateway/config/SchedulingRouteConfiguration.java
[C32]: ../../backend/gateway/src/main/java/com/mediflow/gateway/auth/OrganizationAuthClient.java
[C33]: ../../backend/billing-service/src/main/java/com/mediflow/billing/messaging/consumer/BillingEventConsumer.java
[C34]: ../../backend/report-service/src/main/java/com/mediflow/report/messaging/consumer/OutpatientReportFactConsumer.java
[C35]: ../../backend/report-service/src/main/java/com/mediflow/report/messaging/consumer/ReportEventConsumer.java
[C36]: ../../mobile/lib/features/medical_record/data/gateway_medical_record_repository.dart
[C37]: ../../mobile/lib/features/lab/data/gateway_patient_lab_results.dart
[D12]: ../ai/15-frontend-ownership.md
[D13]: ../../mobile/README.md
[D14]: ../plans/06-acceptance-runbook.md
