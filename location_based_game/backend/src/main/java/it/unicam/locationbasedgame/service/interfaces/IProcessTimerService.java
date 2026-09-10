package it.unicam.locationbasedgame.service.interfaces;

/**
 * Finds the timers of the deployed BPMN model.
 */
public interface IProcessTimerService {

    /**
     * The id of the timer event that manages the penalty timer after a wrong answer.
     *
     * @param participantId the pool the player is impersonating
     * @return the id of the penalty event in that pool's process
     */
    int getPenaltySeconds(String participantId);

    /**
     * The id of the timer event that manages the time of the game.
     *
     * @param participantId the pool the player is impersonating
     * @return the id of the game time event in that pool's process
     */
    String getGameTimeActivityId(String participantId);

    /**
     * Forgets all the timers.
     */
    void forget();
}