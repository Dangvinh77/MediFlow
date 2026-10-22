enum PharmacyIdentityKind { patient, doctor, department }

/// Display-only public identities. Commands continue to carry their original UUIDs.
abstract interface class PharmacyIdentityReader {
  Future<String> identityName(PharmacyIdentityKind kind, String id);
}
