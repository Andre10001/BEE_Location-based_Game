import '../enum/outpost_state.dart';

/// A place on the map that players can try to conquer.
class Outpost {
  final int? id;
  final String place;
  final List<int> topicIds;
  final int difficulty;
  final int requiredPlayers;
  final OutpostState state;

  Outpost({
    this.id,
    required this.place,
    required this.topicIds,
    required this.difficulty,
    required this.requiredPlayers,
    this.state = OutpostState.neutral,
  });

  /* Builds an Outpost from the JSON sent by the backend. */
  factory Outpost.fromJson(Map<String, dynamic> json) {
    return Outpost(
      id: json['id'] as int?,
      place: json['place'] as String,
      topicIds: List<int>.from(json['topicIds'] as List),
      difficulty: json['difficulty'] as int,
      requiredPlayers: json['requiredPlayers'] as int,
      state: json['state'] == null
          ? OutpostState.neutral
          : OutpostState.values.byName(json['state'] as String),
    );
  }

  /* Builds the JSON body for the backend. */
  Map<String, dynamic> toJson() {
    return {
      'place': place,
      'topicIds': topicIds,
      'difficulty': difficulty,
      'requiredPlayers': requiredPlayers,
    };
  }
}
