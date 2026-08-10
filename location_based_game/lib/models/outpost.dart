import 'dart:math';

import '../enum/team.dart';
import '../enum/outpost_state.dart';
import 'question.dart';
import 'topic.dart';

/// Represents an Outpost that the player can select on the game map.
///
/// An Outpost is linked to 1 to 3 [Topic]s (questions can be drawn from any
/// of them), has a required [difficulty] level (1-5) for the question that
/// will be asked, requires a minimum number of players ([requiredPlayers])
/// to answer it, and sits at a named [place] on the map.
class Outpost {
  /// The name of the place this outpost represents on the map.
  final String place;

  /// The topics this outpost draws questions from (between 1 and 3).
  final List<Topic> topics;

  /// Difficulty level required for this outpost's question, from 1 to 5.
  final int difficulty;

  /// Minimum number of players that must answer this outpost's question.
  final int requiredPlayers;

  OutpostState _state;

  Outpost({
    required this.place,
    required this.topics,
    required this.difficulty,
    required this.requiredPlayers,
    OutpostState state = OutpostState.neutral,
  })  : _state = state,
        assert(
          place.trim().isNotEmpty,
          'place must not be empty',
        ),
        assert(
          topics.isNotEmpty && topics.length <= 3,
          'an Outpost must be linked to 1 to 3 topics (was ${topics.length})',
        ),
        assert(
          difficulty >= 1 && difficulty <= 5,
          'difficulty must be between 1 and 5 (was $difficulty)',
        ),
        assert(
          requiredPlayers >= 1,
          'requiredPlayers must be at least 1 (was $requiredPlayers)',
        );

  /// Who currently controls this outpost.
  OutpostState get state => _state;

  /// Whether this outpost is currently unclaimed.
  bool get isNeutral => _state == OutpostState.neutral;

  /// Whether [team] is currently allowed to *start* a conquest of this
  /// outpost. A team can never conquer an outpost it already owns; a
  /// neutral outpost can always be conquered by either team.
  bool canBeConqueredBy(Team team) => isNeutral || _state.ownerTeam != team;

  /// Applies the outcome of a correctly answered conquest question started
  /// by [team]. Call this only after the associated question has been
  /// answered correctly.
  ///
  /// Rules:
  /// - If the outpost is [OutpostOwner.neutral], it becomes [team]'s.
  /// - If the outpost belongs to the *opposing* team, this first successful
  ///   conquest only strips that ownership away, turning it back to
  ///   [OutpostOwner.neutral]. [team] must then start and win a *second*
  ///   conquest (once it's neutral) to actually make it theirs.
  /// - If the outpost already belongs to [team], nothing changes.
  ///
  /// Returns the resulting [OutpostOwner].
  OutpostState conquer(Team team) {
    if (!canBeConqueredBy(team)) return _state;
    _state =
      switch (_state) {
          OutpostState.neutral => team == Team.team1 ? OutpostState.team1 : OutpostState.team2,
          OutpostState.team1 when team == Team.team2 => OutpostState.neutral,
          OutpostState.team2 when team == Team.team1 => OutpostState.neutral,
          _ => _state,
      };
    return _state;
  }

  /// All questions across [topics] that match [difficulty].
  List<Question> get matchingQuestions => topics
      .expand((topic) => topic.questionsByDifficulty(difficulty))
      .toList();

  /// Picks a random question matching [difficulty] from one of the [topics].
  /// Returns null if no question matches.
  Question? pickRandomQuestion([Random? random]) {
    final candidates = matchingQuestions;
    if (candidates.isEmpty) return null;
    final rng = random ?? Random();
    return candidates[rng.nextInt(candidates.length)];
  }

  /// Builds an [Outpost] from JSON, resolving topic names against a list of
  /// [availableTopics] (typically loaded once from questions.json).
  factory Outpost.fromJson(
    Map<String, dynamic> json, {
    required List<Topic> availableTopics,
  }) {
    final topicNames = List<String>.from(json['topics'] as List);
    final resolvedTopics = availableTopics
        .where((t) => topicNames.contains(t.name))
        .toList();
    return Outpost(
      topics: resolvedTopics,
      difficulty: json['difficulty'] as int,
      requiredPlayers: json['requiredPlayers'] as int,
      place: json['place'] as String,
      state:
          OutpostState.values.byName(json['state'] as String? ?? 'neutral'),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'topics': topics.map((t) => t.name).toList(),
      'difficulty': difficulty,
      'requiredPlayers': requiredPlayers,
      'place': place,
      'state': _state.name,
    };
  }

  @override
  String toString() =>
      'Outpost(place: $place, topics: ${topics.map((t) => t.name).join(', ')}, '
      'difficulty: $difficulty, requiredPlayers: $requiredPlayers, state: ${_state.name})';
}