package it.unicam.locationbasedgame.controller;

import it.unicam.locationbasedgame.dto.PlayerDTO;
import it.unicam.locationbasedgame.service.interfaces.IPlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contains the calls to the database for the Player entity.
 */
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final IPlayerService playerService;

    @PostMapping("/createPlayer")
    public ResponseEntity<PlayerDTO> createPlayer(@RequestBody PlayerDTO playerDTO) {
        PlayerDTO created = playerService.createPlayer(playerDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/getPlayerById/{id}")
    public ResponseEntity<PlayerDTO> getPlayerById(@PathVariable String id) {
        return ResponseEntity.ok(playerService.getPlayerById(id));
    }

    @GetMapping("/getAllPlayers")
    public ResponseEntity<List<PlayerDTO>> getAllPlayers() {
        return ResponseEntity.ok(playerService.getAllPlayers());
    }

    @PutMapping("/updatePlayerTeam/{id}")
    public ResponseEntity<PlayerDTO> updatePlayerTeam(@PathVariable String id,
                                                        @RequestParam(required = false) String team) {
        return ResponseEntity.ok(playerService.updatePlayerTeam(id, team));
    }

    @DeleteMapping("/deletePlayer/{id}")
    public ResponseEntity<Void> deletePlayer(@PathVariable String id) {
        playerService.deletePlayer(id);
        return ResponseEntity.noContent().build();
    }
 
    @PostMapping("/login")
    public ResponseEntity<PlayerDTO> login(@RequestBody PlayerDTO playerDTO) {
        return ResponseEntity.ok(playerService.login(playerDTO.getNickname(), playerDTO.getPassword()));
    }
}
