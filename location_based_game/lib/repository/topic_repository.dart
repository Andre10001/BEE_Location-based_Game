import 'dart:convert';
import 'dart:io';

import '../models/question.dart';
import '../models/topic.dart';
import '../components/exception.dart';

/// Repository responsible for reading and persisting the [Topic] (and
/// nested [Question]) catalogue stored in `questions.json`.
///
/// This is a pure data-access layer: it only knows how to load, cache and
/// save the JSON file. It has no notion of outposts and does not enforce
/// any cross-entity rule (see `GameDataService` for that).
abstract class TopicRepository {
  /// Returns every [Topic] currently stored.
  Future<List<Topic>> getAllTopics();

  /// Returns the [Topic] named [name], or `null` if none exists.
  Future<Topic?> getTopicByName(String name);

  /// Adds a new [topic].
  ///
  /// Throws [DuplicateEntityException] if a topic with the same
  /// [Topic.name] already exists.
  Future<void> addTopic(Topic topic);

  /// Replaces the topic named [name] with [updatedTopic].
  ///
  /// Throws [EntityNotFoundException] if [name] doesn't exist.
  Future<void> updateTopic(String name, Topic updatedTopic);

  /// Removes the topic named [name].
  ///
  /// Throws [EntityNotFoundException] if [name] doesn't exist.
  Future<void> removeTopic(String name);

  /// All questions of [topicName] at the given [difficulty] (1-5).
  ///
  /// Throws [EntityNotFoundException] if [topicName] doesn't exist.
  Future<List<Question>> getQuestionsByDifficulty(
    String topicName,
    int difficulty,
  );

  /// Appends [question] to the topic named [topicName].
  ///
  /// Throws [EntityNotFoundException] if [topicName] doesn't exist.
  Future<void> addQuestionObject(String topicName, Question question);

  /// Adds a new [question] to the topic named [topicName].
  ///
  /// Throws [EntityNotFoundException] if [topicName] doesn't exist.
  Future<void> addnewQuestion(String topicName, int difficulty, String text, List<String> options, int correctOptionIndex, String explanation);

  /// Replaces the question at position [index] with [updatedQuestion].
  ///
  /// Throws [EntityNotFoundException] if [topicName] doesn't exist or
  /// [index] is out of range.
  Future<void> updateQuestion(
    String topicName,
    int index,
    Question updatedQuestion,
  );

  /// Removes the question at position [index] from the topic named
  /// [topicName].
  ///
  /// Throws [EntityNotFoundException] if [topicName] doesn't exist or
  /// [index] is out of range.
  Future<void> removeQuestion(String topicName, int index);
}

/// [TopicRepository] implementation backed by a JSON file on disk.
///
/// The file is read once and cached in memory; every mutating method
/// updates the cache first and then rewrites the whole file, so the cache
/// and the file on disk never drift apart.
class JsonTopicRepository implements TopicRepository {
  final File _file;
  List<Topic>? _cache;

  /// Points the repository at [path] (e.g. `'data/questions.json'`).
  JsonTopicRepository(String path) : _file = File(path);

  /// Points the repository at an already-resolved [file], useful on
  /// Flutter when the writable path comes from `path_provider`.
  JsonTopicRepository.fromFile(File file) : _file = file;

  Future<List<Topic>> _load() async {
    final cache = _cache;
    if (cache != null) return cache;
    if (!await _file.exists()) {
      return _cache = <Topic>[];
    }
    final content = await _file.readAsString();
    final decoded = jsonDecode(content) as Map<String, dynamic>;
    final topicsJson = (decoded['topics'] as List?) ?? const [];
    return _cache = topicsJson
        .map((t) => Topic.fromJson(t as Map<String, dynamic>))
        .toList();
  }

  Future<void> _save() async {
    final topics = _cache;
    if (topics == null) return;
    final data = {'topics': topics.map((t) => t.toJson()).toList()};
    await _file.writeAsString(const JsonEncoder.withIndent('  ').convert(data));
  }

  int _indexOfTopic(List<Topic> topics, String name) =>
      topics.indexWhere((t) => t.name == name);

  @override
  Future<List<Topic>> getAllTopics() async =>
      List.unmodifiable(await _load());

  @override
  Future<Topic?> getTopicByName(String name) async {
    final topics = await _load();
    final index = _indexOfTopic(topics, name);
    return index == -1 ? null : topics[index];
  }

  @override
  Future<void> addTopic(Topic topic) async {
    final topics = await _load();
    if (_indexOfTopic(topics, topic.name) != -1) {
      throw DuplicateEntityException(
        'A topic named "${topic.name}" already exists',
      );
    }
    topics.add(topic);
    await _save();
  }

  @override
  Future<void> updateTopic(String name, Topic updatedTopic) async {
    final topics = await _load();
    final index = _indexOfTopic(topics, name);
    if (index == -1) {
      throw EntityNotFoundException('No topic named "$name" found');
    }
    topics[index] = updatedTopic;
    await _save();
  }

  @override
  Future<void> removeTopic(String name) async {
    final topics = await _load();
    final index = _indexOfTopic(topics, name);
    if (index == -1) {
      throw EntityNotFoundException('No topic named "$name" found');
    }
    topics.removeAt(index);
    await _save();
  }

  @override
  Future<List<Question>> getQuestionsByDifficulty(
    String topicName,
    int difficulty,
  ) async {
    final topics = await _load();
    final index = _indexOfTopic(topics, topicName);
    if (index == -1) {
      throw EntityNotFoundException('No topic named "$topicName" found');
    }
    return topics[index].questionsByDifficulty(difficulty);
  }

  @override
  Future<void> addQuestionObject(String topicName, Question question) async {
    final topics = await _load();
    final index = _indexOfTopic(topics, topicName);
    if (index == -1) {
      throw EntityNotFoundException('No topic named "$topicName" found');
    }
    topics[index].questions.add(question);
    await _save();
  }

  @override
  Future<void> addnewQuestion(String topicName, int difficulty, String text, List<String> options, int correctOptionIndex, String explanation) async {
    final question = Question(
      difficulty: difficulty,
      text: text,
      options: options,
      correctOptionIndex: correctOptionIndex,
      explanation: explanation,
    );
    await addQuestionObject(topicName, question);
  }

  @override
  Future<void> updateQuestion(
    String topicName,
    int index,
    Question updatedQuestion,
  ) async {
    final topics = await _load();
    final topicIndex = _indexOfTopic(topics, topicName);
    if (topicIndex == -1) {
      throw EntityNotFoundException('No topic named "$topicName" found');
    }
    final questions = topics[topicIndex].questions;
    if (index < 0 || index >= questions.length) {
      throw EntityNotFoundException(
        'No question at index $index in topic "$topicName"',
      );
    }
    questions[index] = updatedQuestion;
    await _save();
  }

  @override
  Future<void> removeQuestion(String topicName, int index) async {
    final topics = await _load();
    final topicIndex = _indexOfTopic(topics, topicName);
    if (topicIndex == -1) {
      throw EntityNotFoundException('No topic named "$topicName" found');
    }
    final questions = topics[topicIndex].questions;
    if (index < 0 || index >= questions.length) {
      throw EntityNotFoundException(
        'No question at index $index in topic "$topicName"',
      );
    }
    questions.removeAt(index);
    await _save();
  }
}