package it.unicam.locationbasedgame.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Data Transfer Object used to expose and receive
 * Topic data through the REST API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopicDTO {

    private Long id;

    private String name;

    private List<QuestionDTO> questions;
}
