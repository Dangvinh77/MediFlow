import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/self_profile.dart';

final selfProfileRepositoryProvider = Provider<SelfProfileRepository>(
  (ref) => throw StateError(
    'SelfProfileRepository must be bound in app/dependencies.dart',
  ),
);

final readOwnProfileProvider = Provider<ReadOwnProfile>(
  (ref) => ReadOwnProfile(ref.watch(selfProfileRepositoryProvider)),
);

final readOwnStaffDepartmentProvider = Provider<ReadOwnStaffDepartment>(
  (ref) => ReadOwnStaffDepartment(ref.watch(selfProfileRepositoryProvider)),
);

final updateOwnStaffContactProvider = Provider<UpdateOwnStaffContact>(
  (ref) => UpdateOwnStaffContact(ref.watch(selfProfileRepositoryProvider)),
);
