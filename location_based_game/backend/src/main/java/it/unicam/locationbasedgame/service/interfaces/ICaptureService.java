package it.unicam.locationbasedgame.service.interfaces;

import it.unicam.locationbasedgame.dto.AttackResultDTO;
import it.unicam.locationbasedgame.dto.AttackStateDTO;

/**
 * Defines the operations for the capture of an outpost by a group of players.
 */
public interface ICaptureService {

    /**
     * Declares a player ready to attack the outpost they are standing in.
     *
     * @param placeId the id of the place in the BEE environment
     * @param playerId the player that is ready
     * @return how the conquest stands after this
     */
    AttackStateDTO join(String placeId, String playerId);

    /**
     * Takes a ready player back out of the conquest.
     *
     * @param placeId the id of the place in the BEE environment
     * @param playerId the player that wants to leave
     * @return how the conquest stands after this
     */
    AttackStateDTO leave(String placeId, String playerId);

    /**
     * How the conquest of an outpost stands, as seen by one player.
     *
     * @param placeId the id of the place in the BEE environment
     * @param playerId the player asking
     * @return the state, including that player's own question
     */
    AttackStateDTO getState(String placeId, String playerId);

    /**
     * Records the answer of one player.
     *
     * @param placeId the id of the place in the BEE environment
     * @param playerId the player answering
     * @param questionId the question they were given
     * @param optionIndex the option they chose
     * @return what happened, including the correct answer
     */
    AttackResultDTO answer(String placeId, String playerId, Long questionId, int optionIndex);

    /**
     * Drops a conquest nobody finished, freeing the outpost.
     *
     * @param placeId the id of the place in the BEE environment
     */
    void cancel(String placeId);

    /** Forgets every conquest in progress, when a new match begins. */
    void clear();
}
