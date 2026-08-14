package it.unicam.locationbasedgame.service.interfaces;

import it.unicam.locationbasedgame.dto.TopicDTO;

import java.util.List;

/**
 * Defines the operations for Topic entities.
 */
public interface ITopicService {

    /**
     * Creates a new topic together with its questions.
     *
     * @param topicDTO the data of the topic to create
     * @return the created topic
     */
    TopicDTO createTopic(TopicDTO topicDTO);

    /**
     * Gets a topic by id.
     *
     * @param id the id of the topic to get
     * @return the matching topic
     */
    TopicDTO getTopicById(Long id);

    /**
     * Gets every topic available in the game.
     *
     * @return the list of all topics
     */
    List<TopicDTO> getAllTopics();

    /**
     * Updates an existing topic.
     *
     * @param id the id of the topic to update
     * @param topicDTO the new data for the topic
     * @return the updated topic
     */
    TopicDTO updateTopic(Long id, TopicDTO topicDTO);

    /**
     * Removes a topic.
     *
     * @param id the id of the topic to remove
     */
    void deleteTopic(Long id);

    /**
     * Assigns existing questions to a topic.
     *
     * @param topicId the id of the topic to update
     * @param questionIds the ids of the questions to assign
     * @return the updated topic
     */
    public TopicDTO assignQuestions(Long topicId, List<Long> questionIds);
}
