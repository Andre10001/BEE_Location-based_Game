package it.unicam.locationbasedgame.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Represents a single multiple-choice question belonging to a Topic.
 */
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Difficulty level of the question. */
    @Column(nullable = false)
    private int difficulty;

    /** The question prompt. */
    @Column(nullable = false, length = 1000)
    private String text;

    /**
     * The list of possible answers. Must contain between 2 and 5 options.
     */
    @ElementCollection
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_index")
    @Column(name = "option_text", nullable = false)
    private List<String> options;

    /** Index of the correct answer within options. */
    @Column(nullable = false)
    private int correctOptionIndex;

    /** Explanation of the correct answer. */
    @Column(nullable = false, length = 2000)
    private String explanation;

    /**
     * Gets the text of the correct answer.
     *
     * @return the option text located at correctOptionIndex
     */
    @Transient
    public String getCorrectAnswer() {
        return options.get(correctOptionIndex);
    }

    /**
     * Checks whether the given option index corresponds to the correct
     * answer.
     *
     * @param optionIndex the index to check
     * @return true if optionIndex is the correct answer, false otherwise
     */
    public boolean isCorrect(int optionIndex) {
        return optionIndex == correctOptionIndex;
    }
}
