package it.unicam.locationbasedgame.repository;

import it.unicam.locationbasedgame.model.Outpost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutpostRepository extends JpaRepository<Outpost, Long> {

    /**
     * Finds an outpost by the place name it represents on the map.
     *
     * @param place the place name to search for
     * @return the matching outpost, if any
     */
    Optional<Outpost> findByPlace(String place);
}
