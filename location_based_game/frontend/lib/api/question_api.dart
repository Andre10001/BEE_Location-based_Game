import '../model/question.dart';
import 'api_client.dart';
import 'topic_api.dart';

/// All the requests about single questions.
class QuestionApi {

  /* Saves a question and links it to the topic with id. */
  static Future<void> createAndAssign(Question question, int topicId) async {
    final json = await ApiClient.post(
      '/api/questions/createQuestion',
      question.toJson(),
    );
    final int questionId = json['id'] as int;
    await TopicApi.assignQuestions(topicId, [questionId]);
  }
}