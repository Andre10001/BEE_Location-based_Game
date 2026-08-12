import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../enum/team.dart';
import '../models/lobby_message.dart';
import '../models/player.dart';

/// Connection state of the lobby socket, so the UI can show a spinner, an
/// error banner, the join form, etc.
enum LobbyConnectionStatus { disconnected, connecting, connected, error }

/// Client-side counterpart of `LobbySession`: connects this device to the
/// lobby server, sends this player's name/team choices, and keeps
/// [players] in sync with every other connected device in real time.
///
/// Kept separate from any widget so [MainMenuScreen] only depends on this
/// class's public API, never on the socket or the wire protocol directly —
/// mirrors how [GameController] is kept separate from [GameScreen].
class LobbyController extends ChangeNotifier {
  WebSocketChannel? _channel;
  StreamSubscription? _subscription;

  List<Player> _players = [];
  Player? _me;
  LobbyConnectionStatus _status = LobbyConnectionStatus.disconnected;
  String? _errorMessage;

  /// Every player currently connected, across both teams.
  List<Player> get players => List.unmodifiable(_players);

  /// This device's own player, once [connectAndJoin] has succeeded.
  Player? get me => _me;

  /// Current state of the connection to the lobby server.
  LobbyConnectionStatus get status => _status;

  /// Last error message received from the server, if any.
  String? get errorMessage => _errorMessage;

  /// Players who have already picked [Team.team1].
  List<Player> get team1Players =>
      _players.where((p) => p.team == Team.team1).toList();

  /// Players who have already picked [Team.team2].
  List<Player> get team2Players =>
      _players.where((p) => p.team == Team.team2).toList();

  /// Players connected but who haven't picked a team yet.
  List<Player> get undecidedPlayers =>
      _players.where((p) => p.team == null).toList();

  /// Opens the WebSocket connection to [serverUrl] (e.g.
  /// `ws://192.168.1.10:8080`) and asks to join under [name]. Call this
  /// once, typically from the main menu, before showing team choices.
  Future<void> connectAndJoin(String serverUrl, String name) async {
    await disconnect();
    _status = LobbyConnectionStatus.connecting;
    _errorMessage = null;
    notifyListeners();
    try {
      final channel = WebSocketChannel.connect(Uri.parse(serverUrl));
      await channel.ready;
      _channel = channel;
      _subscription = channel.stream.listen(
        (raw) => _onMessage(raw as String),
        onError: (_) => _setError('Connessione al server persa'),
        onDone: () => _setError('Connessione al server chiusa'),
      );
      _send(LobbyMessage.join(name));
    } catch (_) {
      _setError('Impossibile connettersi al server');
    }
  }

  /// Sends this device's team choice to the server. [players] updates for
  /// everyone once the server confirms it via a broadcast.
  void selectTeam(Team team) => _send(LobbyMessage.selectTeam(team));

  void _onMessage(String raw) {
    final message = LobbyMessage.fromJsonString(raw);
    switch (message.type) {
      case LobbyMessageType.joined:
        _me = Player.fromJson(message.data['player'] as Map<String, dynamic>);
        _players = _parsePlayers(message.data['players']);
        _status = LobbyConnectionStatus.connected;
        notifyListeners();
        break;
      case LobbyMessageType.playersUpdate:
        _players = _parsePlayers(message.data['players']);
        final myId = _me?.id;
        if (myId != null) {
          _me = _players.firstWhere((p) => p.id == myId, orElse: () => _me!);
        }
        notifyListeners();
        break;
      case LobbyMessageType.error:
        _errorMessage = message.data['message'] as String;
        notifyListeners();
        break;
      default:
        break;
    }
  }

  List<Player> _parsePlayers(dynamic raw) => (raw as List)
      .map((p) => Player.fromJson(p as Map<String, dynamic>))
      .toList();

  void _send(LobbyMessage message) =>
      _channel?.sink.add(message.toJsonString());

  void _setError(String message) {
    _status = LobbyConnectionStatus.error;
    _errorMessage = message;
    notifyListeners();
  }

  /// Closes the connection. Safe to call even if never connected.
  Future<void> disconnect() async {
    await _subscription?.cancel();
    await _channel?.sink.close();
    _channel = null;
    _subscription = null;
    _status = LobbyConnectionStatus.disconnected;
  }

  @override
  void dispose() {
    disconnect();
    super.dispose();
  }
}