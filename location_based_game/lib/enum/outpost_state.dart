import 'team.dart';

/// Ownership status of an Outpost: unclaimed ([neutral]) or belonging to one
/// of the two [Team]s.
enum OutpostState {
  neutral,
  team1,
  team2;

  /// Human readable label for this ownership status.
  String get label => switch (this) {
    OutpostState.neutral => 'Neutrale',
    OutpostState.team1 => 'Team 1',
    OutpostState.team2 => 'Team 2',
  };

  /// The [Team] that currently owns this status, or `null` if [neutral].
  Team? get ownerTeam => switch (this) {
    OutpostState.neutral => null,
    OutpostState.team1 => Team.team1,
    OutpostState.team2 => Team.team2,
  };
}