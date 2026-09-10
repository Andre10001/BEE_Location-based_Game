package it.unicam.locationbasedgame.service.interfaces;

import it.unicam.locationbasedgame.dto.MatchStateDTO;

/**
 * Defines the operations for the Match.
 */
public interface IMatchService {

    /**
     * The state of the match for the given player.
     *
     * @param playerId the id of player
     * @return the time left, the outposts each team holds, and the winner
     */
    MatchStateDTO getState(String playerId);

    /**
     * Closes a finished match and frees every player who took part.
     *
     * @return the final state, with the result already in it
     */
    MatchStateDTO endMatch();
}