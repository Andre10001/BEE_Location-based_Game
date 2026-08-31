package it.unicam.locationbasedgame.service.interfaces;

import it.unicam.locationbasedgame.dto.AttackQuestionDTO;
import it.unicam.locationbasedgame.dto.AttackResultDTO;
import it.unicam.locationbasedgame.dto.OutpostDTO;

import java.util.List;

/**
 * Defines the operations for Outpost entities.
 */
public interface IOutpostService {

    /**
     * Assigns topics to the outpost create in BEE environment.
     *
     * @param placeId the id of the place in the BEE environment
     * @param outpostDTO the place details and the topics to assign
     * @return the outpost as it is after the change
     */
    OutpostDTO assignTopics(String placeId, OutpostDTO outpostDTO);

    /**
     * Gets an outpost by id.
     *
     * @param placeId the id of the outpost in the BEE environment
     * @return the matching outpost
     */
    OutpostDTO getOutpostByPlaceId(String placeId);

    /**
     * Gets every outpost placed on the map.
     *
     * @return the list of all outposts
     */
    List<OutpostDTO> getAllOutposts();

    /**
     * Draws a random question for an attack on the given place.
     *
     * @param placeId the id of the place in the BEE environment
     * @param team the name of the attacking team
     * @return the question the player has to answer
     */
    AttackQuestionDTO drawQuestion(String placeId, String team);

    /**
     * Checks the answer given by a player.
     *
     * @param placeId the id of the place in the BEE environment
     * @param questionId the question that was asked
     * @param optionIndex the option the player chose
     * @param team the name of the attacking team
     * @return what happened, including the correct answer
     */
    AttackResultDTO answerQuestion(String placeId, Long questionId, int optionIndex, String team);

    /**
     * Attempts a conquest of the given outpost by the given team, applying
     * the domain rules defined in the Outpost entity.
     *
     * @param placeId the id of the outpost in the BEE environment
     * @param team the name of the attacking team
     * @return the updated outpost
     */
    OutpostDTO conquerOutpost(String placeId, String team);

    /**
     * Removes an outpost.
     *
     * @param placeId the id of the outpost in the BEE environment
     */
    void deleteOutpost(String placeId);
}
