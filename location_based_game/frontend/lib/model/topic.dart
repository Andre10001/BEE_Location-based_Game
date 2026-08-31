import 'question.dart';

/// A subject holding a group of questions.
class Topic {
  final int? id;
  final String name;
  final List<Question> questions;

  Topic({this.id, required this.name, required this.questions});

  /* Builds a Topic from the JSON sent by the backend. */
  factory Topic.fromJson(Map<String, dynamic> json) {
    final List<dynamic> rawQuestions =
        (json['questions'] as List<dynamic>?) ?? [];
    return Topic(
      id: json['id'] as int?,
      name: json['name'] as String,
      questions: rawQuestions
          .map((item) => Question.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }

  
  /* Builds the JSON body for the backend. */
  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'questions': questions.map((question) => question.toJson()).toList(),
    };
  }
}
