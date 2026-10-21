import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/organization.dart';

final organizationRepositoryProvider = Provider<OrganizationRepository>(
  (ref) => throw StateError(
    'OrganizationRepository must be bound in app/dependencies.dart',
  ),
);
