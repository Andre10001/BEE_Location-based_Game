package it.unicam.locationbasedgame.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a subject containing a set of related Question entities.
 */
@Entity
@Table(name = "topics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Topic {

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The name of the topic. */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * The list of questions belonging to this topic.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "topic_id")
    private List<Question> questions;

    /**
     * Returns all questions in this topic with the given difficulty.
     *
     * @param difficulty the difficulty level to filter by
     * @return the list of matching questions
     */
    @Transient
    public List<Question> questionsByDifficulty(int difficulty) {
        return questions.stream()
                .filter(question -> question.getDifficulty() == difficulty)
                .collect(Collectors.toList());
    }

    /**
     * Assigns question to this topic, unless it is already one of its questions.
     *
     * @param question the already-persisted question to assign to this topic
     */
    public void assignQuestion(Question question) {
        if (!questions.contains(question)) {
            questions.add(question);
        }
    }
}
