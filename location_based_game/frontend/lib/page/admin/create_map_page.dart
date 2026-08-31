import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../api/api_client.dart';

/// Lets an administrator create a map for the game.
class CreateMapPage extends StatefulWidget {
  const CreateMapPage ({super.key});

  @override
  _CreateMapPageState createState() => _CreateMapPageState();
}

class _CreateMapPageState extends State<CreateMapPage> {
  late final WebViewController controller;

  @override
  void initState() {
    super.initState();
    controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..loadRequest(Uri.parse('${ApiClient.beeUrl}/map-editor.html'));
  }

  @override
  Widget build(BuildContext context) {
    return WebViewWidget(controller: controller); 
  }
}