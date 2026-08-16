package it.unicam.locationbasedgame.controller;

import it.unicam.locationbasedgame.dto.OutpostDTO;
import it.unicam.locationbasedgame.service.interfaces.IOutpostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contains the calls to the database for the Outpost entity.
 */
@RestController
@RequestMapping("/api/outposts")
@RequiredArgsConstructor
public class OutpostController {

    private final IOutpostService outpostService;

    @PostMapping("/createOutpost")
    public ResponseEntity<OutpostDTO> createOutpost(@RequestBody OutpostDTO outpostDTO) {
        OutpostDTO created = outpostService.createOutpost(outpostDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/getOutpostById/{id}")
    public ResponseEntity<OutpostDTO> getOutpostById(@PathVariable Long id) {
        return ResponseEntity.ok(outpostService.getOutpostById(id));
    }

    @GetMapping("/getAllOutposts")
    public ResponseEntity<List<OutpostDTO>> getAllOutposts() {
        return ResponseEntity.ok(outpostService.getAllOutposts());
    }

    @PutMapping("/updateOutpost/{id}")
    public ResponseEntity<OutpostDTO> updateOutpost(@PathVariable Long id, @RequestBody OutpostDTO outpostDTO) {
        return ResponseEntity.ok(outpostService.updateOutpost(id, outpostDTO));
    }

    @PostMapping("/conquerOutpost/{id}")
    public ResponseEntity<OutpostDTO> conquerOutpost(@PathVariable Long id, @RequestParam String team) {
        return ResponseEntity.ok(outpostService.conquerOutpost(id, team));
    }

    @DeleteMapping("/deleteOutpost/{id}")
    public ResponseEntity<Void> deleteOutpost(@PathVariable Long id) {
        outpostService.deleteOutpost(id);
        return ResponseEntity.noContent().build();
    }
}
