import 'package:flutter/material.dart';

import 'question_draft.dart';

/// Shows the input fields of one question: its text, its difficulty, its
/// answer options (with a round button to mark the correct one) and the
/// explanation.
class QuestionForm extends StatelessWidget {
  final QuestionDraft draft;
  final VoidCallback onChanged;
  final String? title;
  final VoidCallback? onDelete;

  const QuestionForm({
    super.key,
    required this.draft,
    required this.onChanged,
    this.title,
    this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (title != null)
              Row(
                children: [
                  Expanded(
                    child: Text(
                      title!,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                  if (onDelete != null)
                    IconButton(
                      icon: const Icon(Icons.delete_outline),
                      tooltip: 'Remove this question',
                      onPressed: onDelete,
                    ),
                ],
              ),

            TextField(
              controller: draft.textController,
              decoration: const InputDecoration(labelText: 'Question text'),
              maxLines: 2,
            ),
            const SizedBox(height: 12),

            DropdownButtonFormField<int>(
              initialValue: draft.difficulty,
              decoration: const InputDecoration(labelText: 'Difficulty'),
              items: const [1, 2, 3, 4, 5]
                  .map((value) =>
                      DropdownMenuItem(value: value, child: Text('$value')))
                  .toList(),
              onChanged: (value) {
                if (value == null) return;
                draft.difficulty = value;
                onChanged();
              },
            ),
            const SizedBox(height: 12),

            Text(
              'Answer options (select the correct one)',
              style: Theme.of(context).textTheme.labelLarge,
            ),

            for (int i = 0; i < draft.optionControllers.length; i++)
              Row(
                children: [
                  Radio<int>(
                    value: i,
                    groupValue: draft.correctOptionIndex,
                    onChanged: (value) {
                      if (value == null) return;
                      draft.correctOptionIndex = value;
                      onChanged();
                    },
                  ),
                  Expanded(
                    child: TextField(
                      controller: draft.optionControllers[i],
                      decoration:
                          InputDecoration(labelText: 'Option ${i + 1}'),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: draft.optionControllers.length <= 2
                        ? null
                        : () {
                            draft.removeOption(i);
                            onChanged();
                          },
                  ),
                ],
              ),

            if (draft.optionControllers.length < 5)
              TextButton.icon(
                icon: const Icon(Icons.add),
                label: const Text('Add option'),
                onPressed: () {
                  draft.addOption();
                  onChanged();
                },
              ),

            const SizedBox(height: 12),
            TextField(
              controller: draft.explanationController,
              decoration: const InputDecoration(labelText: 'Explanation'),
              maxLines: 2,
            ),
          ],
        ),
      ),
    );
  }
}
