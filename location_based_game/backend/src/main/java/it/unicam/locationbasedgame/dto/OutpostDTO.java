package it.unicam.locationbasedgame.dto;

import it.unicam.locationbasedgame.enums.OutpostState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Data Transfer Object used to expose and receive
 * Outpost data through the REST API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutpostDTO {

    private Long id;

    private String place;

    private List<Long> topicIds;

    private int difficulty;

    private int requiredPlayers;

    private OutpostState state;
}
