# PostgreSQL trong MVP

Đọc [data/reliability](04-data-reliability.md) và [runbook](06-acceptance-runbook.md).
Giữ databases/volumes/migrations hiện có; Docker engine phải chạy cho Testcontainers.
Không drop DB, xóa volume hoặc Flyway repair để sửa startup. Core/Support datasource tách module.
