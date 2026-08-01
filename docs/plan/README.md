# 📋 Kế Hoạch Dự Án MediFlow — Semester 4 (08/2026 → 10/2026)

> **Dự án:** MediFlow — Hệ thống quản lý Bệnh viện / Phòng khám
> **Thời gian thực hiện:** 3 tháng (Tháng 8, 9, 10 năm 2026)
> **Nộp kế hoạch trước ngày:** 01-Aug-2026
> **Nhóm:** 3 thành viên

---

## 👥 Thành viên & Vai trò

| # | Thành viên | Vai trò chính | Trách nhiệm |
|---|-----------|--------------|-------------|
| 1 | **Nhóm trưởng (Harori)** | Trưởng nhóm / Backend Lead | Phân công công việc, tổng hợp hồ sơ, nộp giáo viên, thiết kế Backend (Spring Boot), chịu trách nhiệm tiến độ chung |
| 2 | Thành viên B | Frontend Lead | Thiết kế giao diện (GUI Standards), phát triển Frontend (Next.js), mobile (Flutter) |
| 3 | Thành viên C | Database / QA Lead | Thiết kế CSDL (Database Design), tài liệu nghiệp vụ, kiểm thử (Testing), biên soạn tài liệu |

> ⚠️ *Điền tên thật từng thành viên vào bảng trên trước khi nộp.*

---

## 📅 Tổng quan Timeline 3 tháng

| Tháng | Giai đoạn | Nội dung chính | Output |
|-------|-----------|----------------|--------|
| **Tháng 8** | Thiết kế (3 giai đoạn) | Đặc tả yêu cầu → Thiết kế giao diện & CSDL → Hoàn thiện hồ sơ | Bộ tài liệu thiết kế hoàn chỉnh |
| **Tháng 9** | Phát triển (Backend + DB) | Xây dựng 8 microservices, CSDL, API, events | Backend chạy được |
| **Tháng 10** | Phát triển (Frontend + Mobile) + Kiểm thử | Next.js, Flutter, tích hợp, kiểm thử, demo | Sản phẩm hoàn chỉnh + Báo cáo |

---

# PHẦN A — GIAI ĐOẠN THIẾT KẾ (Tháng 8/2026)

## 🏁 GIAI ĐOẠN 1: Khởi động & Đặc tả Yêu cầu (Tuần 1 — 03/08 → 09/08)

### Mục tiêu
- Xác định vấn đề của đề tài
- Phân tích yêu cầu khách hàng, bám sát theo tài liệu hướng dẫn dự án

### Chi tiết công việc & Deadline

| # | Công việc | Người phụ trách | Deadline | Kết quả bàn giao |
|---|-----------|----------------|----------|------------------|
| 1.1 | Họp nhóm kickoff: thống nhất mục tiêu, phạm vi dự án | Nhóm trưởng + Cả nhóm | 03/08 | Biên bản họp |
| 1.2 | Xác định vấn đề, bài toán của đề tài (Problem Statement) | Cả nhóm | 04/08 | Phần 1.1 tài liệu đặc tả |
| 1.3 | Xác định các vai trò người dùng: **Administrator, Customer, Staff/Expert** | Nhóm trưởng + Thành viên B | 05/08 | Bảng vai trò + chức năng |
| 1.4 | Phân tích yêu cầu chức năng theo từng vai trò (Use Case sơ bộ) | Thành viên B + C | 06/08 | Danh sách Use Case |
| 1.5 | Phân tích yêu cầu phi chức năng (bảo mật, hiệu năng, khả dụng) | Thành viên C | 07/08 | Danh sách NFR |
| 1.6 | Hoàn thành bộ biểu mẫu ban đầu (interview/requirement form) | Cả nhóm | 08/08 | Bộ biểu mẫu |
| 1.7 | **Tổng hợp tài liệu Đặc tả Yêu cầu (SRS) v1.0** | Nhóm trưởng | **09/08** | `docs/plan/giai-doan-1-srs.md` |

### ✅ Tiêu chí hoàn thành Giai đoạn 1
- [ ] Bảng vai trò người dùng (Administrator, Customer, Staff/Expert) hoàn chỉnh
- [ ] Mỗi vai trò có danh sách chức năng tương ứng rõ ràng
- [ ] Bộ biểu mẫu ban đầu hoàn thành
- [ ] SRS v1.0 được nhóm trưởng duyệt và nộp

---

## 🎨 GIAI ĐOẠN 2: Thiết kế Giao diện & Tiêu chuẩn (Tuần 2-3 — 10/08 → 23/08)

### Mục tiêu
- Xây dựng quy chuẩn giao diện (GUI Standards)
- Xây dựng luồng xử lý và thiết kế kiến trúc sơ bộ hệ thống

### Chi tiết công việc & Deadline

| # | Công việc | Người phụ trách | Deadline | Kết quả bàn giao |
|---|-----------|----------------|----------|------------------|
| 2.1 | Xây dựng GUI Standards: bố cục header/footer, màu sắc, font chữ | Thành viên B | 14/08 | Tài liệu GUI Standards + Style Guide |
| 2.2 | Thiết kế wireframe / mockup cho các màn hình chính | Thành viên B | 18/08 | Wireframe + Mockup |
| 2.3 | Thiết kế mô hình CSDL (Database Design): ERD + DDL | Thành viên C | 20/08 | ERD + `docs/eproject_general_plan/` |
| 2.4 | Thiết kế kiến trúc hệ thống sơ bộ (System Architecture) | Nhóm trưởng | 21/08 | Sơ đồ kiến trúc microservices |
| 2.5 | Thiết kế luồng xử lý nghiệp vụ chi tiết (Business Flow) | Cả nhóm | 22/08 | Tài liệu quy trình nghiệp vụ |
| 2.6 | **Tổng hợp tài liệu Thiết kế v1.0** | Nhóm trưởng | **23/08** | `docs/plan/giai-doan-2-design.md` |

### ✅ Tiêu chí hoàn thành Giai đoạn 2
- [ ] GUI Standards: header/footer, màu sắc, font chữ đầy đủ
- [ ] ERD mô tả đầy đủ các bảng nghiệp vụ (8 module)
- [ ] Kiến trúc hệ thống sơ bộ (microservices + gateway + Eureka)
- [ ] Quy trình nghiệp vụ chi tiết cho từng phân hệ

---

## 📦 GIAI ĐOẠN 3: Phân chia chi tiết & Hoàn thiện hồ sơ thiết kế (Tuần 4 — 24/08 → 31/08)

### Mục tiêu
- Hoàn thiện bảng phân công công việc chi tiết cho giai đoạn code
- Tổng hợp và kiểm duyệt toàn bộ gói tài liệu thiết kế

### Chi tiết công việc & Deadline

| # | Công việc | Người phụ trách | Deadline | Kết quả bàn giao |
|---|-----------|----------------|----------|------------------|
| 3.1 | Lập bảng phân công hoạt động chi tiết cho giai đoạn code | Nhóm trưởng | 27/08 | Bảng phân công (Phần B bên dưới) |
| 3.2 | Rà soát tính đồng bộ giữa SRS ↔ Design ↔ Database ↔ GUI | Cả nhóm | 28/08 | Biên bản rà soát |
| 3.3 | Chỉnh sửa, bổ sung theo kết quả rà soát | Cả nhóm | 29/08 | Tài liệu v2.0 |
| 3.4 | Kiểm duyệt toàn bộ gói tài liệu thiết kế (Final Review) | Nhóm trưởng | 30/08 | Gói tài liệu hoàn chỉnh |
| 3.5 | **Nộp gói tài liệu thiết kế cho giáo viên** | Nhóm trưởng | **31/08** | Hồ sơ thiết kế hoàn chỉnh |

### ✅ Tiêu chí hoàn thành Giai đoạn 3
- [ ] Bảng phân công chi tiết gắn liền với từng task
- [ ] Toàn bộ tài liệu đồng bộ, nhất quán (SRS ↔ Design ↔ DB ↔ GUI)
- [ ] Gói hồ sơ thiết kế được nộp đúng hạn 31/08

---

# PHẦN B — GIAI ĐOẠN PHÁT TRIỂN (Tháng 9-10/2026)

> *Bảng phân công chi tiết này được hoàn thiện trong Giai đoạn 3 (Task 3.1).*

## 🗓️ THÁNG 9: Phát triển Backend & CSDL

| Tuần | Thời gian | Công việc | Người phụ trách | Output |
|------|-----------|-----------|------------------|--------|
| 9.1 | 01/09 – 07/09 | Setup infra (Docker, PostgreSQL, RabbitMQ), dựng khung 8 services | Nhóm trưởng | Backend skeleton chạy được |
| 9.2 | 08/09 – 14/09 | Triển khai **common + organization + patient** (domain, JPA, API, events) | Nhóm trưởng + C | 3 services hoàn chỉnh |
| 9.3 | 15/09 – 21/09 | Triển khai **clinical + lab + pharmacy** | Nhóm trưởng + B | 3 services hoàn chỉnh |
| 9.4 | 22/09 – 28/09 | Triển khai **billing (saga) + notification + report** + gateway JWT | Cả nhóm | Toàn bộ backend + tests |
| 9.5 | 29/09 – 30/09 | Test tích hợp backend, fix bug | Cả nhóm | Backend stable v1.0 |

## 🗓️ THÁNG 10: Phát triển Frontend + Mobile + Kiểm thử

| Tuần | Thời gian | Công việc | Người phụ trách | Output |
|------|-----------|-----------|------------------|--------|
| 10.1 | 01/10 – 07/10 | Dựng khung Frontend (Next.js): auth, layout, các trang chính | Thành viên B | Frontend skeleton |
| 10.2 | 08/10 – 14/10 | Hoàn thiện Frontend: patient, appointment, lab, pharmacy, billing | Thành viên B | Frontend v1.0 |
| 10.3 | 15/10 – 21/10 | Dựng khung Mobile (Flutter): auth, navigation, features | Thành viên B | Mobile skeleton |
| 10.4 | 22/10 – 26/10 | Kiểm thử toàn hệ thống (test case, QA, fix bug) | Thành viên C | Test report |
| 10.5 | 27/10 – 31/10 | Tổng hợp, chuẩn bị demo, viết báo cáo, nộp sản phẩm | Cả nhóm | **Sản phẩm hoàn chỉnh** |

---

## 📁 Cấu trúc tài liệu dự án

```
docs/
├── plan/
│   ├── README.md                    ← Kế hoạch tổng thể (file này)
│   ├── giai-doan-1-srs.md           ← Đặc tả yêu cầu (09/08)
│   ├── giai-doan-2-design.md        ← Thiết kế giao diện + CSDL + kiến trúc (23/08)
│   ├── giai-doan-3-final-review.md  ← Kiểm duyệt hồ sơ (31/08)
│   └── phan-cong.md                 ← Bảng phân công chi tiết (27/08)
├── ai/                              ← Coding standards (đã có)
└── eproject_general_plan/           ← Design docs (đã có)
```

---

## 📌 Checklist nộp hồ sơ

| Hạng mục | Deadline | Trạng thái |
|----------|----------|-----------|
| Kế hoạch tổng thể (file này) nộp giáo viên + CC thành viên | **01/08** | ⬜ |
| SRS v1.0 | 09/08 | ⬜ |
| GUI Standards + Wireframe | 18/08 | ⬜ |
| Database Design (ERD + DDL) | 20/08 | ⬜ |
| Tài liệu thiết kế v1.0 | 23/08 | ⬜ |
| Bảng phân công chi tiết | 27/08 | ⬜ |
| Gói hồ sơ thiết kế hoàn chỉnh | **31/08** | ⬜ |
| Backend v1.0 (test tích hợp) | 30/09 | ⬜ |
| Frontend + Mobile | 26/10 | ⬜ |
| Sản phẩm + báo cáo hoàn chỉnh | **31/10** | ⬜ |

---

*Kế hoạch này được lập dựa trên tài liệu hướng dẫn dự án, 3 giai đoạn thiết kế trong tháng 8 và toàn bộ thời gian phát triển tháng 9-10.*
