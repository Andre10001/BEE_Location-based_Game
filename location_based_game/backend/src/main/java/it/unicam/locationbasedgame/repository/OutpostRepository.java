package it.unicam.locationbasedgame.repository;

import it.unicam.locationbasedgame.model.Outpost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutpostRepository extends JpaRepository<Outpost, Long> {

    /**
     * Finds the outpost of the BEE environment by the place id.
     *
     * @param placeId the id of the place in the BEE environment
     * @return the matching outpost, if any
     */
    Optional<Outpost> findByPlaceId(String placeId);
}
