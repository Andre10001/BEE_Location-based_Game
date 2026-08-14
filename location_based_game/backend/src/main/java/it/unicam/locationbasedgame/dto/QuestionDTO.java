package it.unicam.locationbasedgame.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Data Transfer Object used to expose and receive
 * Question data through the REST API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDTO {

    private Long id;

    private int difficulty;

    private String text;

    private List<String> options;

    private int correctOptionIndex;

    private String explanation;
}
