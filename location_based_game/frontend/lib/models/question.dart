/// Represents a single multiple-choice question belonging to a [Topic].
class Question {
  /// Difficulty level of the question, from 1 (easy) to 5 (hard).
  final int difficulty;

  /// The question prompt.
  final String text;

  /// The list of possible answers. Must contain between 2 and 5 options.
  final List<String> options;

  /// Index of the correct answer within [options].
  final int correctOptionIndex;

  /// Explanation of the correct answer.
  final String explanation;

  Question({
    required this.difficulty,
    required this.text,
    required this.options,
    required this.correctOptionIndex,
    required this.explanation,
  })  : assert(
          difficulty >= 1 && difficulty <= 5,
          'difficulty must be between 1 and 5 (was $difficulty)',
        ),
        assert(
          options.length >= 2 && options.length <= 5,
          'options must contain between 2 and 5 elements (was ${options.length})',
        ),
        assert(
          correctOptionIndex >= 0 && correctOptionIndex < options.length,
          'correctOptionIndex must be a valid index of options',
        );

  /// Convenience getter returning the text of the correct answer.
  String get correctAnswer => options[correctOptionIndex];

  /// Returns true if [optionIndex] corresponds to the correct answer.
  bool isCorrect(int optionIndex) => optionIndex == correctOptionIndex;

  factory Question.fromJson(Map<String, dynamic> json) {
    return Question(
      difficulty: json['difficulty'] as int,
      text: json['text'] as String,
      options: List<String>.from(json['options'] as List),
      correctOptionIndex: json['correctOptionIndex'] as int,
      explanation: json['explanation'] as String,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'difficulty': difficulty,
      'text': text,
      'options': options,
      'correctOptionIndex': correctOptionIndex,
      'explanation': explanation,
    };
  }

  @override
  String toString() => 'Question(difficulty: $difficulty, text: $text)';
}