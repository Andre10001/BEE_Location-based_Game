import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/api_client.dart';
import 'assign_topics_page.dart';
import 'create_map_page.dart';
import 'create_question_page.dart';
import 'create_topic_page.dart';

/// The home page for administrators.
class AdminMenuPage extends StatelessWidget {
  final String playerName;

  const AdminMenuPage({super.key, required this.playerName});

  /* Opens a page on top of this one. */
  void _openPage(BuildContext context, Widget page) {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));
  }

  /* Opens the map creation link in the browser. */
  Future<void> mapCreationLink() async {
    final Uri url = Uri.parse('${ApiClient.beeUrl}/map-editor.html');
    
    if (!await launchUrl(url)) {
      throw Exception('Unable to open the link $url');
    }
  }

  /* Opens the topic assignment link in the browser. */
  Future<void> assignTopicsLink() async {
    final Uri url = Uri.parse('${ApiClient.beeUrl}/outpost-topics.html');
    
    if (!await launchUrl(url)) {
      throw Exception('Unable to open the link $url');
    }
  }

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
              label: const Text('Create topic'),
              onPressed: () => _openPage(context, const CreateTopicPage()),
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              icon: const Icon(Icons.help_outline),
              label: const Text('Create question'),
              onPressed: () => _openPage(context, const CreateQuestionPage()),
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              icon: const Icon(Icons.map_outlined),
              label: const Text('Create map'),
              onPressed: () => {
                if (defaultTargetPlatform == TargetPlatform.iOS || defaultTargetPlatform == TargetPlatform.android) {
                  _openPage(context, const CreateMapPage()),
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
                if (defaultTargetPlatform == TargetPlatform.iOS || defaultTargetPlatform == TargetPlatform.android) {
                  _openPage(context, const AssignTopicsPage()),
                } else {
                  assignTopicsLink(),
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}
