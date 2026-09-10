package it.unicam.locationbasedgame.controller;

import it.unicam.locationbasedgame.dto.AttackResultDTO;
import it.unicam.locationbasedgame.dto.AttackStateDTO;
import it.unicam.locationbasedgame.dto.OutpostDTO;
import it.unicam.locationbasedgame.service.interfaces.ICaptureService;
import it.unicam.locationbasedgame.service.interfaces.IOutpostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Contains the calls to the database for the Outpost entity.
 */
@RestController
@RequestMapping("/api/outposts")
@RequiredArgsConstructor
public class OutpostController {

    private final IOutpostService outpostService;
    private final ICaptureService captureService;

    @PostMapping("/assignTopics/{placeId}")
    public ResponseEntity<OutpostDTO> assignTopics(@PathVariable String placeId,
                                                   @RequestBody OutpostDTO outpostDTO) {
        return ResponseEntity.ok(outpostService.assignTopics(placeId, outpostDTO));
    }

    @GetMapping("/getOutpostByPlace/{placeId}")
    public ResponseEntity<OutpostDTO> getOutpostByPlace(@PathVariable String placeId) {
        return ResponseEntity.ok(outpostService.getOutpostByPlaceId(placeId));
    }

    @PostMapping("/syncWithEnvironment")
    public ResponseEntity<List<OutpostDTO>> syncWithEnvironment() {
        return ResponseEntity.ok(outpostService.syncWithEnvironment());
    }

    @GetMapping("/getAllOutposts")
    public ResponseEntity<List<OutpostDTO>> getAllOutposts() {
        return ResponseEntity.ok(outpostService.getAllOutposts());
    }
    
    @PostMapping("/joinAttack/{placeId}")
    public ResponseEntity<AttackStateDTO> joinAttack(@PathVariable String placeId,
                                                     @RequestParam String playerId) {
        return ResponseEntity.ok(captureService.join(placeId, playerId));
    }

    @PostMapping("/leaveAttack/{placeId}")
    public ResponseEntity<AttackStateDTO> leaveAttack(@PathVariable String placeId,
                                                      @RequestParam String playerId) {
        return ResponseEntity.ok(captureService.leave(placeId, playerId));
    }

    @GetMapping("/attackState/{placeId}")
    public ResponseEntity<AttackStateDTO> attackState(@PathVariable String placeId,
                                                      @RequestParam String playerId) {
        return ResponseEntity.ok(captureService.getState(placeId, playerId));
    }

    @PostMapping("/answer/{placeId}")
    public ResponseEntity<AttackResultDTO> answerQuestion(@PathVariable String placeId,
                                                          @RequestParam String playerId,
                                                          @RequestBody Map<String, Object> body) {
        Long questionId = Long.valueOf(body.get("questionId").toString());
        int optionIndex = Integer.parseInt(body.get("optionIndex").toString());
        return ResponseEntity.ok(
                captureService.answer(placeId, playerId, questionId, optionIndex));
    }

    @PostMapping("/cancelAttack/{placeId}")
    public ResponseEntity<Void> cancelAttack(@PathVariable String placeId) {
        captureService.cancel(placeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resetAttempt/{placeId}")
    public ResponseEntity<Void> resetAttempt(@PathVariable String placeId) {
        outpostService.resetAttempt(placeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/deleteOutpost/{placeId}")
    public ResponseEntity<Void> deleteOutpost(@PathVariable String placeId) {
        outpostService.deleteOutpost(placeId);
        return ResponseEntity.noContent().build();
    }
}