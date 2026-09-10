package it.unicam.locationbasedgame.service.interfaces;

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
     * Resets the last attempt back to pending.
     *
     * @param placeId the id of the place in the BEE environment
     */
    void resetAttempt(String placeId);

    /**
     * Synchronizes the outposts data with the map that was deployed in BEE.
     *
     * @return every outpost after the synchronisation
     */
    List<OutpostDTO> syncWithEnvironment();

    /**
     * Gets every outpost placed on the map.
     *
     * @return the list of all outposts
     */
    List<OutpostDTO> getAllOutposts();

    /**
     * Removes an outpost.
     *
     * @param placeId the id of the outpost in the BEE environment
     */
    void deleteOutpost(String placeId);
}