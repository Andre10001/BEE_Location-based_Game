/// The teams competing in the game.
enum Team {
  team1,
  team2;

  String get label => switch (this) {
        Team.team1 => 'Team 1',
        Team.team2 => 'Team 2',
      };
}
