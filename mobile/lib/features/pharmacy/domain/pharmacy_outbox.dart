class PharmacyOutboxReplay {
  const PharmacyOutboxReplay({required this.eventId, required this.replayed});
  final String eventId;
  final bool replayed;
}

/// There is no list endpoint: replay targets one event ID given by support.
abstract interface class PharmacyOutboxRepository {
  Future<PharmacyOutboxReplay> replayOutbox(String eventId);
}
