/// Thrown when a lookup by key (topic name, outpost place, question index...)
/// doesn't match anything currently stored.
class EntityNotFoundException implements Exception {
  final String message;

  EntityNotFoundException(this.message);

  @override
  String toString() => 'EntityNotFoundException: $message';
}

/// Thrown when trying to add an entity whose key already exists.
class DuplicateEntityException implements Exception {
  final String message;

  DuplicateEntityException(this.message);

  @override
  String toString() => 'DuplicateEntityException: $message';
}

/// Thrown when an operation would break referential integrity between
/// topics and outposts.
class TopicInUseException implements Exception {
  final String message;

  TopicInUseException(this.message);

  @override
  String toString() => 'TopicInUseException: $message';
}