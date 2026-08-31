package it.unicam.locationbasedgame.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * The essential data of a question sent to a player who is attacking an outpost.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttackQuestionDTO {

    private Long questionId;

    private String topicName;

    private int difficulty;

    private String text;

    private List<String> options;
}
