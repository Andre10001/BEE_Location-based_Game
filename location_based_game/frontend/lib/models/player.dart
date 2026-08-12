import '../enum/team.dart';

/// Represents a single participant connected to the game from their own
/// device.
///
/// A [Player] is created by the lobby server the moment a device joins (see
/// `LobbySession`) and always keeps the same [id] for the rest of the
/// session. [team] stays `null` until the player picks a side from the main
/// menu.
class Player {
  /// Unique identifier assigned by the server when the player connects.
  /// Never chosen by the client, so two players are always distinguishable
  /// even if they pick the same [name].
  final String id;

  /// Display name the player typed in when joining.
  final String name;

  /// Password of the player typed in when joining.
  final String password;

  /// The team this player has chosen, or `null` if they haven't chosen yet.
  final Team? team;

  Player({
    required this.id,
    required this.name,
    required this.password,
    this.team,
  }) : assert(name.trim().isNotEmpty, 'name must not be empty');

  /// Whether this player has picked a team yet.
  bool get hasChosenTeam => team != null;

  /// Returns a copy of this player with [team] replaced. Pass `null` to
  /// clear the current choice.
  Player copyWithTeam(Team? team) => Player(id: id, name: name, password: password, team: team);

  factory Player.fromJson(Map<String, dynamic> json) {
    return Player(
      id: json['id'] as String,
      name: json['name'] as String,
      password: json['password'] as String,
      team: json['team'] == null
          ? null
          : Team.values.byName(json['team'] as String),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'password': password,
        'team': team?.name,
      };

  @override
  String toString() =>
      'Player(id: $id, name: $name, team: ${team?.label ?? 'nessuno'})';
}