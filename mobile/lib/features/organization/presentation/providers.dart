import 'dart:convert';
import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/domain/workspace_access.dart';
import '../../../core/providers.dart';
import '../domain/organization.dart';
import '../domain/profile_account.dart';

final organizationRepositoryProvider = Provider<OrganizationRepository>(
  (ref) => throw StateError(
    'OrganizationRepository must be bound in app/dependencies.dart',
  ),
);

final profileAccountRepositoryProvider = Provider<ProfileAccountRepository>(
  (ref) => throw StateError(
    'ProfileAccountRepository must be bound in app/dependencies.dart',
  ),
);

final profileAccountIdempotencyKeyFactoryProvider = Provider<String Function()>(
  (ref) => _newProfileAccountIdempotencyKey,
);

final profileAccountIntentStoreProvider = Provider<ProfileAccountIntentStore>((
  ref,
) {
  final store = ProfileAccountIntentStore();
  ref.listen<WorkspaceAccess?>(workspaceAccessProvider, (previous, next) {
    if (previous?.sessionIdentity != next?.sessionIdentity) {
      store.clear();
    }
  });
  return store;
});

class ProfileAccountIntentScope {
  const ProfileAccountIntentScope({
    required this.sessionIdentity,
    required this.ownerType,
    required this.ownerId,
  });

  final Object sessionIdentity;
  final ProfileAccountOwnerType ownerType;
  final String ownerId;

  @override
  bool operator ==(Object other) =>
      other is ProfileAccountIntentScope &&
      other.sessionIdentity == sessionIdentity &&
      other.ownerType == ownerType &&
      other.ownerId == ownerId;

  @override
  int get hashCode => Object.hash(sessionIdentity, ownerType, ownerId);
}

class ProfileAccountPendingIntents {
  String? issueKey;
  String? resetKey;
  String? pendingEmailTarget;
  bool issueOutcomeUnknown = false;
  bool resetOutcomeUnknown = false;
  bool emailOutcomeUnknown = false;
}

class ProfileAccountIntentStore {
  final _intents = <ProfileAccountIntentScope, ProfileAccountPendingIntents>{};

  ProfileAccountPendingIntents forScope(ProfileAccountIntentScope scope) =>
      _intents.putIfAbsent(scope, ProfileAccountPendingIntents.new);

  void clear() => _intents.clear();
}

String _newProfileAccountIdempotencyKey() {
  final random = Random.secure();
  final bytes = List<int>.generate(32, (_) => random.nextInt(256));
  return base64Url.encode(bytes).replaceAll('=', '');
}
