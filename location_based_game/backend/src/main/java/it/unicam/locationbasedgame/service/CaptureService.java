package it.unicam.locationbasedgame.service;

import it.unicam.locationbasedgame.config.BeeClient;
import it.unicam.locationbasedgame.dto.AttackResultDTO;
import it.unicam.locationbasedgame.dto.AttackStateDTO;
import it.unicam.locationbasedgame.dto.PlayerDTO;
import it.unicam.locationbasedgame.enums.OutpostState;
import it.unicam.locationbasedgame.enums.Team;
import it.unicam.locationbasedgame.model.Outpost;
import it.unicam.locationbasedgame.model.Question;
import it.unicam.locationbasedgame.model.Topic;
import it.unicam.locationbasedgame.repository.OutpostRepository;
import it.unicam.locationbasedgame.service.interfaces.ICaptureService;
import it.unicam.locationbasedgame.service.interfaces.IPlayerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ICaptureService.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CaptureService implements ICaptureService {

    private static final int TIME_TO_ANSWER = 30;
    private static final int ADDITIONAL_TIME = 10;

    private final OutpostRepository outpostRepository;
    private final IPlayerService playerService;
    private final BeeClient beeClient;
    private final PlatformTransactionManager transactionManager;

    private final Object captureLock = new Object();

    private TransactionTemplate transaction;

    @PostConstruct
    void prepareTransactions() {
        this.transaction = new TransactionTemplate(transactionManager);
    }

    private final Random random = new SecureRandom();

    /**
     * One conquest being formed or played out.
     */
    private static class Attempt {

        private Team team;
        private int required;
        private boolean started;

        /** Who agreed to attack. */
        private final Set<String> ready = new LinkedHashSet<>();

        /** The question each player was given, once the group filled up. */
        private final Map<String, Question> questionByPlayer = new LinkedHashMap<>();

        /** Which topic each question came from. */
        private final Map<String, String> topicByPlayer = new LinkedHashMap<>();

        /** How long this group was given. */
        private int limitSeconds;

        /** The moment after which no answer counts any more. */
        private Instant deadline;

        int secondsLeftToAnswer() {
            if (deadline == null) {
                return 0;
            }
            long left = deadline.getEpochSecond() - Instant.now().getEpochSecond();
            return left > 0 ? (int) left : 0;
        }

        boolean isLate() {
            return started && outcome == null && deadline != null
                    && Instant.now().isAfter(deadline);
        }

        /** Who has answered, and whether they were right. */
        private final Map<String, Boolean> correctByPlayer = new LinkedHashMap<>();

        /**
         * What the group achieved, once the last of them answered.
         */
        private String outcome;

        /** Who has been shown that outcome, so it can be dropped in the end. */
        private final Set<String> told = new LinkedHashSet<>();

        boolean isSettled() {
            return outcome != null;
        }

        boolean everybodyAnswered() {
            return started && correctByPlayer.size() >= questionByPlayer.size();
        }

        boolean everybodyRight() {
            return everybodyAnswered() && !correctByPlayer.containsValue(false);
        }
    }

    private final Map<String, Attempt> attemptsByPlace = new ConcurrentHashMap<>();

    // ----- GROUP CREATION -----

    @Override
    public AttackStateDTO join(String placeId, String playerId) {
        synchronized (captureLock) {
            return transaction.execute(status -> doJoin(placeId, playerId));
        }
    }

    private AttackStateDTO doJoin(String placeId, String playerId) {
        Outpost outpost = outpostOf(placeId);
        PlayerDTO player = playerService.getPlayerById(playerId);

        Team team = teamOf(player);
        if (!outpost.canBeConqueredBy(team)) {
            throw new IllegalArgumentException("Your team already holds this outpost");
        }

        int waiting = player.getPenaltySecondsLeft() == null ? 0 : player.getPenaltySecondsLeft();
        if (waiting > 0) {
            throw new IllegalArgumentException(
                    "You have to wait " + waiting + " more seconds before attacking again");
        }

        requireStandingIn(player, placeId);

        Attempt previous = attemptsByPlace.get(placeId);
        if (previous != null) {
            expireIfLate(placeId, previous);
        }
        if (previous != null && previous.isSettled()) {
            attemptsByPlace.remove(placeId);
        }

        Attempt attempt = attemptsByPlace.computeIfAbsent(placeId, key -> {
            Attempt fresh = new Attempt();
            fresh.team = team;
            fresh.required = Math.max(outpost.getRequiredPlayers(), 1);
            return fresh;
        });

        if (attempt.started) {
            throw new IllegalArgumentException("This conquest has already begun");
        }
        if (attempt.team != team) {
            throw new IllegalArgumentException(
                    "The other team is already gathering on this outpost");
        }

        attempt.ready.add(playerId);
        log.info("[Capture] " + player.getNickname() + " is ready on " + placeId + " (" + attempt.ready.size() + "/" + attempt.required + ")");

        if (attempt.ready.size() >= attempt.required) {
            begin(outpost, attempt);
        }
        return stateOf(attempt, playerId);
    }

    @Override
    public AttackStateDTO leave(String placeId, String playerId) {
        synchronized (captureLock) {
            return transaction.execute(status -> doLeave(placeId, playerId));
        }
    }

    private AttackStateDTO doLeave(String placeId, String playerId) {
        Attempt attempt = attemptsByPlace.get(placeId);
        if (attempt == null) {
            return emptyState(outpostOf(placeId));
        }
        if (attempt.started) {
            throw new IllegalArgumentException(
                    "The questions have been handed out: this conquest can no longer be called off");
        }

        attempt.ready.remove(playerId);
        if (attempt.ready.isEmpty()) {
            attemptsByPlace.remove(placeId);
            return emptyState(outpostOf(placeId));
        }
        return stateOf(attempt, playerId);
    }

    /**
     * Distributes the questions and locks the outpost.
     */
    private void begin(Outpost outpost, Attempt attempt) {
        List<Question> questions = new ArrayList<>();
        List<String> topics = new ArrayList<>();
        for (Topic topic : outpost.getTopics()) {
            for (Question question : topic.getQuestions()) {
                if (question.getDifficulty() == outpost.getDifficulty()) {
                    questions.add(question);
                    topics.add(topic.getName());
                }
            }
        }
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("No question of difficulty "
                    + outpost.getDifficulty() + " among the topics of this outpost");
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) order.add(i);
        Collections.shuffle(order, random);

        int next = 0;
        for (String playerId : attempt.ready) {
            int index = order.get(next % order.size());
            attempt.questionByPlayer.put(playerId, questions.get(index));
            attempt.topicByPlayer.put(playerId, topics.get(index));
            next++;
        }

        attempt.started = true;
        attempt.limitSeconds = limitFor(attempt.required);
        attempt.deadline = Instant.now().plusSeconds(attempt.limitSeconds);
        outpost.startAttempt();
        outpostRepository.save(outpost);
        syncToBee(outpost);

        log.info("[Capture] Conquest of " + outpost.getPlaceId() + " begun with " + attempt.questionByPlayer.size() + " player(s)");
    }

    /**
     * The time limit for a conquest, given how many of them there are.
     *
     * @param required how many players the outpost asks for
     * @return the limit in seconds
     */
    private int limitFor(int required) {
        return TIME_TO_ANSWER + ADDITIONAL_TIME * Math.max(required - 1, 0);
    }

    /**
     * Closes a conquest whose time ran out.
     *
     * @param placeId the place the conquest is on
     * @param attempt the conquest to check
     * @return true if it was closed by this call
     */
    private boolean expireIfLate(String placeId, Attempt attempt) {
        if (!attempt.isLate()) {
            return false;
        }

        int silent = 0;
        for (String playerId : attempt.questionByPlayer.keySet()) {
            if (attempt.correctByPlayer.containsKey(playerId)) {
                continue;
            }
            attempt.correctByPlayer.put(playerId, false);
            silent++;
            try {
                playerService.startPenalty(playerId);
            } catch (RuntimeException e) {
                log.warn("[Capture] Could not start the penalty of " + playerId + ": " + e.getMessage());
            }
        }

        Outpost outpost = outpostOf(placeId);
        attempt.outcome = "Time is up. " + settle(outpost, attempt);
        log.info("[Capture] Conquest of " + placeId + " ran out of time with " + silent + " player(s) silent");
        return true;
    }

    // ----- ANSWERING -----

    @Override
    public AttackResultDTO answer(String placeId, String playerId,
                                  Long questionId, int optionIndex) {
        synchronized (captureLock) {
            return transaction.execute(status -> doAnswer(placeId, playerId, questionId, optionIndex));
        }
    }

    private AttackResultDTO doAnswer(String placeId, String playerId,
                                     Long questionId, int optionIndex) {
        Attempt attempt = attemptsByPlace.get(placeId);
        if (attempt == null || !attempt.started) {
            throw new IllegalArgumentException("No conquest is running on this outpost");
        }

        if (expireIfLate(placeId, attempt)) {
            throw new IllegalArgumentException("Time is up: this conquest is over");
        }

        Question question = attempt.questionByPlayer.get(playerId);
        if (question == null) {
            throw new IllegalArgumentException("You are not part of this conquest");
        }
        if (attempt.correctByPlayer.containsKey(playerId)) {
            throw new IllegalArgumentException("You have already answered");
        }
        if (!question.getId().equals(questionId)) {
            throw new IllegalArgumentException("That is not the question you were given");
        }

        boolean correct = question.isCorrect(optionIndex);
        attempt.correctByPlayer.put(playerId, correct);

        String message;
        int penaltySeconds = 0;
        if (correct) {
            message = "Right answer.";
        } else {
            message = "Wrong answer.";
            try {
                penaltySeconds = playerService.startPenalty(playerId).getPenaltySecondsLeft();
                message += " You cannot attack for " + penaltySeconds + " seconds.";
            } catch (RuntimeException e) {
                log.warn("[Capture] Could not start the penalty of " + playerId + ": " + e.getMessage());
            }
        }

        Outpost outpost = outpostOf(placeId);

        if (attempt.everybodyAnswered()) {
            attempt.outcome = settle(outpost, attempt);
            attempt.told.add(playerId);
            message += " " + attempt.outcome;
        } else {
            int left = attempt.questionByPlayer.size() - attempt.correctByPlayer.size();
            message += " Waiting for " + left + " more player(s).";
        }

        AttackResultDTO result = new AttackResultDTO();
        result.setCorrect(correct);
        result.setCorrectOptionIndex(question.getCorrectOptionIndex());
        result.setExplanation(question.getExplanation());
        result.setState(outpost.getState());
        result.setMessage(message);
        result.setPenaltySeconds(penaltySeconds);
        return result;
    }

    /**
     * Says the outcome of a conquest, and what the player sees of it.
     */
    private String settle(Outpost outpost, Attempt attempt) {
        boolean won = attempt.everybodyRight();
        String message;

        if (won) {
            OutpostState previous = outpost.getState();
            OutpostState now = outpost.conquer(attempt.team);
            if (now == previous) {
                message = "Nothing changed here.";
            } else if (now == OutpostState.neutral) {
                message = "The outpost is no longer held by the other team. "
                        + "Win another conquest to take it.";
            } else {
                message = "The outpost is yours.";
            }
        } else {
            message = "Somebody in your group got it wrong, so the outpost stays as it is.";
        }

        outpost.endAttempt(won);
        outpostRepository.save(outpost);
        syncToBee(outpost);
        return message;
    }

    // ----- OUTPOST STATE -----

    @Override
    public AttackStateDTO getState(String placeId, String playerId) {
        synchronized (captureLock) {
            Attempt attempt = attemptsByPlace.get(placeId);
            if (attempt == null) {
                return emptyState(outpostOf(placeId));
            }

            expireIfLate(placeId, attempt);

            AttackStateDTO state = stateOf(attempt, playerId);

            if (attempt.isSettled() && attempt.questionByPlayer.containsKey(playerId)) {
                attempt.told.add(playerId);
                if (attempt.told.size() >= attempt.questionByPlayer.size()) {
                    attemptsByPlace.remove(placeId);
                }
            }
            return state;
        }
    }

    @Override
    public void cancel(String placeId) {
        synchronized (captureLock) {
            transaction.executeWithoutResult(status -> doCancel(placeId));
        }
    }

    private void doCancel(String placeId) {
        Attempt attempt = attemptsByPlace.remove(placeId);
        if (attempt == null || !attempt.started) {
            return;
        }

        Outpost outpost = outpostOf(placeId);
        outpost.endAttempt(false);
        outpostRepository.save(outpost);
        syncToBee(outpost);
    }

    @Override
    public void clear() {
        synchronized (captureLock) {
            attemptsByPlace.clear();
        }
    }

    // ----- HELPERS -----

    /** The state of an outpost nobody is attacking. */
    private AttackStateDTO emptyState(Outpost outpost) {
        int required = Math.max(outpost.getRequiredPlayers(), 1);

        AttackStateDTO state = new AttackStateDTO();
        state.setRequired(required);
        state.setMessage(required > 1
                ? "This outpost needs " + required + " players of the same team."
                : "");
        return state;
    }

    /** The state of a running conquest, through the eyes of one player. */
    private AttackStateDTO stateOf(Attempt attempt, String playerId) {
        AttackStateDTO state = new AttackStateDTO();
        state.setRequired(attempt.required);
        state.setTeam(attempt.team.name());
        state.setReadyCount(attempt.ready.size());
        state.setSecondsToAnswer(attempt.limitSeconds);
        state.setSecondsLeftToAnswer(attempt.isSettled() ? 0 : attempt.secondsLeftToAnswer());
        state.setReady(attempt.ready.contains(playerId));
        state.setStarted(attempt.started);
        state.setFinished(attempt.isSettled());
        state.setAnswered(attempt.correctByPlayer.containsKey(playerId));
        state.setAnsweredCount(attempt.correctByPlayer.size());

        Question question = attempt.questionByPlayer.get(playerId);
        if (attempt.started && question != null) {
            state.setQuestionId(question.getId());
            state.setTopicName(attempt.topicByPlayer.get(playerId));
            state.setDifficulty(question.getDifficulty());
            state.setText(question.getText());
            state.setOptions(question.getOptions());
        }
        if (attempt.isSettled() && question != null) {
            state.setCorrectOptionIndex(question.getCorrectOptionIndex());
            state.setExplanation(question.getExplanation());
        }

        if (attempt.isSettled()) {
            state.setMessage(attempt.outcome);
        } else if (!attempt.started) {
            state.setMessage("Waiting for " + (attempt.required - attempt.ready.size())
                    + " more player(s) of your team to be ready.");
        } else if (state.isAnswered()) {
            int missing = attempt.questionByPlayer.size() - attempt.correctByPlayer.size();
            state.setMessage("Waiting for " + missing + " more player(s) to answer.");
        } else {
            state.setMessage("");
        }
        return state;
    }

    /** Refuses a player who is not standing where they claim to be. */
    private void requireStandingIn(PlayerDTO player, String placeId) {
        String where = beeClient.getParticipantPlaceId(player.getBeeParticipantId());
        if (where == null) {
            throw new IllegalArgumentException("The process does not know where you are");
        }
        if (!where.equals(placeId)) {
            throw new IllegalArgumentException("You are not standing in this outpost");
        }
    }

    private Outpost outpostOf(String placeId) {
        return outpostRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new EntityNotFoundException("No outpost on place " + placeId));
    }

    private Team teamOf(PlayerDTO player) {
        if (player.getTeam() == null) {
            throw new IllegalArgumentException("You are not in a team");
        }
        return player.getTeam();
    }

    /**
     * Syncs the state of an outpost onto its place in BEE.
     */
    private void syncToBee(Outpost outpost) {
        String placeId = outpost.getPlaceId();
        boolean captured = outpost.getState() != OutpostState.neutral;

        beeClient.updatePlaceAttribute(placeId, "status", outpost.getState().name());
        beeClient.updatePlaceAttribute(placeId, "isCaptured", String.valueOf(captured));
        beeClient.updatePlaceAttribute(placeId, "isBeingCaptured",
                String.valueOf(outpost.isBeingCaptured()));
        beeClient.updatePlaceAttribute(placeId, "lastAttempt", outpost.getLastAttempt());
        beeClient.updatePlaceAttribute(placeId, "requiredPlayers",
                String.valueOf(outpost.getRequiredPlayers()));
    }
}