package it.unicam.locationbasedgame.service;

import it.unicam.locationbasedgame.dto.OutpostDTO;
import it.unicam.locationbasedgame.enums.Team;
import it.unicam.locationbasedgame.model.Outpost;
import it.unicam.locationbasedgame.model.Topic;
import it.unicam.locationbasedgame.repository.OutpostRepository;
import it.unicam.locationbasedgame.repository.TopicRepository;
import it.unicam.locationbasedgame.service.interfaces.IOutpostService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of IOutpostService, supported by OutpostRepository.
 */
@Service
@RequiredArgsConstructor
public class OutpostService implements IOutpostService {

    private final OutpostRepository outpostRepository;
    private final TopicRepository topicRepository;

    @Override
    @Transactional
    public OutpostDTO assignTopics(String placeId, OutpostDTO outpostDTO) {
        validate(placeId, outpostDTO);

        // Creates the outpost the first time topics are given to a place.
        Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseGet(() -> {
                    Outpost created = new Outpost();
                    created.setPlaceId(placeId);
                    return created;
                });

        outpost.setPlaceName(outpostDTO.getPlaceName().trim());
        outpost.setDifficulty(outpostDTO.getDifficulty());
        outpost.setRequiredPlayers(outpostDTO.getRequiredPlayers());
        outpost.setMaxTopics(outpostDTO.getMaxTopics());

        List<Topic> topics = new ArrayList<>();
        for (Long topicId : outpostDTO.getTopicIds()) {
            Topic topic = topicRepository.findById(topicId)
                    .orElseThrow(() -> new EntityNotFoundException("Topic not found with id " + topicId));
            if (!topics.contains(topic)) {
                topics.add(topic);
            }
        }
        outpost.setTopics(topics);

        return toDto(outpostRepository.save(outpost));
    }

    @Override
    public OutpostDTO getOutpostByPlaceId(String placeId) {
        Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));
        return toDto(outpost);
    }

    @Override
    public List<OutpostDTO> getAllOutposts() {
        return outpostRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public OutpostDTO conquerOutpost(String placeId, String team) {
        Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));
        outpost.conquer(Team.valueOf(team));
        return toDto(outpostRepository.save(outpost));
    }

    @Override
    public void deleteOutpost(String placeId) {
                Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));
        outpostRepository.delete(outpost);
    }

    /**
     * Checks what was sent before anything is written. */
    private void validate(String placeId, OutpostDTO dto) {
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("placeId must not be empty");
        }
        if (dto == null || dto.getTopicIds() == null) {
            throw new IllegalArgumentException("topicIds must not be null");
        }
        if (dto.getPlaceName() == null || dto.getPlaceName().isBlank()) {
            throw new IllegalArgumentException("placeName must not be empty");
        }
        if (dto.getDifficulty() < 1 || dto.getDifficulty() > 5) {
            throw new IllegalArgumentException("difficulty must be between 1 and 5");
        }
        if (dto.getRequiredPlayers() < 1) {
            throw new IllegalArgumentException("requiredPlayers must be at least 1");
        }
        if (dto.getMaxTopics() < 1) {
            throw new IllegalArgumentException("maxTopics must be at least 1");
        }
        if (dto.getTopicIds().size() > dto.getMaxTopics()) {
            throw new IllegalArgumentException(
                    "This outpost accepts at most " + dto.getMaxTopics() + " topics");
        }
    }

    /** Converts an Outpost entity into its DTO representation. */
    private OutpostDTO toDto(Outpost outpost) {
        List<Long> topicIds = outpost.getTopics().stream()
                .map(Topic::getId)
                .collect(Collectors.toList());
        List<String> topicNames = outpost.getTopics().stream()
                .map(Topic::getName)
                .collect(Collectors.toList());
       return new OutpostDTO(outpost.getId(), outpost.getPlaceId(), outpost.getPlaceName(),
                outpost.getDifficulty(), outpost.getRequiredPlayers(), outpost.getMaxTopics(),
                topicIds, topicNames, outpost.getState());
    }
}
