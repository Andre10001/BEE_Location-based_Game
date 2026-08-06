import 'dart:convert';

import '../enum/team.dart';
import 'player.dart';

/// The kind of message exchanged between a device and the lobby server over
/// the WebSocket connection. The direction each type travels is documented
/// on the value itself.
enum LobbyMessageType {
  /// Client -> server: request to join under a chosen name.
  join,

  /// Client -> server: request to select (or change) a team.
  selectTeam,

  /// Server -> the joining client only: confirms the assigned player id and
  /// sends the current player list, so the client knows "who am I".
  joined,

  /// Server -> every connected client: the authoritative player list
  /// changed (someone joined, changed team, or disconnected).
  playersUpdate,

  /// Server -> client: the last request could not be fulfilled.
  error,
}

/// Envelope for every message sent over the lobby WebSocket connection, in
/// either direction. Keeping the wire format in one shared class means the
/// client and the server can never disagree on how to (de)serialize it.
class LobbyMessage {
  final LobbyMessageType type;
  final Map<String, dynamic> data;

  LobbyMessage(this.type, [this.data = const {}]);

  factory LobbyMessage.join(String name) =>
      LobbyMessage(LobbyMessageType.join, {'name': name});

  factory LobbyMessage.selectTeam(Team team) =>
      LobbyMessage(LobbyMessageType.selectTeam, {'team': team.name});

  factory LobbyMessage.joined(Player self, List<Player> players) =>
      LobbyMessage(LobbyMessageType.joined, {
        'player': self.toJson(),
        'players': players.map((p) => p.toJson()).toList(),
      });

  factory LobbyMessage.playersUpdate(List<Player> players) => LobbyMessage(
        LobbyMessageType.playersUpdate,
        {'players': players.map((p) => p.toJson()).toList()},
      );

  factory LobbyMessage.error(String message) =>
      LobbyMessage(LobbyMessageType.error, {'message': message});

  /// Decodes a raw string as received from the socket.
  factory LobbyMessage.fromJsonString(String source) {
    final decoded = jsonDecode(source) as Map<String, dynamic>;
    return LobbyMessage(
      LobbyMessageType.values.byName(decoded['type'] as String),
      (decoded['data'] as Map<String, dynamic>?) ?? const {},
    );
  }

  /// Encodes this message ready to send over the socket.
  String toJsonString() => jsonEncode({'type': type.name, 'data': data});
}