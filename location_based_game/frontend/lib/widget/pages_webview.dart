import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';

/// Shows a web page of the game inside a WebView.
class WebViewPage extends StatefulWidget {
  final String title;
  final String url;

  const WebViewPage({super.key, required this.title, required this.url});

  @override
  State<WebViewPage> createState() => _WebViewPageState();
}

class _WebViewPageState extends State<WebViewPage> {
  late final WebViewController controller;

  @override
  void initState() {
    super.initState();
    controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted);
    _preparePage();
  }

  /* Asks for the GPS permission, lets the web page use the GPS and then loads the page. */
  Future<void> _preparePage() async {
    await _askLocationPermission();

    /* On Android the WebView has to be told that the page can use the GPS. */
    if (controller.platform is AndroidWebViewController) {
      final androidController = controller.platform as AndroidWebViewController;
      await androidController.setGeolocationPermissionsPromptCallbacks(
        onShowPrompt: (request) async {
          return const GeolocationPermissionsResponse(allow: true, retain: true);
        },
      );
    }

    await controller.loadRequest(Uri.parse(widget.url));
  }

  /* Asks the user for the permission to read the position of the phone. */
  Future<void> _askLocationPermission() async {
    try {
      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        _showMessage('Without the GPS permission your position will not be shown on the map.');
      }
    } catch (error) {
      _showMessage('Could not ask for the GPS permission: $error');
    }
  }

  /* Shows a short message at the bottom of the screen. */
  void _showMessage(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  /* Goes back inside the web pages, or closes this page when there is nothing to go back to. */
  Future<void> _goBack() async {
    if (await controller.canGoBack()) {
      await controller.goBack();
    } else if (mounted) {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        _goBack();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(widget.title),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: () => controller.reload(),
            ),
          ],
        ),
        body: WebViewWidget(controller: controller),
      ),
    );
  }
}