import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/api_client.dart';
import '../../widget/pages_webview.dart';

/// The home page for regular players.
class PlayerHomePage extends StatelessWidget {
  final String nickname;
  final String playerId;

  const PlayerHomePage({super.key, required this.nickname, required this.playerId});

  String get gameUrl => '${ApiClient.beeUrl}/login.html#playerId=${Uri.encodeComponent(playerId)}';

  /* Opens the game link in the browser. */
  Future<void> gameLink() async {
    final Uri url = Uri.parse(gameUrl);
    
    if (!await launchUrl(url)) {
      throw Exception('Unable to open the link $url');
    }
  }

  bool get isMobileApp =>
      !kIsWeb &&
      (defaultTargetPlatform == TargetPlatform.iOS ||
          defaultTargetPlatform == TargetPlatform.android);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Game')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'Welcome, $nickname',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 12),
              const Text(
                'The game map will be shown here.',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 12),
              FilledButton.icon(
                icon: const Icon(Icons.map_outlined),
                label: const Text('Play'),
                onPressed: () {
                  if (isMobileApp) {
                    Navigator.of(context).push(MaterialPageRoute(
                      builder: (_) => WebViewPage(title: 'Game', url: gameUrl),
                    ));
                  } else {
                    gameLink();
                  }
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}