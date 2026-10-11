# MediFlow Review 1 — Standard vs Detailed Comparison / So sánh bản Tiêu chuẩn và Chi tiết

| Document item / Hạng mục | Value / Giá trị |
|---|---|
| Purpose / Mục đích | Select and use the correct Review 1 report version / Chọn và sử dụng đúng phiên bản báo cáo Review 1 |
| Compared versions / Phiên bản so sánh | Standard and Detailed / Tiêu chuẩn và Chi tiết |
| Languages / Ngôn ngữ | English and Vietnamese in separate report files / Tiếng Anh và Tiếng Việt ở các file báo cáo riêng |
| Additional format / Định dạng bổ sung | Presentation version in both languages / Bản thuyết trình ở cả hai ngôn ngữ |

## 1. Quick Recommendation / Khuyến nghị nhanh

**English:** Use the Standard report as the default Review 1 submission when the reviewer expects a clear 15–20-page Word document. Use the Detailed report when the panel requires formal requirement identifiers, architecture constraints, data ownership, API/event rules, risk treatment, and acceptance traceability. Use the Presentation report as the speaking outline; it is not a replacement for the formal report.

**Tiếng Việt:** Dùng bản Tiêu chuẩn làm báo cáo Review 1 mặc định khi người đánh giá cần tài liệu Word rõ ràng khoảng 15–20 trang. Dùng bản Chi tiết khi hội đồng cần mã yêu cầu chính thức, ràng buộc kiến trúc, quyền sở hữu dữ liệu, quy tắc API/sự kiện, xử lý rủi ro và truy vết nghiệm thu. Dùng bản Thuyết trình làm dàn ý nói; bản này không thay thế báo cáo chính thức.

## 2. Side-by-Side Comparison / So sánh song song

| Criterion / Tiêu chí | Standard / Tiêu chuẩn | Detailed / Chi tiết |
|---|---|---|
| Primary purpose / Mục đích chính | Formal Review 1 report with balanced depth / Báo cáo Review 1 chính thức với độ sâu cân bằng | Specification and defense document with traceability / Tài liệu đặc tả và bảo vệ có truy vết |
| Expected Word length / Độ dài Word dự kiến | Approximately 15–20 pages after normal Word formatting / Khoảng 15–20 trang sau định dạng Word thông thường | Approximately 25–35 pages after normal Word formatting / Khoảng 25–35 trang sau định dạng Word thông thường |
| Reading time / Thời gian đọc | Shorter; suited to first review / Ngắn hơn; phù hợp vòng đánh giá đầu | Longer; suited to technical/customer examination / Dài hơn; phù hợp thẩm định kỹ thuật/khách hàng |
| Part A depth / Độ sâu Phần A | Context, problems, objectives, scope, roles, solution, limitations / Bối cảnh, vấn đề, mục tiêu, phạm vi, vai trò, giải pháp, giới hạn | Adds drivers, formal gaps, measurable outcomes, assumptions, dependencies, and risk treatment / Bổ sung động lực, khoảng trống, kết quả đo lường, giả định, phụ thuộc và xử lý rủi ro |
| Part B depth / Độ sâu Phần B | Stakeholders, role cycles, main flows, environment, security, acceptance / Stakeholder, chu trình vai trò, luồng chính, môi trường, bảo mật, nghiệm thu | Adds requirement IDs, flow-by-flow steps, ownership, API, events, NFRs, and traceability matrix / Bổ sung mã yêu cầu, bước theo luồng, quyền sở hữu, API, sự kiện, phi chức năng và ma trận truy vết |
| Functional requirement IDs / Mã yêu cầu chức năng | Not used; requirements are grouped in readable tables / Không dùng; yêu cầu được nhóm trong bảng dễ đọc | Uses stable `FR-*`, `API-*`, `EVT-*`, `SEC-*`, and `AC-*` references / Dùng tham chiếu ổn định `FR-*`, `API-*`, `EVT-*`, `SEC-*`, `AC-*` |
| User roles / Vai trò | Covers all nine roles / Bao phủ đủ chín vai trò | Covers all nine roles with create/read/control boundaries / Bao phủ đủ chín vai trò cùng ranh giới tạo/đọc/kiểm soát |
| Services / Dịch vụ | Covers all ten final-system contexts / Bao phủ đủ mười context của hệ thống hoàn chỉnh | Covers all ten contexts plus ownership and contract constraints / Bao phủ đủ mười context cùng quyền sở hữu và ràng buộc hợp đồng |
| Data-processing model / Mô hình xử lý dữ liệu | One consolidated actor-input-process-output-owner table / Một bảng tổng hợp actor-input-process-output-owner | Consolidated table plus detailed workflow steps / Bảng tổng hợp và các bước chi tiết theo quy trình |
| Cross-service design / Thiết kế chéo dịch vụ | Explains REST, events, outbox, idempotency / Giải thích REST, event, outbox, idempotency | Defines explicit API and asynchronous requirements / Xác định yêu cầu API và bất đồng bộ tường minh |
| Hardware/software / Phần cứng/phần mềm | Concise client/server recommendations / Khuyến nghị client/server cô đọng | Separate client/server profiles with verification caveats / Tách cấu hình client/server cùng điều kiện xác minh |
| Limitations / Giới hạn | Clear list of incomplete or unproven Review 1 outcomes / Danh sách rõ kết quả Review 1 chưa hoàn tất/chưa chứng minh | Risk IDs, current mitigation, and required completion / Mã rủi ro, giảm thiểu hiện tại và phần cần hoàn tất |
| Acceptance / Nghiệm thu | Fifteen narrative acceptance checks / Mười lăm kiểm tra nghiệm thu dạng mô tả | Fifteen traceable acceptance rows linked to requirements / Mười lăm dòng nghiệm thu liên kết yêu cầu |
| Best audience / Đối tượng phù hợp | Lecturer, supervisor, general customer representative / Giảng viên, hướng dẫn, đại diện khách hàng tổng quát | Technical panel, architect, tester, procurement or formal reviewer / Hội đồng kỹ thuật, kiến trúc sư, kiểm thử, mua sắm hoặc thẩm định chính thức |
| Editing effort / Công sức chỉnh sửa | Lower / Thấp hơn | Higher because references must remain consistent / Cao hơn vì tham chiếu phải nhất quán |
| Preparation effort / Công sức chuẩn bị | Moderate: consolidate evidence into readable sections / Trung bình: tổng hợp bằng chứng thành các mục dễ đọc | High: verify requirement IDs, actor failures, source evidence, and traceability / Cao: xác minh mã yêu cầu, nhánh lỗi actor, bằng chứng nguồn và truy vết |
| Risk of overwhelming audience / Nguy cơ quá tải người đọc | Low / Thấp | Moderate if presented without an executive summary / Trung bình nếu không có tóm tắt |

## 3. Content Mapping / Ánh xạ nội dung

| Standard section / Mục bản Tiêu chuẩn | Detailed expansion / Phần mở rộng trong bản Chi tiết |
|---|---|
| A1. Introduction and context / Giới thiệu và bối cảnh | A1 executive context and A2 formal problem / Bối cảnh tổng quan và phát biểu vấn đề chính thức |
| A2. Problem identification / Xác định vấn đề | A3 business drivers and A4 current-state gaps / Động lực nghiệp vụ và khoảng trống hiện trạng |
| A3. Objectives / Mục tiêu | A5 objectives with measurable evidence / Mục tiêu cùng bằng chứng đo lường |
| A4. Scope / Phạm vi | A6 business, platform, and excluded scope / Phạm vi nghiệp vụ, nền tảng và loại trừ |
| A5. Users and functions / Người dùng và chức năng | A7 create/read/prohibited responsibility matrix / Ma trận trách nhiệm tạo/đọc/bị kiểm soát |
| A6. Proposed solution / Giải pháp đề xuất | A8 architecture and component constraints / Kiến trúc và ràng buộc thành phần |
| A7. Limitations / Giới hạn | A9 assumptions/dependencies and A10 risks/remaining work / Giả định/phụ thuộc và rủi ro/công việc còn lại |
| B1. Stakeholders / Các bên liên quan | A3 stakeholder mapping plus B1 conventions / Ánh xạ stakeholder và quy ước yêu cầu |
| B2. Role data cycles / Chu trình vai trò | B2 functional requirements and B3 actor cycles / Yêu cầu chức năng và chu trình actor |
| B3. Cross-department flows / Luồng liên khoa | B4–B10 step-by-step workflow specifications / Đặc tả từng bước cho các quy trình |
| B4–B7. Environment / Môi trường | B16 hardware and B17 software / Phần cứng và phần mềm tách riêng |
| B8. Security/reliability / Bảo mật/độ tin cậy | B12 API, B13 events, B14 security, B15 non-functional / API, sự kiện, bảo mật, phi chức năng |
| B9. Acceptance / Nghiệm thu | B18 traceability and acceptance matrix / Ma trận truy vết và nghiệm thu |
| B10. Glossary / Thuật ngữ | B19 expanded glossary / Thuật ngữ mở rộng |

## 4. Evidence Coverage / Mức bao phủ bằng chứng

Both versions are aligned with the same source baseline. The difference is depth, not project status.

Cả hai phiên bản bám cùng một đường cơ sở mã nguồn. Khác biệt nằm ở độ sâu, không phải trạng thái dự án.

| Evidence area / Khu vực bằng chứng | Standard / Tiêu chuẩn | Detailed / Chi tiết |
|---|---|---|
| Ten final-system services / Mười dịch vụ của hệ thống hoàn chỉnh | Named and summarized / Nêu tên và tóm tắt | Assigned requirements, ownership, and workflow steps / Gán yêu cầu, quyền sở hữu và bước quy trình |
| Nine roles / Chín vai trò | Function table and data-cycle rows / Bảng chức năng và chu trình | Responsibility boundary and requirement linkage / Ranh giới trách nhiệm và liên kết yêu cầu |
| Java/Spring baseline | Listed in software requirements / Liệt kê trong yêu cầu phần mềm | Listed with platform separation and operational needs / Liệt kê cùng phân tách nền tảng và nhu cầu vận hành |
| Frontend baseline | Next.js, React, TypeScript, Tailwind, pnpm / Công nghệ frontend hiện tại | Same baseline plus gateway/API boundary requirements / Cùng baseline và yêu cầu ranh giới gateway/API |
| PostgreSQL/RabbitMQ/Eureka | Architecture and server software tables / Bảng kiến trúc và phần mềm server | Component constraints, events, recovery, and observability / Ràng buộc thành phần, sự kiện, khôi phục, quan sát |
| Mobile status | Explicit limitation / Nêu rõ giới hạn | Limitation with mitigation and required completion / Giới hạn cùng giảm thiểu và phần cần hoàn tất |
| Production sizing | Planning recommendation warning / Cảnh báo chỉ là khuyến nghị | Planning profiles plus validation requirements / Cấu hình lập kế hoạch và yêu cầu xác minh |

## 5. When to Choose Each Version / Khi nào chọn từng bản

### Choose Standard / Chọn bản Tiêu chuẩn when / khi

- The school or customer expects a compact Review 1 document.
- The report must be easy to read without extensive technical background.
- The submission page limit is strict.
- The main goal is to explain the problem, scope, actors, solution, and CRS clearly.
- The presentation and question session will cover deeper technical detail separately.

- Trường hoặc khách hàng cần tài liệu Review 1 gọn.
- Báo cáo cần dễ đọc mà không yêu cầu nền tảng kỹ thuật sâu.
- Giới hạn số trang chặt chẽ.
- Mục tiêu chính là giải thích rõ vấn đề, phạm vi, đối tượng, giải pháp và CRS.
- Phần thuyết trình/hỏi đáp sẽ giải thích kỹ thuật sâu riêng.

### Choose Detailed / Chọn bản Chi tiết when / khi

- The reviewer expects formal and traceable requirements.
- Architecture, event reliability, security, or service ownership may be challenged.
- Testers need requirement IDs for acceptance planning.
- The team needs a reference for implementation, integration, and review of the complete final system.
- A customer representative must distinguish final-system requirements from Review 1 completion gaps.

- Người đánh giá cần yêu cầu chính thức và truy vết được.
- Kiến trúc, độ tin cậy sự kiện, bảo mật hoặc quyền sở hữu dịch vụ có thể bị chất vấn.
- Kiểm thử viên cần mã yêu cầu để lập kế hoạch nghiệm thu.
- Nhóm cần tài liệu tham chiếu cho triển khai, tích hợp và review hệ thống cuối cùng đầy đủ.
- Đại diện khách hàng cần phân biệt yêu cầu hệ thống cuối cùng với khoảng trống cần hoàn tất tại Review 1.

## 6. Recommended Submission Package / Gói nộp khuyến nghị

| Use case / Trường hợp | Primary document / Tài liệu chính | Supporting document / Tài liệu hỗ trợ |
|---|---|---|
| English formal submission / Nộp chính thức tiếng Anh | `01-review-1-standard-en.md` | `03-review-1-detailed-en.md` as appendix/reference |
| Vietnamese formal submission / Nộp chính thức tiếng Việt | `02-review-1-standard-vi.md` | `04-review-1-detailed-vi.md` as appendix/reference |
| English presentation / Thuyết trình tiếng Anh | `05-review-1-presentation-en.md` | Detailed English for Q&A |
| Vietnamese presentation / Thuyết trình tiếng Việt | `06-review-1-presentation-vi.md` | Detailed Vietnamese for Q&A |
| Technical/customer defense / Bảo vệ kỹ thuật/khách hàng | Detailed report in the session language | Standard report as executive summary |

## 7. Word Conversion Guidance / Hướng dẫn chuyển sang Word

1. Use Heading 1 for the title, Heading 2 for Part/major sections, and Heading 3 for numbered subsections.
2. Use an A4 page, normal margins, 11–12 pt body text, and 1.15–1.5 line spacing.
3. Apply a single consistent table style; repeat header rows on multi-page tables.
4. Keep role names and service names in English code style for source traceability.
5. Add an automatic table of contents after applying Word heading styles.
6. If page count is too high, reduce spacing before shrinking font size.
7. Do not remove limitation statements; they protect the accuracy of the review.

1. Dùng Heading 1 cho tiêu đề, Heading 2 cho Phần/mục lớn và Heading 3 cho mục đánh số.
2. Dùng giấy A4, lề Normal, chữ nội dung 11–12 pt và giãn dòng 1,15–1,5.
3. Áp dụng một kiểu bảng thống nhất; lặp dòng tiêu đề khi bảng qua trang.
4. Giữ tên role và service bằng tiếng Anh dạng code để truy vết mã nguồn.
5. Thêm mục lục tự động sau khi áp dụng style tiêu đề Word.
6. Nếu số trang quá cao, giảm khoảng cách đoạn trước khi giảm cỡ chữ.
7. Không xóa các tuyên bố giới hạn vì chúng bảo vệ tính chính xác của báo cáo.

## 8. Final Decision / Quyết định cuối

| Decision question / Câu hỏi quyết định | Answer / Trả lời |
|---|---|
| Which version should be submitted first? / Nên nộp bản nào trước? | Standard / Tiêu chuẩn |
| Which version should support technical questions? / Bản nào hỗ trợ câu hỏi kỹ thuật? | Detailed / Chi tiết |
| Which version should be used while speaking? / Bản nào dùng khi nói? | Presentation / Thuyết trình |
| Can Detailed replace Standard? / Bản Chi tiết có thể thay Tiêu chuẩn? | Yes, if there is no strict page limit / Có, nếu không giới hạn trang chặt |
| Can Presentation replace the report? / Bản Thuyết trình có thể thay báo cáo? | No; it is a speaking aid / Không; đây là công cụ hỗ trợ nói |

**English conclusion:** The three versions form one layered package: Standard explains, Detailed proves and traces, and Presentation communicates. Select the layer that matches the audience while preserving the same source-aligned facts.

**Kết luận tiếng Việt:** Ba phiên bản tạo thành một gói nhiều lớp: bản Tiêu chuẩn giải thích, bản Chi tiết chứng minh và truy vết, bản Thuyết trình truyền đạt. Hãy chọn lớp phù hợp đối tượng nhưng giữ nguyên các dữ kiện bám sát mã nguồn.
