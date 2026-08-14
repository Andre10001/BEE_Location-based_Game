package it.unicam.locationbasedgame.repository;

import it.unicam.locationbasedgame.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * Finds all questions matching the given difficulty level.
     *
     * @param difficulty the difficulty level to filter by (1-5)
     * @return the list of matching questions
     */
    List<Question> findByDifficulty(int difficulty);
}
