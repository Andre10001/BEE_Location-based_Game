package it.unicam.locationbasedgame.service;

import it.unicam.locationbasedgame.config.EngineClient;
import it.unicam.locationbasedgame.dto.MatchStateDTO;
import it.unicam.locationbasedgame.dto.PlayerDTO;
import it.unicam.locationbasedgame.enums.OutpostState;
import it.unicam.locationbasedgame.model.Outpost;
import it.unicam.locationbasedgame.repository.OutpostRepository;
import it.unicam.locationbasedgame.service.interfaces.IMatchService;
import it.unicam.locationbasedgame.service.interfaces.IPlayerService;
import it.unicam.locationbasedgame.service.interfaces.IProcessTimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of IMatchService.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MatchService implements IMatchService {

    private final OutpostRepository outpostRepository;
    private final IPlayerService playerService;
    private final IProcessTimerService processTimerService;
    private final EngineClient engineClient;

    @Override
    public MatchStateDTO getState(String playerId) {
        int team1 = 0;
        int team2 = 0;
        for (Outpost outpost : outpostRepository.findAll()) {
            if (outpost.getState() == OutpostState.team1) team1++;
            if (outpost.getState() == OutpostState.team2) team2++;
        }

        MatchStateDTO state = new MatchStateDTO();
        state.setTeam1Outposts(team1);
        state.setTeam2Outposts(team2);
        state.setWinner(aheadBetween(team1, team2));

        int left = secondsLeftFor(playerId);
        state.setSecondsLeft(left);
        state.setRunning(left > 0);
        state.setStarted(left > 0 || anyClockStillRunning());
        return state;
    }

    @Override
    public MatchStateDTO endMatch() {
        if (anyClockStillRunning()) {
            throw new IllegalArgumentException("The match is still being played");
        }
 
        int released = playerService.releaseAllPlayers();
        log.info("[MatchService] Match closed, " + released + " player(s) released");
        return getState(null);
    }
 
    /** Whether someone's playtime has already run out. */
    private boolean anyClockStillRunning() {
        for (PlayerDTO player : playerService.getAllPlayers()) {
            if (player.getBeeParticipantId() == null) {
                continue;
            }
            if (secondsLeftFor(player.getId()) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Says how many seconds are left on the clock for the given player. */
    private int secondsLeftFor(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return 0;
        }

        try {
            PlayerDTO player = playerService.getPlayerById(playerId);
            if (player.getBeeParticipantId() == null) {
                return 0;
            }
            String gameTime = processTimerService.getGameTimeActivityId(player.getBeeParticipantId());
            return engineClient.secondsLeftOn(playerId, gameTime);
        } catch (RuntimeException e) {
            log.warn("[MatchService] Could not read the clock of " + playerId + ": " + e.getMessage());
            return 0;
        }
    }


    /** Who holds the most outposts, or a draw. */
    private String aheadBetween(int team1, int team2) {
        if (team1 > team2) return OutpostState.team1.name();
        if (team2 > team1) return OutpostState.team2.name();
        return "Draw";
    }
}