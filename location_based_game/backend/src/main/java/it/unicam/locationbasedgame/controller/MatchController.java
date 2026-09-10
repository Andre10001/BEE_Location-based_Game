package it.unicam.locationbasedgame.controller;

import it.unicam.locationbasedgame.dto.MatchStateDTO;
import it.unicam.locationbasedgame.service.interfaces.IMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contains the calls about the match being played.
 */
@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final IMatchService matchService;

    @GetMapping("/state")
    public ResponseEntity<MatchStateDTO> getState(
            @RequestParam(required = false) String playerId) {
        return ResponseEntity.ok(matchService.getState(playerId));
    }

    @PostMapping("/end")
    public ResponseEntity<MatchStateDTO> endMatch() {
        return ResponseEntity.ok(matchService.endMatch());
    }
}