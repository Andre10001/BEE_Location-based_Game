import '../enum/role.dart';
import '../enum/team.dart';

/// A person playing the game.
class Player {
  final String? id;

  /// The nickname of the player.
  final String nickname;

  /// The team this player belongs to.
  final Team? team;

  /// If the player is the admin or a simple player.
  final Role role;

  Player({
    this.id,
    required this.nickname,
    this.team,
    this.role = Role.player,
    });

  /// Builds a Player from the JSON sent by the backend.
  factory Player.fromJson(Map<String, dynamic> json) {
    return Player(
      id: json['id'] as String?,
      nickname: json['nickname'] as String,
      team: json['team'] == null
          ? null
          : Team.values.byName(json['team'] as String),
      role: json['role'] == null
          ? Role.player
          : Role.values.byName(json['role'] as String),
    );
  }
}
