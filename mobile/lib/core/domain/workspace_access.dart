/// Session boundary shared by workspaces without importing the Auth feature.
class WorkspaceAccess {
  const WorkspaceAccess({
    required this.sessionIdentity,
    required this.role,
    this.patientId,
    this.staffId,
  });

  /// Opaque identity of the current authenticated session; never a patient ID.
  final Object sessionIdentity;
  final String role;
  final String? patientId, staffId;
}
