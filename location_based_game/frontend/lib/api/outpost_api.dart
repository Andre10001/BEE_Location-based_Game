import '../model/outpost.dart';
import 'api_client.dart';

/// All the requests about outposts.
class OutpostApi {
  
  /* Loads every outpost placed on the map. */
  static Future<List<Outpost>> getAllOutposts() async {
    final json = await ApiClient.get('/api/outposts/getAllOutposts');
    final List<dynamic> list = json as List<dynamic>;
    return list
        .map((item) => Outpost.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /* Saves a new outpost. */
  static Future<Outpost> createOutpost(Outpost outpost) async {
    final json =
        await ApiClient.post('/api/outposts/createOutpost', outpost.toJson());
    return Outpost.fromJson(json as Map<String, dynamic>);
  }
}