import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/api_client.dart';

/// The home page for regular players.
class PlayerHomePage extends StatelessWidget {
  final String nickname;

  const PlayerHomePage({super.key, required this.nickname});

  /* Opens the game link in the browser. */
  Future<void> gameLink() async {
    final Uri url = Uri.parse('${ApiClient.beeUrl}/game.html');
    
    if (!await launchUrl(url)) {
      throw Exception('Unable to open the link $url');
    }
  }

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
                onPressed: () => { gameLink(),
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}