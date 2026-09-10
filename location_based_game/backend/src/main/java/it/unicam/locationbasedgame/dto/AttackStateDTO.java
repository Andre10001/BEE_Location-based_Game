package it.unicam.locationbasedgame.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Data Transfer Object used to expose and receive
 * data about the attack state.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttackStateDTO {

    private int required;
    
    private String team;

    private int readyCount;

    private boolean ready;

    private boolean started;

    private boolean finished;

    private boolean answered;

    private int answeredCount;

    private int secondsToAnswer;

    private int secondsLeftToAnswer;

    private String message;

    private Long questionId;

    private String topicName;

    private Integer difficulty;

    private String text;

    private List<String> options;

    private Integer correctOptionIndex;

    private String explanation;
}
