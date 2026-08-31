package it.unicam.locationbasedgame.controller;

import it.unicam.locationbasedgame.dto.AttackQuestionDTO;
import it.unicam.locationbasedgame.dto.AttackResultDTO;
import it.unicam.locationbasedgame.dto.OutpostDTO;
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

    @PostMapping("/assignTopics/{placeId}")
    public ResponseEntity<OutpostDTO> assignTopics(@PathVariable String placeId,
                                                   @RequestBody OutpostDTO outpostDTO) {
        return ResponseEntity.ok(outpostService.assignTopics(placeId, outpostDTO));
    }

    @GetMapping("/getOutpostByPlace/{placeId}")
    public ResponseEntity<OutpostDTO> getOutpostByPlace(@PathVariable String placeId) {
        return ResponseEntity.ok(outpostService.getOutpostByPlaceId(placeId));
    }

    @GetMapping("/getAllOutposts")
    public ResponseEntity<List<OutpostDTO>> getAllOutposts() {
        return ResponseEntity.ok(outpostService.getAllOutposts());
    }

    @GetMapping("/drawQuestion/{placeId}")
    public ResponseEntity<AttackQuestionDTO> drawQuestion(@PathVariable String placeId,
                                                          @RequestParam String team) {
        return ResponseEntity.ok(outpostService.drawQuestion(placeId, team));
    }

    @PostMapping("/answer/{placeId}")
    public ResponseEntity<AttackResultDTO> answerQuestion(@PathVariable String placeId,
                                                          @RequestParam String team,
                                                          @RequestBody Map<String, Object> body) {
        Long questionId = Long.valueOf(body.get("questionId").toString());
        int optionIndex = Integer.parseInt(body.get("optionIndex").toString());
        return ResponseEntity.ok(
                outpostService.answerQuestion(placeId, questionId, optionIndex, team));
    }

    @PostMapping("/conquerOutpost/{placeId}")
    public ResponseEntity<OutpostDTO> conquerOutpost(@PathVariable String placeId,
                                                     @RequestParam String team) {
        return ResponseEntity.ok(outpostService.conquerOutpost(placeId, team));
    }

    @DeleteMapping("/deleteOutpost/{placeId}")
    public ResponseEntity<Void> deleteOutpost(@PathVariable String placeId) {
        outpostService.deleteOutpost(placeId);
        return ResponseEntity.noContent().build();
    }
}
