package it.unicam.locationbasedgame.service;

import it.unicam.locationbasedgame.config.BeeClient;
import it.unicam.locationbasedgame.dto.AttackQuestionDTO;
import it.unicam.locationbasedgame.dto.AttackResultDTO;
import it.unicam.locationbasedgame.dto.OutpostDTO;
import it.unicam.locationbasedgame.enums.OutpostState;
import it.unicam.locationbasedgame.enums.Team;
import it.unicam.locationbasedgame.model.Outpost;
import it.unicam.locationbasedgame.model.Question;
import it.unicam.locationbasedgame.model.Topic;
import it.unicam.locationbasedgame.repository.OutpostRepository;
import it.unicam.locationbasedgame.repository.TopicRepository;
import it.unicam.locationbasedgame.service.interfaces.IOutpostService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Implementation of IOutpostService, supported by OutpostRepository.
 */
@Service
@RequiredArgsConstructor
public class OutpostService implements IOutpostService {

    private final OutpostRepository outpostRepository;
    private final TopicRepository topicRepository;
    private final BeeClient beeClient;
    
    private final Random random = new SecureRandom();

    @Override
    @Transactional
    public OutpostDTO assignTopics(String placeId, OutpostDTO outpostDTO) {
        validate(placeId, outpostDTO);

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
    public AttackQuestionDTO drawQuestion(String placeId, String team) {
        Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));

        Team attackingTeam = parseTeam(team);
        if (!outpost.canBeConqueredBy(attackingTeam)) {
            throw new IllegalArgumentException("Your team already holds this outpost");
        }

        if (outpost.isBeingCaptured()) {
            throw new IllegalArgumentException("Somebody is already answering for this outpost");
        }

        List<Question> candidates = new ArrayList<>();
        List<String> topicOfCandidate = new ArrayList<>();
        for (Topic topic : outpost.getTopics()) {
            for (Question question : topic.getQuestions()) {
                if (question.getDifficulty() == outpost.getDifficulty()) {
                    candidates.add(question);
                    topicOfCandidate.add(topic.getName());
                }
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No question of difficulty " + outpost.getDifficulty()
                    + " among the topics of this outpost");
        }

        outpost.startAttempt();
        outpostRepository.save(outpost);
        syncToBee(outpost);

        int chosen = random.nextInt(candidates.size());
        Question question = candidates.get(chosen);
        return new AttackQuestionDTO(question.getId(), topicOfCandidate.get(chosen),
                question.getDifficulty(), question.getText(), question.getOptions());
    }

    @Override
    @Transactional
    public AttackResultDTO answerQuestion(String placeId, Long questionId, int optionIndex, String team) {
        Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));

        Team attackingTeam = parseTeam(team);
        if (!outpost.canBeConqueredBy(attackingTeam)) {
            throw new IllegalArgumentException("Your team already holds this outpost");
        }

        Question question = outpost.getTopics().stream()
                .flatMap(topic -> topic.getQuestions().stream())
                .filter(candidate -> candidate.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "That question does not belong to this outpost"));

        boolean correct = question.isCorrect(optionIndex);
        String message;

        if (correct) {
            OutpostState previousState = outpost.getState();
            OutpostState newState = outpost.conquer(attackingTeam);
            outpostRepository.save(outpost);

            beeClient.updatePlaceStatus(placeId, newState.name());

            message = newState == OutpostState.neutral
                    ? "Right answer: the outpost is no longer held by the other team. "
                      + "Win another attack to take it."
                    : "Right answer: the outpost is yours.";
            if (newState == previousState) {
                message = "Right answer, but nothing changed here.";
            }
        } else {
            message = "Wrong answer: the outpost stays as it is.";
        }

        outpost.endAttempt(correct);
        outpostRepository.save(outpost);
        syncToBee(outpost);

        return new AttackResultDTO(correct, question.getCorrectOptionIndex(),
                question.getExplanation(), outpost.getState(), message);
    }

    @Override
    @Transactional
    public void cancelAttack(String placeId) {
        Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));
 
        if (!outpost.isBeingCaptured()) {
            return;
        }
        outpost.endAttempt(false);
        outpostRepository.save(outpost);
        syncToBee(outpost);
    }


    @Override
    @Transactional
    public void resetAttempt(String placeId) {
        Outpost outpost = outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));
 
        if (Outpost.ATTEMPT_PENDING.equals(outpost.getLastAttempt())) {
            return;
        }
        outpost.resetAttempt();
        outpostRepository.save(outpost);
        syncToBee(outpost);
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

    /** Synchronize outpost data between the application and BEE. */
    private void syncToBee(Outpost outpost) {
        String placeId = outpost.getPlaceId();
        boolean captured = outpost.getState() != OutpostState.neutral;

        beeClient.updatePlaceAttribute(placeId, "status", outpost.getState().name());
        beeClient.updatePlaceAttribute(placeId, "isCaptured", String.valueOf(captured));
        beeClient.updatePlaceAttribute(placeId, "isBeingCaptured",
                String.valueOf(outpost.isBeingCaptured()));
        beeClient.updatePlaceAttribute(placeId, "lastAttempt", outpost.getLastAttempt());
        beeClient.updatePlaceAttribute(placeId, "difficulty",
                String.valueOf(outpost.getDifficulty()));
        beeClient.updatePlaceAttribute(placeId, "requiredPlayers",
                String.valueOf(outpost.getRequiredPlayers()));
        beeClient.updatePlaceAttribute(placeId, "maxTopics",
                String.valueOf(outpost.getMaxTopics()));
    }

    /** Turns the team name sent by a client into a Team, or refuses it. */
    private Team parseTeam(String team) {
        try {
            return Team.valueOf(team);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown team: " + team);
        }
    }

    /** Checks what was sent before anything is written. */
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
