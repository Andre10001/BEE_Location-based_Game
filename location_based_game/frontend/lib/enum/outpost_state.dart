import 'team.dart';

/// Ownership status of an outpost.
enum OutpostState {
  neutral,
  team1,
  team2;

  String get label => switch (this) {
        OutpostState.neutral => 'Neutral',
        OutpostState.team1 => 'Team 1',
        OutpostState.team2 => 'Team 2',
      };

  /* The team that owns this outpost, or null when it is neutral. */
  Team? get ownerTeam => switch (this) {
        OutpostState.neutral => null,
        OutpostState.team1 => Team.team1,
        OutpostState.team2 => Team.team2,
      };
}
