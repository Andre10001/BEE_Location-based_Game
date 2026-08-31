package org.unicam.intermediate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unicam.intermediate.models.dto.Response;
import org.unicam.intermediate.service.environmental.EnvironmentMapService;
import java.util.List;

@RestController
@RequestMapping("/api/environment/maps")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*")
@Slf4j
public class EnvironmentMapController {

    private final EnvironmentMapService environmentMapService;

    @GetMapping
    public ResponseEntity<Response<List<String>>> listMaps() {
        try {
            return ResponseEntity.ok(Response.ok(environmentMapService.listMapNames()));
        } catch (Exception e) {
            log.error("[Environment Maps API] Failed to list maps", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to list maps: " + e.getMessage()));
        }
    }

    @GetMapping("/{name}")
    public ResponseEntity<Response<JsonNode>> getMap(@PathVariable String name) {
        try {
            JsonNode map = environmentMapService.readMap(name);
            if (map == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Response.error("Map not found: " + name));
            }
            return ResponseEntity.ok(Response.ok(map));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Response.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[Environment Maps API] Failed to read map: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to read map: " + e.getMessage()));
        }
    }
    
    @GetMapping("/views/{viewReference}/places")
    public ResponseEntity<Response<List<String>>> getPlacesInView(@PathVariable String viewReference) {
        try {
            return ResponseEntity.ok(Response.ok(environmentMapService.getPlaceIdsInView(viewReference)));
        } catch (Exception e) {
            log.error("[Environment Maps API] Failed to read view: {}", viewReference, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to read view: " + e.getMessage()));
        }
    }
    
    @PostMapping("/{name}/deploy")
    public ResponseEntity<Response<String>> deployMap(@PathVariable String name) {
        try {
            String outcome = environmentMapService.deployMap(name);
            return ResponseEntity.ok(Response.ok(outcome));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Response.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[Environment Maps API] Failed to deploy map: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to deploy map: " + e.getMessage()));
        }
    }

    @PostMapping("/{name}")
    public ResponseEntity<Response<String>> saveMap(@PathVariable String name,
                                                    @RequestBody JsonNode model) {
        try {
            environmentMapService.saveMap(name, model);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Response.ok("Map saved as " + name + ".json"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Response.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[Environment Maps API] Failed to save map: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Response.error("Failed to save map: " + e.getMessage()));
        }
    }
}