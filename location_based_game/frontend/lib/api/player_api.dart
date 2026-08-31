import '../model/player.dart';
import 'api_client.dart';

/// All the requests about players.
class PlayerApi {

  /* Creates a new account. */
  static Future<Player> register(String nickname, String password) async {
    final json = await ApiClient.post('/api/players/createPlayer', {
      'nickname': nickname,
      'password': password,
    });
    return Player.fromJson(json as Map<String, dynamic>);
  }

  /* Checks the given nickname and password for the login. */
  static Future<Player> login(String nickname, String password) async {
    final json = await ApiClient.post('/api/players/login', {
      'nickname': nickname,
      'password': password,
    });
    return Player.fromJson(json as Map<String, dynamic>);
  }
}