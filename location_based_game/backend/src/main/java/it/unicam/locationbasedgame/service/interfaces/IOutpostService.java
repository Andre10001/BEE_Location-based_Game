package it.unicam.locationbasedgame.service.interfaces;

import it.unicam.locationbasedgame.dto.OutpostDTO;

import java.util.List;

/**
 * Defines the operations for Outpost entities.
 */
public interface IOutpostService {

    /**
     * Creates a new outpost.
     *
     * @param outpostDTO the data of the outpost to create
     * @return the created outpost
     */
    OutpostDTO createOutpost(OutpostDTO outpostDTO);

    /**
     * Gets an outpost by id.
     *
     * @param id the id of the outpost to get
     * @return the matching outpost
     */
    OutpostDTO getOutpostById(Long id);

    /**
     * Gets every outpost placed on the map.
     *
     * @return the list of all outposts
     */
    List<OutpostDTO> getAllOutposts();

    /**
     * Updates an existing outpost.
     *
     * @param id the id of the outpost to update
     * @param outpostDTO the new data for the outpost
     * @return the updated outpost
     */
    OutpostDTO updateOutpost(Long id, OutpostDTO outpostDTO);

    /**
     * Attempts a conquest of the given outpost by the given team, applying
     * the domain rules defined in the Outpost entity.
     *
     * @param id the id of the outpost being conquered
     * @param team the name of the attacking team
     * @return the updated outpost
     */
    OutpostDTO conquerOutpost(Long id, String team);

    /**
     * Removes an outpost.
     *
     * @param id the id of the outpost to remove
     */
    void deleteOutpost(Long id);
}
