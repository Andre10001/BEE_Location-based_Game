package it.unicam.locationbasedgame.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object used to expose and receive
 * data about the match state.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchStateDTO {

    private boolean started;

    private boolean running;

    private int secondsLeft;

    private int team1Outposts;

    private int team2Outposts;

    private String winner;
}