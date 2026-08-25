/// A single multiple-choice question.
class Question {
  final int? id;

  /// How hard the question is.
  final int difficulty;

  /// The text of the question.
  final String text;

  /// The possible answers.
  final List<String> options;

  /// The correct answer's position inside [options].
  final int correctOptionIndex;

  /// Short text explaining why that answer is the correct one.
  final String explanation;

  Question({
    this.id,
    required this.difficulty,
    required this.text,
    required this.options,
    required this.correctOptionIndex,
    required this.explanation,
  });

  /// Builds a Question from the JSON sent by the backend.
  factory Question.fromJson(Map<String, dynamic> json) {
    return Question(
      id: json['id'] as int?,
      difficulty: json['difficulty'] as int,
      text: json['text'] as String,
      // The JSON list has type List<dynamic>, so we copy it into a
      // List<String> that Dart can use safely.
      options: List<String>.from(json['options'] as List),
      correctOptionIndex: json['correctOptionIndex'] as int,
      explanation: json['explanation'] as String,
    );
  }

  /// Builds the JSON body for the backend.
  Map<String, dynamic> toJson() {
    return {
      'difficulty': difficulty,
      'text': text,
      'options': options,
      'correctOptionIndex': correctOptionIndex,
      'explanation': explanation,
    };
  }
}
