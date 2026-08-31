import 'package:flutter/material.dart';

import '../model/question.dart';

/// Holds what the user is typing for one question.
class QuestionDraft {
  final TextEditingController textController = TextEditingController();
  final TextEditingController explanationController = TextEditingController();
  final List<TextEditingController> optionControllers = [
    TextEditingController(),
    TextEditingController(),
  ];

  int difficulty = 1;
  int correctOptionIndex = 0;

  /* Adds one more empty option. */
  void addOption() {
    if (optionControllers.length < 5) {
      optionControllers.add(TextEditingController());
    }
  }

  /* Removes the option at [index], as long as 2 of them remain. */
  void removeOption(int index) {
    if (optionControllers.length <= 2) return;
    if (correctOptionIndex == index) return;
    optionControllers[index].dispose();
    optionControllers.removeAt(index);
  }

  /* Returns a message describing the first problem found, or null when
  * everything is filled in properly.
  */
  String? validate() {
    if (textController.text.trim().isEmpty) {
      return 'the question text must not be empty';
    }
    for (final controller in optionControllers) {
      if (controller.text.trim().isEmpty) {
        return 'all answer options must be filled in';
      }
    }
    if (explanationController.text.trim().isEmpty) {
      return 'the explanation must not be empty';
    }
    return null;
  }

  /* Turns what the user typed into a question. */
  Question toQuestion() {
    return Question(
      difficulty: difficulty,
      text: textController.text.trim(),
      options: optionControllers
          .map((controller) => controller.text.trim())
          .toList(),
      correctOptionIndex: correctOptionIndex,
      explanation: explanationController.text.trim(),
    );
  }

  void dispose() {
    textController.dispose();
    explanationController.dispose();
    for (final controller in optionControllers) {
      controller.dispose();
    }
  }
}
