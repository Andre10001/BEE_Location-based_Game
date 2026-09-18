import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/api_client.dart';
import 'create_topic_page.dart';
import '../../widget/pages_webview.dart';

/// The home page for administrators.
class AdminMenuPage extends StatelessWidget {
  final String playerName;
  final String playerId;

  const AdminMenuPage({super.key, required this.playerName, required this.playerId});
  
  String get gameUrl => '${ApiClient.beeUrl}/login.html#playerId=${Uri.encodeComponent(playerId)}';
  String get mapCreationUrl => '${ApiClient.beeUrl}/map-editor.html#playerId=${Uri.encodeComponent(playerId)}';
  String get assignTopicsUrl => '${ApiClient.beeUrl}/outpost-topics.html#playerId=${Uri.encodeComponent(playerId)}';

  /* Opens a page on top of this one. */
  void _openPage(BuildContext context, Widget page) {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));
  }

  /* Opens the lobby in the browser. */
  Future<void> gameLink() async {
    final Uri url = Uri.parse(gameUrl);

    if (!await launchUrl(url)) {
      throw Exception('Unable to open the link $url');
    }
  }

  /* Opens the map creation link in the browser. */
  Future<void> mapCreationLink() async {
    final Uri url = Uri.parse(mapCreationUrl);
    
    if (!await launchUrl(url)) {
      throw Exception('Unable to open the link $url');
    }
  }

  /* Opens the topic assignment link in the browser. */
  Future<void> assignTopicsLink() async {
    final Uri url = Uri.parse(assignTopicsUrl);
    
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
      appBar: AppBar(title: const Text('Admin menu')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Welcome, $playerName',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 24),
            FilledButton.icon(
              icon: const Icon(Icons.folder_outlined),
              label: const Text('Topics and questions'),
              onPressed: () => _openPage(context, const CreateTopicPage()),
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              icon: const Icon(Icons.map_outlined),
              label: const Text('Create map'),
              onPressed: () => {
                if (isMobileApp) {
                  _openPage(context, WebViewPage(
                    title: 'Create map',
                    url: mapCreationUrl,
                  )),
                } else {
                  mapCreationLink(),
                }
              },
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              icon: const Icon(Icons.topic_outlined),
              label: const Text('Assign topics to Outposts'),
              onPressed: () => {
                if (isMobileApp) {
                  _openPage(context, WebViewPage(
                    title: 'Assign topics',
                    url: assignTopicsUrl,
                  )),
                } else {
                  assignTopicsLink(),
                }
              },
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              icon: const Icon(Icons.play_arrow),
              label: const Text('Open lobby and start the match'),
              onPressed: () => {
                if (isMobileApp) {
                  _openPage(context, WebViewPage(
                    title: 'Lobby',
                    url: gameUrl,
                  )),
                } else {
                  gameLink(),
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}
