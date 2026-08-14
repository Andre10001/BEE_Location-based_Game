package it.unicam.locationbasedgame.model;

import it.unicam.locationbasedgame.enums.OutpostState;
import it.unicam.locationbasedgame.enums.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Represents an Outpost that the player can select on the game map.
 * An Outpost is linked to a specific number of Topics (questions can
 * be drawn from any of them), has a required difficulty level for the
 * question that will be asked, requires a minimum number of players
 * to answer it, and sits at a named place on the map.
 */
@Entity
@Table(name = "outposts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Outpost {

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The name of the place this outpost represents on the map. */
    @Column(nullable = false)
    private String place;

    /**
     * The topics this outpost draws questions.
     */
    @ManyToMany
    @JoinTable(
            name = "outpost_topics",
            joinColumns = @JoinColumn(name = "outpost_id"),
            inverseJoinColumns = @JoinColumn(name = "topic_id")
    )
    private List<Topic> topics;

    /** Difficulty level required for this outpost's question*/
    @Column(nullable = false)
    private int difficulty;

    /** Minimum number of players that must answer this outpost's question. */
    @Column(nullable = false)
    private int requiredPlayers;

    /** Who currently controls this outpost. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutpostState state = OutpostState.neutral;

    /**
     * Tells whether this outpost is currently unclaimed.
     *
     * @return true if the state is neutral, false otherwise
     */
    @Transient
    public boolean isNeutral() {
        return state == OutpostState.neutral;
    }

    /**
     * Tells whether the given team is currently allowed to start a conquest
     * of this outpost. A team can never conquer an outpost it already owns;
     * a neutral outpost can always be conquered by either team.
     *
     * @param team the team attempting the conquest
     * @return true if team can start a conquest, false otherwise
     */
    public boolean canBeConqueredBy(Team team) {
        return isNeutral() || state.getOwnerTeam() != team;
    }

    /**
     * Applies the outcome of a correctly answered conquest question started
     * by the given team. Call this only after the associated question has
     * been answered correctly.
     * Rules:
     * - If the outpost is neutral, it becomes the attacking team's.
     * - If the outpost belongs to the opposing team, this first successful
     *   conquest only strips that ownership away, turning it back to
     *   neutral. The team must then start and win a second conquest (once
     *   it is neutral) to actually make it theirs.
     * - If the outpost already belongs to team, nothing changes.
     *
     * @param team the team that won the conquest question
     * @return the resulting OutpostState
     */
    public OutpostState conquer(Team team) {
        if (!canBeConqueredBy(team)) {
            return state;
        }
        if (state == OutpostState.neutral) {
            state = team == Team.team1 ? OutpostState.team1 : OutpostState.team2;
        } else if (state == OutpostState.team1 && team == Team.team2) {
            state = OutpostState.neutral;
        } else if (state == OutpostState.team2 && team == Team.team1) {
            state = OutpostState.neutral;
        }
        return state;
    }

    /**
     * All questions across topics that match this outpost's difficulty.
     *
     * @return the list of matching questions
     */
    @Transient
    public List<Question> getMatchingQuestions() {
        return topics.stream()
                .flatMap(topic -> topic.questionsByDifficulty(difficulty).stream())
                .collect(Collectors.toList());
    }

    /**
     * Picks a random question matching this outpost's difficulty from one
     * of its topics.
     *
     * @return a random matching question, or null if none match
     */
    @Transient
    public Question pickRandomQuestion() {
        List<Question> candidates = getMatchingQuestions();
        if (candidates.isEmpty()) {
            return null;
        }
        Random random = new SecureRandom();
        return candidates.get(random.nextInt(candidates.size()));
    }
}
