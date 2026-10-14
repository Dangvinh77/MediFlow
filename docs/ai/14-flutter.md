# Blueprint Flutter tối giản

Đọc [coding](README.md), [contracts](../plans/03-contracts.md) và mobile/AGENTS.
Không Flutter expansion để đóng backend MVP; giữ client hiện tại và regression khi cần.

    features/<context>/presentation/  # providers/pages/widgets
    features/<context>/domain/        # pure Dart entities/repositories/use cases
    features/<context>/data/          # DTO/datasources/repository implementation
    core/network/api_client.dart

Dependencies presentation → domain ← data; domain không Flutter/network/framework imports.
Gateway-only qua shared API client; không service ports/direct HTTP ở widgets.
DTO mirror exact wire, không mặc định mọi service Vietnamese fields. UUIDs không guessed.
JWT SecureStorage, role hiding chỉ UX; backend enforces authority. Riverpod/router/Dio và
versions giữ pubspec/lockfile, không migration framework. Test use cases/providers/serialization.
