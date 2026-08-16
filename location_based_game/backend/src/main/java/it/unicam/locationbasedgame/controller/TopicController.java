package it.unicam.locationbasedgame.controller;

import it.unicam.locationbasedgame.dto.TopicDTO;
import it.unicam.locationbasedgame.service.interfaces.ITopicService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contains the calls to the database for the Topic entity.
 */
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final ITopicService topicService;

    @PostMapping("/createTopic")
    public ResponseEntity<TopicDTO> createTopic(@RequestBody TopicDTO topicDTO) {
        TopicDTO created = topicService.createTopic(topicDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/getTopicById/{id}")
    public ResponseEntity<TopicDTO> getTopicById(@PathVariable Long id) {
        return ResponseEntity.ok(topicService.getTopicById(id));
    }

    @GetMapping("/getAllTopics")
    public ResponseEntity<List<TopicDTO>> getAllTopics() {
        return ResponseEntity.ok(topicService.getAllTopics());
    }

    @PutMapping("/updateTopic/{id}")
    public ResponseEntity<TopicDTO> updateTopic(@PathVariable Long id, @RequestBody TopicDTO topicDTO) {
        return ResponseEntity.ok(topicService.updateTopic(id, topicDTO));
    }

    @DeleteMapping("/deleteTopic/{id}")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long id) {
        topicService.deleteTopic(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assignQuestions/{topicId}")
    public ResponseEntity<TopicDTO> assignQuestions(@PathVariable Long topicId, @RequestBody List<Long> questionIds) {
        return ResponseEntity.ok(topicService.assignQuestions(topicId, questionIds));
    }
}
