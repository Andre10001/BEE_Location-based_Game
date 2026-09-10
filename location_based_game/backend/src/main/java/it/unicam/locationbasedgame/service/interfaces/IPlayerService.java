package it.unicam.locationbasedgame.service.interfaces;

import it.unicam.locationbasedgame.dto.PlayerDTO;

import java.util.List;

/**
 * Defines the operations for Player entities.
 */
public interface IPlayerService {

    /**
     * Registers a new player in the database.
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
     * Links a player to a BEE participant, or frees it if the participantId is null.
     *
     * @param id the id of the player to update
     * @param participantId the id of the BEE participant, or null to free it
     * @return the updated player
     */
    PlayerDTO linkParticipant(String id, String participantId);

    /**
     * Says the time left for a player's penalty.
     *
     * @param id the id of the player to check
     * @return the seconds left, or 0 when they are free to play
     */
    int getPenaltySecondsLeft(String id);

    /**
     * Takes a player out of the match they were in.
     *
     * @param id the id of the player leaving
     * @return the updated player
     */
    PlayerDTO leaveMatch(String id);

    /**
     * Takes every player out at once.
     *
     * @return how many players were actually holding something
     */
    int releaseAllPlayers();
 
    /**
     * Starts the penalty timer for a player, after a wrong answer.
     *
     * @param id the id of the player to punish
     * @return the updated player, carrying the seconds left
     */
    PlayerDTO startPenalty(String id);

    /**
     * Removes a player.
     *
     * @param id the id of the player to remove
     */
    void deletePlayer(String id);
 
    /**
     * Logs an existing player in by checking the given credentials.
     *
     * @param nickname the nickname of the player
     * @param password the password typed in by the player
     * @return the matching player
     */
    PlayerDTO login(String nickname, String password);
}
