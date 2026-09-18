import 'dart:convert';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../api/question_api.dart';
import '../../api/topic_api.dart';
import '../../model/question.dart';
import '../../model/topic.dart';
import '../../widget/question_draft.dart';
import '../../widget/question_form.dart';
import '../../widget/topic_picker.dart';

/// Lets an administrator create topics and questions.
class CreateTopicPage extends StatefulWidget {
  const CreateTopicPage({super.key});

  @override
  State<CreateTopicPage> createState() => _CreateTopicPageState();
}

class _CreateTopicPageState extends State<CreateTopicPage> {
  final TextEditingController _nameController = TextEditingController();
  final List<QuestionDraft> _questionDrafts = [];
  final List<Topic?> _questionTopics = [];

  List<Topic> _topics = [];

  bool _isLoading = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadTopics();
  }

  @override
  void dispose() {
    _nameController.dispose();
    for (final draft in _questionDrafts) {
      draft.dispose();
    }
    super.dispose();
  }

  /* Loads every topic from the backend. */
  Future<void> _loadTopics() async {
    try {
      final topics = await TopicApi.getAllTopics();
      if (!mounted) return;
      setState(() => _topics = topics);
    } catch (error) {
      if (mounted) setState(() => _errorMessage = error.toString());
    }
  }

  /* Creates a topic with no questions. */
  Future<void> _createTopic() async {
    final String name = _nameController.text.trim();
    if (name.isEmpty) {
      setState(() => _errorMessage = 'The topic name must not be empty');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });
    try {
      await TopicApi.createTopic(Topic(name: name, questions: []));
      await _loadTopics();
      if (!mounted) return;
      _nameController.clear();
      _showMessage('Topic "$name" created');
    } catch (error) {
      setState(() => _errorMessage = error.toString());
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  /* Adds one more empty question block for questions. */
  void _addQuestion() {
    setState(() {
      _questionDrafts.add(QuestionDraft());
      _questionTopics.add(null);
    });
  }

  /* Removes the question block at specific index. */
  void _removeQuestion(int index) {
    setState(() {
      _questionDrafts[index].dispose();
      _questionDrafts.removeAt(index);
      _questionTopics.removeAt(index);
    });
  }

  /* Saves a question under the topic chosen for it. */
  Future<void> _saveQuestions() async {
    if (_questionDrafts.isEmpty) {
      setState(() => _errorMessage = 'Add at least one question');
      return;
    }
    for (int i = 0; i < _questionDrafts.length; i++) {
      if (_questionTopics[i] == null) {
        setState(() => _errorMessage = 'Question ${i + 1}: choose a topic');
        return;
      }
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
      for (int i = 0; i < _questionDrafts.length; i++) {
        await QuestionApi.createAndAssign(
          _questionDrafts[i].toQuestion(),
          _questionTopics[i]!.id!,
        );
      }
      final int saved = _questionDrafts.length;
      for (final draft in _questionDrafts) {
        draft.dispose();
      }
      setState(() {
        _questionDrafts.clear();
        _questionTopics.clear();
      });
      await _loadTopics();
      if (!mounted) return;
      _showMessage('$saved question(s) saved');
    } catch (error) {
      setState(() => _errorMessage = error.toString());
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  /* Reads a JSON file and saves every topic and question it contains. */
  Future<void> _importJsonFile() async {
    setState(() => _errorMessage = null);

    PlatformFile? file;
    try {
      file = await FilePicker.pickFile();
    } catch (error) {
      setState(() => _errorMessage = 'Could not open the file: $error');
      return;
    }
    if (file == null) return;

    setState(() => _isLoading = true);
    try {
      final bytes = await file.readAsBytes();
      final data = jsonDecode(utf8.decode(bytes)) as Map<String, dynamic>;
      final List<dynamic> rawTopics = data['topics'] as List<dynamic>;

      int savedQuestions = 0;
      for (final rawTopic in rawTopics) {
        final topicMap = rawTopic as Map<String, dynamic>;
        final Topic topic = await _topicByName(topicMap['name'] as String);

        final List<dynamic> rawQuestions =
            (topicMap['questions'] as List<dynamic>?) ?? [];
        for (final rawQuestion in rawQuestions) {
          final question =
              Question.fromJson(rawQuestion as Map<String, dynamic>);
          await QuestionApi.createAndAssign(question, topic.id!);
          savedQuestions++;
        }
      }

      await _loadTopics();
      if (!mounted) return;
      _showMessage('$savedQuestions question(s) loaded from the file');
    } catch (error) {
      setState(() => _errorMessage = 'The file could not be used: $error');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  /* Returns the topic with this name, creating it when it does not exist yet. */
  Future<Topic> _topicByName(String name) async {
    for (final topic in _topics) {
      if (topic.name.toLowerCase() == name.toLowerCase()) {
        return topic;
      }
    }

    final Topic created =
        await TopicApi.createTopic(Topic(name: name, questions: []));
    _topics = [..._topics, created];
    return created;
  }

  /* Shows a short message at the bottom of the screen. */
  void _showMessage(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Topics and questions')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'New topic',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _nameController,
            decoration: const InputDecoration(labelText: 'Topic name'),
          ),
          const SizedBox(height: 8),
          FilledButton.icon(
            icon: const Icon(Icons.add),
            label: const Text('Create topic'),
            onPressed: _isLoading ? null : _createTopic,
          ),

          const Divider(height: 36),

          Text(
            'Load from a file',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            icon: const Icon(Icons.upload_file),
            label: const Text('Load a JSON file'),
            onPressed: _isLoading ? null : _importJsonFile,
          ),

          const Divider(height: 36),

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
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 12),
                TopicPicker(
                  topics: _topics,
                  selectedTopic: _questionTopics[i],
                  label: 'Topic of question ${i + 1}',
                  onSelected: (topic) =>
                      setState(() => _questionTopics[i] = topic),
                ),
                QuestionForm(
                  draft: _questionDrafts[i],
                  title: 'Question ${i + 1}',
                  onChanged: () => setState(() {}),
                  onDelete: () => _removeQuestion(i),
                ),
              ],
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
            onPressed: _isLoading ? null : _saveQuestions,
            child: _isLoading
                ? const CircularProgressIndicator()
                : const Text('Save questions'),
          ),
        ],
      ),
    );
  }
}
