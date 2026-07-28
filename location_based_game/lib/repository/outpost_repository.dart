import 'dart:convert';
import 'dart:io';

import '../models/outpost.dart';
import '../models/topic.dart';
import '../components/exception.dart';
import 'topic_repository.dart';

/// Repository responsible for reading and persisting the [Outpost] map
/// data stored in `outposts.json`.
///
/// Loading an outpost requires resolving its [Topic] references by name
/// (see [Outpost.fromJson]), so this repository depends on a
/// [TopicRepository] purely to assemble outposts correctly, it does not
/// otherwise enforce any cross-entity business rule.
abstract class OutpostRepository {
  /// Returns every [Outpost] currently stored.
  Future<List<Outpost>> getAllOutposts();

  /// Returns the [Outpost] at [place], or `null` if none exists.
  Future<Outpost?> getOutpostByPlace(String place);

  /// Adds a brand new [outpost].
  ///
  /// Throws [DuplicateEntityException] if an outpost with the same
  /// [Outpost.place] already exists.
  Future<void> addOutpost(Outpost outpost);

  /// Replaces the outpost at [place] with [updatedOutpost].
  ///
  /// Throws [EntityNotFoundException] if [place] doesn't exist.
  Future<void> updateOutpost(String place, Outpost updatedOutpost);

  /// Removes the outpost at [place].
  ///
  /// Throws [EntityNotFoundException] if [place] doesn't exist.
  Future<void> removeOutpost(String place);
}

/// [OutpostRepository] implementation backed by a JSON file on disk.
///
/// The file is read once and cached in memory; every mutating method
/// updates the cache first and then rewrites the whole file, so the cache
/// and the file on disk never drift apart.
class JsonOutpostRepository implements OutpostRepository {
  final File _file;
  final TopicRepository _topicRepository;
  List<Outpost>? _cache;

  /// Points the repository at [path] (e.g. `'data/outposts.json'`),
  /// resolving each outpost's topics through [topicRepository].
  JsonOutpostRepository(String path, TopicRepository topicRepository)
      : _file = File(path),
        _topicRepository = topicRepository;

  /// Points the repository at an already-resolved [file], useful on
  /// Flutter when the writable path comes from `path_provider`.
  JsonOutpostRepository.fromFile(File file, TopicRepository topicRepository)
      : _file = file,
        _topicRepository = topicRepository;

  Future<List<Outpost>> _load() async {
    final cache = _cache;
    if (cache != null) return cache;
    if (!await _file.exists()) {
      return _cache = <Outpost>[];
    }
    final content = await _file.readAsString();
    final decoded = jsonDecode(content) as Map<String, dynamic>;
    final outpostsJson = (decoded['outposts'] as List?) ?? const [];
    final availableTopics = await _topicRepository.getAllTopics();
    return _cache = outpostsJson
        .map((o) => Outpost.fromJson(
              o as Map<String, dynamic>,
              availableTopics: availableTopics,
            ))
        .toList();
  }

  Future<void> _save() async {
    final outposts = _cache;
    if (outposts == null) return;
    final data = {'outposts': outposts.map((o) => o.toJson()).toList()};
    await _file.writeAsString(const JsonEncoder.withIndent('  ').convert(data));
  }

  int _indexOfOutpost(List<Outpost> outposts, String place) =>
      outposts.indexWhere((o) => o.place == place);

  @override
  Future<List<Outpost>> getAllOutposts() async =>
      List.unmodifiable(await _load());

  @override
  Future<Outpost?> getOutpostByPlace(String place) async {
    final outposts = await _load();
    final index = _indexOfOutpost(outposts, place);
    return index == -1 ? null : outposts[index];
  }

  @override
  Future<void> addOutpost(Outpost outpost) async {
    final outposts = await _load();
    if (_indexOfOutpost(outposts, outpost.place) != -1) {
      throw DuplicateEntityException(
        'An outpost at "${outpost.place}" already exists',
      );
    }
    outposts.add(outpost);
    await _save();
  }

  @override
  Future<void> updateOutpost(String place, Outpost updatedOutpost) async {
    final outposts = await _load();
    final index = _indexOfOutpost(outposts, place);
    if (index == -1) {
      throw EntityNotFoundException('No outpost at "$place" found');
    }
    outposts[index] = updatedOutpost;
    await _save();
  }

  @override
  Future<void> removeOutpost(String place) async {
    final outposts = await _load();
    final index = _indexOfOutpost(outposts, place);
    if (index == -1) {
      throw EntityNotFoundException('No outpost at "$place" found');
    }
    outposts.removeAt(index);
    await _save();
  }
}