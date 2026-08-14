package it.unicam.locationbasedgame.service.interfaces;

import it.unicam.locationbasedgame.dto.PlayerDTO;

import java.util.List;

/**
 * Defines the operations for Player entities.
 */
public interface IPlayerService {

    /**
     * Registers a new player joining the game.
     *
     * @param playerDTO the data of the player to create
     * @return the created player
     */
    PlayerDTO createPlayer(PlayerDTO playerDTO);

    /**
     * Gets a player by id.
     *
     * @param id the id of the player to get
     * @return the matching player
     */
    PlayerDTO getPlayerById(String id);

    /**
     * Gets all connected players.
     *
     * @return the list of all players
     */
    List<PlayerDTO> getAllPlayers();

    /**
     * Updates the team chosen by a player.
     *
     * @param id the id of the player to update
     * @param team the name of the team to assign, or null to clear it
     * @return the updated player
     */
    PlayerDTO updatePlayerTeam(String id, String team);

    /**
     * Removes a player, typically when they disconnect from the game.
     *
     * @param id the id of the player to remove
     */
    void deletePlayer(String id);
 
    /**
     * Logs an existing player in by checking the given credentials.
     *
     * @param name the display name of the player
     * @param password the password typed in by the player
     * @return the matching player
     */
    PlayerDTO login(String name, String password);
}
