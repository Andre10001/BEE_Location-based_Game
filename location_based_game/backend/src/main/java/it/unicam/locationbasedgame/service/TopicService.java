package it.unicam.locationbasedgame.service;

import it.unicam.locationbasedgame.dto.QuestionDTO;
import it.unicam.locationbasedgame.dto.TopicDTO;
import it.unicam.locationbasedgame.model.Question;
import it.unicam.locationbasedgame.model.Topic;
import it.unicam.locationbasedgame.repository.QuestionRepository;
import it.unicam.locationbasedgame.repository.TopicRepository;
import it.unicam.locationbasedgame.service.interfaces.ITopicService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of ITopicService, supported by TopicRepository.
 */
@Service
@RequiredArgsConstructor
public class TopicService implements ITopicService {

    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    @Override
    public TopicDTO createTopic(TopicDTO topicDTO) {
        Topic topic = toEntity(topicDTO);
        Topic saved = topicRepository.save(topic);
        return toDto(saved);
    }

    @Override
    public TopicDTO getTopicById(Long id) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Topic not found with id " + id));
        return toDto(topic);
    }

    @Override
    public List<TopicDTO> getAllTopics() {
        return topicRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public TopicDTO updateTopic(Long id, TopicDTO topicDTO) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Topic not found with id " + id));
        topic.setName(topicDTO.getName());
        topic.setQuestions(toQuestionEntities(topicDTO.getQuestions()));
        Topic saved = topicRepository.save(topic);
        return toDto(saved);
    }

    @Override
    public void deleteTopic(Long id) {
        if (!topicRepository.existsById(id)) {
            throw new EntityNotFoundException("Topic not found with id " + id);
        }
        topicRepository.deleteById(id);
    }
    
    @Override
    @Transactional
    public TopicDTO assignQuestions(Long topicId, List<Long> questionIds) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new EntityNotFoundException("Topic not found with id " + topicId));
        for (Long questionId : questionIds) {
            Question question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new EntityNotFoundException("Question not found with id " + questionId));
            topic.assignQuestion(question);
        }
        Topic saved = topicRepository.save(topic);
        return toDto(saved);
    }

    /** Converts a Topic entity, including its questions, into its DTO representation. */
    private TopicDTO toDto(Topic topic) {
        List<QuestionDTO> questionDTOs = topic.getQuestions().stream()
                .map(q -> new QuestionDTO(q.getId(), q.getDifficulty(), q.getText(), q.getOptions(),
                        q.getCorrectOptionIndex(), q.getExplanation()))
                .collect(Collectors.toList());
        return new TopicDTO(topic.getId(), topic.getName(), questionDTOs);
    }

    /** Converts a TopicDTO into a new Topic entity. */
    private Topic toEntity(TopicDTO dto) {
        Topic topic = new Topic();
        topic.setName(dto.getName());
        topic.setQuestions(toQuestionEntities(dto.getQuestions()));
        return topic;
    }

    /** Converts a list of QuestionDTO into a list of new Question entities. */
    private List<Question> toQuestionEntities(List<QuestionDTO> questionDTOs) {
        return questionDTOs.stream()
                .map(q -> new Question(q.getId(), q.getDifficulty(), q.getText(), q.getOptions(),
                        q.getCorrectOptionIndex(), q.getExplanation()))
                .collect(Collectors.toList());
    }
}
