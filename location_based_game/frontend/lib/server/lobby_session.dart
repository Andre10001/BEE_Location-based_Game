import 'dart:io';
import 'dart:math';

import '../components/exception.dart';
import '../enum/team.dart';
import '../models/lobby_message.dart';
import '../models/player.dart';

/// Server-side authority for a single game lobby: keeps the canonical list
/// of connected [Player]s and keeps every connected socket in sync with it.
///
/// One running [LobbySession] represents one party playing together: every
/// device that opens a WebSocket connection to this server joins the same
/// lobby. That's exactly what makes two different browsers/devices "see"
/// each other as being in the same game.
class LobbySession {
  final Map<String, WebSocket> _sockets = {};
  final Map<String, Player> _players = {};
  final _random = Random();

  /// All players currently connected, in join order.
  List<Player> get players => List.unmodifiable(_players.values);

  /// Registers a freshly upgraded [socket] and starts listening to it. The
  /// player only appears in [players] once they send a `join` message.
  void handleConnection(WebSocket socket) {
    socket.listen(
      (raw) => _onMessage(socket, raw as String),
      onDone: () => _onDisconnect(socket),
      onError: (_) => _onDisconnect(socket),
      cancelOnError: true,
    );
  }

  void _onMessage(WebSocket socket, String raw) {
    final LobbyMessage message;
    try {
      message = LobbyMessage.fromJsonString(raw);
    } catch (_) {
      return; // Ignore anything that isn't a well-formed LobbyMessage.
    }
    try {
      switch (message.type) {
        case LobbyMessageType.join:
          _handleJoin(socket, message.data['name'] as String? ?? '');
          break;
        case LobbyMessageType.selectTeam:
          _handleSelectTeam(socket, message.data['team'] as String);
          break;
        default:
          break; // joined/playersUpdate/error only ever travel server->client.
      }
    } on DuplicateEntityException catch (e) {
      socket.add(LobbyMessage.error(e.message).toJsonString());
    } on EntityNotFoundException catch (e) {
      socket.add(LobbyMessage.error(e.message).toJsonString());
    }
  }

  void _handleJoin(WebSocket socket, String name) {
    final trimmed = name.trim();
    if (trimmed.isEmpty) {
      socket.add(
        LobbyMessage.error('Il nome non può essere vuoto').toJsonString(),
      );
      return;
    }
    final alreadyTaken = _players.values
        .any((p) => p.name.toLowerCase() == trimmed.toLowerCase());
    if (alreadyTaken) {
      throw DuplicateEntityException(
        'Un giocatore di nome "$trimmed" è già connesso',
      );
    }
    final player = Player(id: _newPlayerId(), name: trimmed);
    _sockets[player.id] = socket;
    _players[player.id] = player;
    socket.add(LobbyMessage.joined(player, players).toJsonString());
    _broadcastPlayers();
  }

  void _handleSelectTeam(WebSocket socket, String teamName) {
    final id = _idOf(socket);
    if (id == null) {
      throw EntityNotFoundException(
        'Devi entrare in gioco prima di scegliere un team',
      );
    }
    final team = Team.values.byName(teamName);
    _players[id] = _players[id]!.copyWithTeam(team);
    _broadcastPlayers();
  }

  void _onDisconnect(WebSocket socket) {
    final id = _idOf(socket);
    if (id == null) return;
    _sockets.remove(id);
    _players.remove(id);
    _broadcastPlayers();
  }

  String? _idOf(WebSocket socket) {
    for (final entry in _sockets.entries) {
      if (entry.value == socket) return entry.key;
    }
    return null;
  }

  String _newPlayerId() =>
      'p${DateTime.now().microsecondsSinceEpoch}_${_random.nextInt(99999)}';

  void _broadcastPlayers() {
    final message = LobbyMessage.playersUpdate(players).toJsonString();
    for (final socket in _sockets.values) {
      socket.add(message);
    }
  }
}