package it.unicam.locationbasedgame.repository;

import it.unicam.locationbasedgame.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, String> {

    /**
     * Finds a player by their display name.
     *
     * @param nickname the display name to search for
     * @return the matching player, if any
     */
    Optional<Player> findByNickname(String nickname);
}
