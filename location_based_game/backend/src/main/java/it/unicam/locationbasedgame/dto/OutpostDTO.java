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

    private String placeId;

    private String placeName;

    private int difficulty;

    private int requiredPlayers;

    private int maxTopics;

    private List<Long> topicIds;

    private List<String> topicNames;

    private OutpostState state;
}
