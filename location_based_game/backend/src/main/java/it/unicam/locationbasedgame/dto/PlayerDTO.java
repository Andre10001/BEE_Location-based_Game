package it.unicam.locationbasedgame.dto;

import it.unicam.locationbasedgame.enums.Team;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object used to expose and receive
 * Player data through the REST API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDTO {

    private String id;

    private String name;

    private String password;

    private Team team;
}
