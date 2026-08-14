package it.unicam.locationbasedgame.service.interfaces;

import it.unicam.locationbasedgame.dto.QuestionDTO;

import java.util.List;

/**
 * Defines the operations for Question entities.
 */
public interface IQuestionService {

    /**
     * Creates a new standalone question.
     *
     * @param questionDTO the data of the question to create
     * @return the created question
     */
    QuestionDTO createQuestion(QuestionDTO questionDTO);

    /**
     * Gets a question by id.
     *
     * @param id the id of the question to get
     * @return the matching question
     */
    QuestionDTO getQuestionById(Long id);

    /**
     * Gets every question available in the game.
     *
     * @return the list of all questions
     */
    List<QuestionDTO> getAllQuestions();

    /**
     * Gets all questions matching the given difficulty level.
     *
     * @param difficulty the difficulty level
     * @return the list of matching questions
     */
    List<QuestionDTO> getQuestionsByDifficulty(int difficulty);

    /**
     * Updates an existing question.
     *
     * @param id the id of the question to update
     * @param questionDTO the new data for the question
     * @return the updated question
     */
    QuestionDTO updateQuestion(Long id, QuestionDTO questionDTO);

    /**
     * Removes a question.
     *
     * @param id the id of the question to remove
     */
    void deleteQuestion(Long id);
}
