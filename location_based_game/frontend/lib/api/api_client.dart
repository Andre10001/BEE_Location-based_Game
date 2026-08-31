import 'dart:convert';

import 'package:http/http.dart' as http;

/// Knows where the backend is and how to talk to it.
class ApiClient {

  /* Address of the Spring Boot backend. (Different for Android: http://192.168.1.10:8080) */
  static const String baseUrl = 'http://localhost:8080';

  /* Address of BEE. */
  static const String beeUrl = 'http://localhost:8082';

  /* Asks the backend for data and returns the decoded JSON. */
  static Future<dynamic> get(String path) async {
    final response = await http.get(Uri.parse('$baseUrl$path'));
    return _readResponse(response);
  }

  /* Sends the body object to the backend as JSON and returns the decoded answer. */
  static Future<dynamic> post(String path, Object body) async {
    final response = await http.post(
      Uri.parse('$baseUrl$path'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(body),
    );
    return _readResponse(response);
  }

  /* Turns the raw HTTP answer into usable data. */
  static dynamic _readResponse(http.Response response) {
    if (response.statusCode == 200 || response.statusCode == 201) {
      if (response.body.isEmpty) return null;
      return jsonDecode(response.body);
    }
    String message = 'Request failed (${response.statusCode})';
    if (response.body.isNotEmpty) {
      final decoded = jsonDecode(response.body);
      if (decoded is Map && decoded['error'] != null) {
        message = decoded['error'].toString();
      }
    }
    throw Exception(message);
  }
}
