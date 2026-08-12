import 'question.dart';

/// Represents a subject containing a set of related [Question]s.
class Topic {
  /// The name of the topic.
  final String name;

  /// The list of questions belonging to this topic.
  final List<Question> questions;

  Topic({
    required this.name,
    required this.questions,
  });

  /// Returns all questions in this topic with the given [difficulty] (1-5).
  List<Question> questionsByDifficulty(int difficulty) {
    return questions.where((q) => q.difficulty == difficulty).toList();
  }

  factory Topic.fromJson(Map<String, dynamic> json) {
    return Topic(
      name: json['name'] as String,
      questions: (json['questions'] as List)
          .map((q) => Question.fromJson(q as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'questions': questions.map((q) => q.toJson()).toList(),
    };
  }

  @override
  String toString() => 'Topic(name: $name, questions: ${questions.length})';
}