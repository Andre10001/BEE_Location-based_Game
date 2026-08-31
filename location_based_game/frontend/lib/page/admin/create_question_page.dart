import 'package:flutter/material.dart';

import '../../api/question_api.dart';
import '../../api/topic_api.dart';
import '../../model/topic.dart';
import '../../widget/question_draft.dart';
import '../../widget/question_form.dart';
import '../../widget/topic_picker.dart';

/// Lets an administrator write one question and assign it to a topic.
class CreateQuestionPage extends StatefulWidget {
  const CreateQuestionPage({super.key});

  @override
  State<CreateQuestionPage> createState() => _CreateQuestionPageState();
}

class _CreateQuestionPageState extends State<CreateQuestionPage> {
  final QuestionDraft _draft = QuestionDraft();

  List<Topic> _topics = [];
  Topic? _selectedTopic;
  bool _isLoadingTopics = true;
  bool _isSaving = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadTopics();
  }

  @override
  void dispose() {
    _draft.dispose();
    super.dispose();
  }

  /* Loads every topic from backend. */
  Future<void> _loadTopics() async {
    setState(() {
      _isLoadingTopics = true;
      _errorMessage = null;
    });
    try {
      final topics = await TopicApi.getAllTopics();
      if (!mounted) return;
      setState(() => _topics = topics);
    } catch (error) {
      if (mounted) setState(() => _errorMessage = error.toString());
    } finally {
      if (mounted) setState(() => _isLoadingTopics = false);
    }
  }

  /* Checks the fields, then saves the question and links it to the topic the user chose. */
  Future<void> _save() async {
    final String? problem = _draft.validate();
    if (problem != null) {
      setState(() => _errorMessage = 'Question: $problem');
      return;
    }
    if (_selectedTopic == null) {
      setState(() => _errorMessage = 'Choose a topic for this question');
      return;
    }

    setState(() {
      _isSaving = true;
      _errorMessage = null;
    });
    try {
      await QuestionApi.createAndAssign(
        _draft.toQuestion(),
        _selectedTopic!.id!,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Question created')),
      );
      Navigator.of(context).pop();
    } catch (error) {
      setState(() => _errorMessage = error.toString());
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Create question')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          QuestionForm(
            draft: _draft,
            onChanged: () => setState(() {}),
          ),
          const SizedBox(height: 20),
          Text(
            'Assign to a topic',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          if (_isLoadingTopics)
            const Center(child: CircularProgressIndicator())
          else
            TopicPicker(
              topics: _topics,
              selectedTopic: _selectedTopic,
              onSelected: (topic) => setState(() => _selectedTopic = topic),
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
            onPressed: _isSaving ? null : _save,
            child: _isSaving
                ? const CircularProgressIndicator()
                : const Text('Save question'),
          ),
        ],
      ),
    );
  }
}
