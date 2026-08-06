import 'dart:io';

import '../lib/server/lobby_session.dart';

/// Entry point for the standalone lobby server.
///
/// Run it with:
///   dart run bin/game_server.dart [port]
///
/// (default port: 8080).
Future<void> main(List<String> args) async {
  final port = args.isNotEmpty ? int.parse(args.first) : 8080;
  final session = LobbySession();

  final server = await HttpServer.bind(InternetAddress.anyIPv4, port);

  print('Lobby in ascolto su ws://${server.address.address}:$port');

  await for (final request in server) {
    if (WebSocketTransformer.isUpgradeRequest(request)) {
      final socket = await WebSocketTransformer.upgrade(request);
      session.handleConnection(socket);
    } else {
      request.response
        ..statusCode = HttpStatus.forbidden
        ..write('Questo server accetta solo connessioni WebSocket')
        ..close();
    }
  }
}