package it.unicam.locationbasedgame.repository;

import it.unicam.locationbasedgame.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    /**
     * Finds a topic by its unique name.
     *
     * @param name the topic name to search for
     * @return the matching topic, if any
     */
    Optional<Topic> findByName(String name);
}
