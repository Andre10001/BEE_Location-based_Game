import 'package:flutter/material.dart';

import '../../api/topic_api.dart';
import '../../model/topic.dart';
import '../../widget/question_draft.dart';
import '../../widget/question_form.dart';

/// Lets an administrator create a topic.
class CreateTopicPage extends StatefulWidget {
  const CreateTopicPage({super.key});

  @override
  State<CreateTopicPage> createState() => _CreateTopicPageState();
}

class _CreateTopicPageState extends State<CreateTopicPage> {
  final TextEditingController _nameController = TextEditingController();
  final List<QuestionDraft> _questionDrafts = [];

  bool _isLoading = false;
  String? _errorMessage;

  @override
  void dispose() {
    _nameController.dispose();
    for (final draft in _questionDrafts) {
      draft.dispose();
    }
    super.dispose();
  }

  /* Adds one more empty question block for questions. */
  void _addQuestion() {
    setState(() => _questionDrafts.add(QuestionDraft()));
  }

  /* Removes the question block at specific index. */
  void _removeQuestion(int index) {
    setState(() {
      _questionDrafts[index].dispose();
      _questionDrafts.removeAt(index);
    });
  }

  /* Checks every field, then saves the topic with all its questions to the backend. */
  Future<void> _save() async {
    if (_nameController.text.trim().isEmpty) {
      setState(() => _errorMessage = 'The topic name must not be empty');
      return;
    }
    if (_questionDrafts.isEmpty) {
      setState(() => _errorMessage = 'Add at least one question');
      return;
    }
    for (int i = 0; i < _questionDrafts.length; i++) {
      final String? problem = _questionDrafts[i].validate();
      if (problem != null) {
        setState(() => _errorMessage = 'Question ${i + 1}: $problem');
        return;
      }
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });
    try {
      final topic = Topic(
        name: _nameController.text.trim(),
        questions:
            _questionDrafts.map((draft) => draft.toQuestion()).toList(),
      );
      await TopicApi.createTopic(topic);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Topic created')),
      );
      Navigator.of(context).pop();
    } catch (error) {
      setState(() => _errorMessage = error.toString());
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Create topic')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: _nameController,
            decoration: const InputDecoration(labelText: 'Topic name'),
          ),
          const SizedBox(height: 20),
          Row(
            children: [
              Expanded(
                child: Text(
                  'Questions (${_questionDrafts.length})',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              IconButton.filled(
                icon: const Icon(Icons.add),
                tooltip: 'Add a question',
                onPressed: _addQuestion,
              ),
            ],
          ),
          if (_questionDrafts.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Text('No question yet. Press + to add one.'),
            ),
          for (int i = 0; i < _questionDrafts.length; i++)
            QuestionForm(
              draft: _questionDrafts[i],
              title: 'Question ${i + 1}',
              onChanged: () => setState(() {}),
              onDelete: () => _removeQuestion(i),
            ),
          const SizedBox(height: 20),
          if (_errorMessage != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(
                _errorMessage!,
                style: const TextStyle(color: Colors.red),
              ),
            ),
          FilledButton(
            onPressed: _isLoading ? null : _save,
            child: _isLoading
                ? const CircularProgressIndicator()
                : const Text('Save topic'),
          ),
        ],
      ),
    );
  }
}
