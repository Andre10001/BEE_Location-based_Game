package org.unicam.intermediate.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unicam.intermediate.models.dto.Response;
import org.unicam.intermediate.service.environmental.BpmnProcessService;

import java.util.List;

@RestController
@RequestMapping("/api/environment/processes")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*")
@Slf4j
public class BpmnProcessController {

    private final BpmnProcessService bpmnProcessService;

    @GetMapping
    public ResponseEntity<Response<List<String>>> listProcesses() {
        try {
            return ResponseEntity.ok(Response.ok(bpmnProcessService.listProcessNames()));
        } catch (Exception e) {
            log.error("[BPMN Processes API] Failed to list processes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to list processes: " + e.getMessage()));
        }
    }
}
