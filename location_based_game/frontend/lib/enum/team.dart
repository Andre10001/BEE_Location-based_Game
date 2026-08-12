/// The teams competing.
enum Team {
  team1,
  team2;

  /// Human readable label for this team.
  String get label => switch (this) {
    Team.team1 => 'Team 1',
    Team.team2 => 'Team 2',
  };

  /// The current team.
  Team get current => this == Team.team1 ? Team.team1 : Team.team2;

  /// The opposing team.
  Team get opponent => this == Team.team1 ? Team.team2 : Team.team1;
}