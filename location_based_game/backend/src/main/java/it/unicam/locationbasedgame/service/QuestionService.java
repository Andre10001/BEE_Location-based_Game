package it.unicam.locationbasedgame.service;

import it.unicam.locationbasedgame.dto.QuestionDTO;
import it.unicam.locationbasedgame.model.Question;
import it.unicam.locationbasedgame.repository.QuestionRepository;
import it.unicam.locationbasedgame.service.interfaces.IQuestionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of IQuestionService, supported by QuestionRepository.
 */
@Service
@RequiredArgsConstructor
public class QuestionService implements IQuestionService {

    private final QuestionRepository questionRepository;

    @Override
    public QuestionDTO createQuestion(QuestionDTO questionDTO) {
        validate(questionDTO);
        Question question = toEntity(questionDTO);
        Question saved = questionRepository.save(question);
        return toDto(saved);
    }

    @Override
    public QuestionDTO getQuestionById(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Question not found with id " + id));
        return toDto(question);
    }

    @Override
    public List<QuestionDTO> getAllQuestions() {
        return questionRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<QuestionDTO> getQuestionsByDifficulty(int difficulty) {
        return questionRepository.findByDifficulty(difficulty).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public QuestionDTO updateQuestion(Long id, QuestionDTO questionDTO) {
        validate(questionDTO);
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Question not found with id " + id));
        question.setDifficulty(questionDTO.getDifficulty());
        question.setText(questionDTO.getText());
        question.setOptions(questionDTO.getOptions());
        question.setCorrectOptionIndex(questionDTO.getCorrectOptionIndex());
        question.setExplanation(questionDTO.getExplanation());
        Question saved = questionRepository.save(question);
        return toDto(saved);
    }

    @Override
    public void deleteQuestion(Long id) {
        if (!questionRepository.existsById(id)) {
            throw new EntityNotFoundException("Question not found with id " + id);
        }
        questionRepository.deleteById(id);
    }

    /**
     * Checks the data integrity of the provided QuestionDTO.
     *
     * @param dto the question data to validate
     * @throws IllegalArgumentException if any field is invalid
     */
    private void validate(QuestionDTO dto) {
        if (dto.getDifficulty() < 1 || dto.getDifficulty() > 5) {
            throw new IllegalArgumentException("difficulty must be between 1 and 5");
        }
        if (dto.getText() == null || dto.getText().isBlank()) {
            throw new IllegalArgumentException("text must not be empty");
        }
        if (dto.getOptions() == null || dto.getOptions().size() < 2 || dto.getOptions().size() > 5) {
            throw new IllegalArgumentException("options must contain between 2 and 5 elements");
        }
        if (dto.getCorrectOptionIndex() < 0 || dto.getCorrectOptionIndex() >= dto.getOptions().size()) {
            throw new IllegalArgumentException("correctOptionIndex must be a valid index of options");
        }
        if (dto.getExplanation() == null || dto.getExplanation().isBlank()) {
            throw new IllegalArgumentException("explanation must not be empty");
        }
    }

    /** Converts a Question entity into its DTO representation. */
    private QuestionDTO toDto(Question question) {
        return new QuestionDTO(question.getId(), question.getDifficulty(), question.getText(),
                question.getOptions(), question.getCorrectOptionIndex(), question.getExplanation());
    }

    /** Converts a QuestionDTO into a new Question entity. */
    private Question toEntity(QuestionDTO dto) {
        Question question = new Question();
        question.setDifficulty(dto.getDifficulty());
        question.setText(dto.getText());
        question.setOptions(dto.getOptions());
        question.setCorrectOptionIndex(dto.getCorrectOptionIndex());
        question.setExplanation(dto.getExplanation());
        return question;
    }
}
