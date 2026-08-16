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
    public OutpostDTO createOutpost(OutpostDTO outpostDTO) {
        validate(outpostDTO);
        Outpost outpost = toEntity(outpostDTO);
        Outpost saved = outpostRepository.save(outpost);
        return toDto(saved);
    }

    @Override
    public OutpostDTO getOutpostById(Long id) {
        Outpost outpost = outpostRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Outpost not found with id " + id));
        return toDto(outpost);
    }

    @Override
    public List<OutpostDTO> getAllOutposts() {
        return outpostRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public OutpostDTO updateOutpost(Long id, OutpostDTO outpostDTO) {
        validate(outpostDTO);
        Outpost outpost = outpostRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Outpost not found with id " + id));
        outpost.setPlace(outpostDTO.getPlace());
        outpost.setTopics(resolveTopics(outpostDTO.getTopicIds()));
        outpost.setDifficulty(outpostDTO.getDifficulty());
        outpost.setRequiredPlayers(outpostDTO.getRequiredPlayers());
        Outpost saved = outpostRepository.save(outpost);
        return toDto(saved);
    }

    @Override
    public OutpostDTO conquerOutpost(Long id, String team) {
        Outpost outpost = outpostRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Outpost not found with id " + id));
        outpost.conquer(Team.valueOf(team));
        Outpost saved = outpostRepository.save(outpost);
        return toDto(saved);
    }

    @Override
    public void deleteOutpost(Long id) {
        if (!outpostRepository.existsById(id)) {
            throw new EntityNotFoundException("Outpost not found with id " + id);
        }
        outpostRepository.deleteById(id);
    }

    /**
     * Checks the data integrity of the provided OutpostDTO.
     *
     * @param dto the outpost data to validate
     * @throws IllegalArgumentException if any field is invalid
     */
    private void validate(OutpostDTO dto) {
        if (dto.getPlace() == null || dto.getPlace().isBlank()) {
            throw new IllegalArgumentException("place must not be empty");
        }
        if (dto.getTopicIds() == null || dto.getTopicIds().isEmpty() || dto.getTopicIds().size() > 3) {
            throw new IllegalArgumentException("an Outpost must be linked to 1 to 3 topics");
        }
        if (dto.getDifficulty() < 1 || dto.getDifficulty() > 5) {
            throw new IllegalArgumentException("difficulty must be between 1 and 5");
        }
        if (dto.getRequiredPlayers() < 1) {
            throw new IllegalArgumentException("requiredPlayers must be at least 1");
        }
    }

    /** Converts an Outpost entity into its DTO representation. */
    private OutpostDTO toDto(Outpost outpost) {
        List<Long> topicIds = outpost.getTopics().stream()
                .map(Topic::getId)
                .collect(Collectors.toList());
        return new OutpostDTO(outpost.getId(), outpost.getPlace(), topicIds, outpost.getDifficulty(),
                outpost.getRequiredPlayers(), outpost.getState());
    }

    /** Converts an OutpostDTO into a new Outpost entity. */
    private Outpost toEntity(OutpostDTO dto) {
        Outpost outpost = new Outpost();
        outpost.setPlace(dto.getPlace());
        outpost.setTopics(resolveTopics(dto.getTopicIds()));
        outpost.setDifficulty(dto.getDifficulty());
        outpost.setRequiredPlayers(dto.getRequiredPlayers());
        return outpost;
    }

    /** Loads the Topic entities matching the given ids, failing if any id does not exist. */
    private List<Topic> resolveTopics(List<Long> topicIds) {
        return topicIds.stream()
                .map(topicId -> topicRepository.findById(topicId)
                        .orElseThrow(() -> new EntityNotFoundException("Topic not found with id " + topicId)))
                .collect(Collectors.toList());
    }
}
