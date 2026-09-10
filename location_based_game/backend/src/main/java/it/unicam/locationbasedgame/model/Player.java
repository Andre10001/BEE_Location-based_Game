package it.unicam.locationbasedgame.model;

import java.time.Instant;

import it.unicam.locationbasedgame.enums.Role;
import it.unicam.locationbasedgame.enums.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a single participant connected to the game from their own device.
 */
@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    /** Primary Key. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Nickname of the player when joining. */
    @Column(nullable = false, unique = true)
    private String nickname;

    /** Password of the player when joining. */
    @Column(nullable = false)
    private String password;
    
    /** Role of the player. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.player;
    
    /** The team this player has chosen, or null if they haven't chosen yet. */
    @Enumerated(EnumType.STRING)
    private Team team;

    /** The pool of the BPMN process this player is impersonating. */
    @Column(unique = true)
    private String beeParticipantId;

    /** The penalty until which the player cannot attack. */
    private Instant penaltyUntil;

    /**
     * How many seconds this player still has to wait, zero when free.
     *
     * @return the seconds left, never negative
     */
    @Transient
    public int getPenaltySecondsLeft() {
        if (penaltyUntil == null) {
            return 0;
        }
        long left = penaltyUntil.getEpochSecond() - Instant.now().getEpochSecond();
        return left > 0 ? (int) left : 0;
    }

    /**
     * Says whether this player is serving a penalty right now.
     *
     * @return true if they cannot attack yet, false otherwise
     */
    @Transient
    public boolean isUnderPenalty() {
        return getPenaltySecondsLeft() > 0;
    }

    /**
     * Starts the penalty time.
     *
     * @param seconds how long it lasts, as the process decided
     */
    public void startPenalty(int seconds) {
        this.penaltyUntil = Instant.now().plusSeconds(seconds);
    }

    /** Frees this player from any penalty. */
    public void clearPenalty() {
        this.penaltyUntil = null;
    }

    /**
     * Says whether this player has already picked a team.
     *
     * @return true if a team has been chosen, false otherwise
     */
    @Transient
    public boolean hasChosenTeam() {
        return team != null;
    }

    /**
     * Says whether the player is ready to enter the match.
     *
     * @return true if the player can enter the match, false otherwise
     */
    @Transient
    public boolean isReady() {
        return team != null && beeParticipantId != null && !beeParticipantId.isBlank();
    }
}