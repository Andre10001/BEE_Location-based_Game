package it.unicam.locationbasedgame.dto;

import it.unicam.locationbasedgame.enums.OutpostState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object used to expose and receive
 * data about the result of an attack.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttackResultDTO {

    private boolean correct;

    private int correctOptionIndex;

    private String explanation;

    private OutpostState state;

    private String message;

    private int penaltySeconds;
}
