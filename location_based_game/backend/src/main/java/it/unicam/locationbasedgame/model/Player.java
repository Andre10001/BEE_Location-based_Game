package it.unicam.locationbasedgame.model;

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

    /**
     * Primary Key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Display name the player typed in when joining. */
    @Column(nullable = false)
    private String name;

    /**
     * Password of the player when joining.
     */
    @Column(nullable = false)
    private String password;

    /** The team this player has chosen, or null if they haven't chosen yet. */
    @Enumerated(EnumType.STRING)
    private Team team;

    /**
     * Tells whether this player has already picked a team.
     *
     * @return true if a team has been chosen, false otherwise
     */
    @Transient
    public boolean hasChosenTeam() {
        return team != null;
    }
}
