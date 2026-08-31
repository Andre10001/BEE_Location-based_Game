import '../model/topic.dart';
import 'api_client.dart';

/// All the requests about topics.
class TopicApi {

  /* Loads every topic saved in the database. */
  static Future<List<Topic>> getAllTopics() async {
    final json = await ApiClient.get('/api/topics/getAllTopics');
    final List<dynamic> list = json as List<dynamic>;
    return list
        .map((item) => Topic.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /* Saves a topic together with all its questions. */
  static Future<Topic> createTopic(Topic topic) async {
    final json =
        await ApiClient.post('/api/topics/createTopic', topic.toJson());
    return Topic.fromJson(json as Map<String, dynamic>);
  }

  /* Links questions that already exist in the database to a topic. */
  static Future<void> assignQuestions(int topicId, List<int> questionIds) async {
    await ApiClient.post('/api/topics/assignQuestions/$topicId', questionIds);
  }
}