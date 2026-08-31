import 'package:flutter/material.dart';

import '../model/topic.dart';

/// A dropdown listing the topics saved in the database, with a box on top
/// that filters the list as the user types.
class TopicPicker extends StatelessWidget {
  final List<Topic> topics;
  final Topic? selectedTopic;
  final ValueChanged<Topic?> onSelected;
  final String label;

  const TopicPicker({
    super.key,
    required this.topics,
    required this.selectedTopic,
    required this.onSelected,
    this.label = 'Topic',
  });

  @override
  Widget build(BuildContext context) {
    return DropdownMenu<Topic>(
      label: Text(label),
      width: double.infinity,
      initialSelection: selectedTopic,
      enableFilter: true,
      requestFocusOnTap: true,
      menuHeight: 300,
      dropdownMenuEntries: topics
          .map((topic) =>
              DropdownMenuEntry<Topic>(value: topic, label: topic.name))
          .toList(),
      onSelected: onSelected,
    );
  }
}
