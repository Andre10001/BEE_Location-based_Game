import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../api/api_client.dart';

/// Lets an administrator assign topics to outposts.
class AssignTopicsPage extends StatefulWidget {
  const AssignTopicsPage ({super.key});

  @override
  _AssignTopicsPageState createState() => _AssignTopicsPageState();
}

class _AssignTopicsPageState extends State<AssignTopicsPage> {
  late final WebViewController controller;

  @override
  void initState() {
    super.initState();
    controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..loadRequest(Uri.parse('${ApiClient.beeUrl}/outpost-topics.html'));
  }

  @override
  Widget build(BuildContext context) {
    return WebViewWidget(controller: controller); 
  }
}