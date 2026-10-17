import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/auth_repository.dart';

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) =>
      throw StateError('AuthRepository must be bound in app/dependencies.dart'),
);
