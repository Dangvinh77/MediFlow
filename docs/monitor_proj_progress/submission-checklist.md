# Manifest và checklist hồ sơ nộp

## A. Hồ sơ có sẵn để gửi ngay

- [x] Kế hoạch Markdown: [`docs/plan/README.md`](../plan/README.md)
- [x] Kế hoạch Word: [`docs/plan/KeHoachDuAnMediFlow.docx`](../plan/KeHoachDuAnMediFlow.docx)
- [x] 8 backend service spec + gateway/shared overview: [`backend-spec`](../eproject_general_plan/backend-spec/README.md)
- [x] 11 bộ ERD ở `.mmd/.svg/.png`: [`erd`](../eproject_general_plan/erd/README.md)
- [x] Kiến trúc, API, events, RBAC, testing, Web, Flutter blueprint: [`docs/ai`](../ai/README.md)
- [x] Báo cáo tiến độ ngày 02/09: [`README.md`](README.md)
- [x] SRS phục hồi bản nháp: [`giai-doan-1-srs.md`](giai-doan-1-srs.md)
- [x] GUI/wireframe bản khung: [`gui-standards-wireframes-draft.md`](gui-standards-wireframes-draft.md)
- [x] Audit ERD/DDL/events: [`erd-ddl-events-audit.md`](erd-ddl-events-audit.md)
- [x] Lộ trình bù tiến độ: [`recovery-roadmap.md`](recovery-roadmap.md)

## B. Hồ sơ phải hoàn thiện trước khi nộp bản final

- [ ] `docs/plan/giai-doan-1-srs.md` v1.0 đã ký duyệt.
- [ ] `docs/plan/giai-doan-2-design.md` gồm GUI, architecture, business flow, ERD/DDL/events.
- [ ] `docs/plan/phan-cong.md` có owner/reviewer/deadline/output.
- [ ] `docs/plan/giai-doan-3-final-review.md` có kết quả rà soát chéo.
- [ ] GUI Standards + wireframe/mockup Web/Mobile có file nguồn và export xem được.
- [ ] DDL Flyway đủ 8 service và báo cáo migrate sạch.
- [ ] Event catalog/diagram không còn mâu thuẫn.
- [ ] Traceability matrix Use Case → screen → endpoint → table/event → test.
- [ ] Maven test và frontend typecheck/lint/build đạt.
- [ ] Roster, tên dự án, số service, số bảng và phiên bản đồng nhất ở mọi tài liệu.

## C. Biên bản ký duyệt

| Vai trò | Người ký | Phạm vi duyệt | Ngày | Kết quả |
|---|---|---|---|---|
| Leader/Backend | Phạm Đăng Vinh | Scope, architecture, API, gói nộp |  |  |
| Frontend/Mobile | Trần Hoàng Anh | GUI Standards, Web/Mobile flow |  |  |
| Database/QA | Lê Quang Huy | ERD, DDL, NFR, testability |  |  |
| Database/Frontend/Review | Nguyễn Hoàng Phúc | Traceability và final review |  |  |

## D. Bằng chứng nộp

- Kênh nộp: ____________________
- Thời điểm nộp: ____________________
- Người nộp: ____________________
- Link/biên nhận: ____________________
- Phiên bản/commit: ____________________

