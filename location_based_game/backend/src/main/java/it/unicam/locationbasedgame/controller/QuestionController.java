package it.unicam.locationbasedgame.controller;

import it.unicam.locationbasedgame.dto.QuestionDTO;
import it.unicam.locationbasedgame.service.interfaces.IQuestionService;
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
 * Contains the calls to the database for the Question entity.
 */
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final IQuestionService questionService;

    @PostMapping("/createQuestion")
    public ResponseEntity<QuestionDTO> createQuestion(@RequestBody QuestionDTO questionDTO) {
        QuestionDTO created = questionService.createQuestion(questionDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/getQuestionById/{id}")
    public ResponseEntity<QuestionDTO> getQuestionById(@PathVariable Long id) {
        return ResponseEntity.ok(questionService.getQuestionById(id));
    }

    @GetMapping("/getAllQuestions")
    public ResponseEntity<List<QuestionDTO>> getAllQuestions(@RequestParam(required = false) Integer difficulty) {
        if (difficulty != null) {
            return ResponseEntity.ok(questionService.getQuestionsByDifficulty(difficulty));
        }
        return ResponseEntity.ok(questionService.getAllQuestions());
    }

    @PutMapping("/updateQuestion/{id}")
    public ResponseEntity<QuestionDTO> updateQuestion(@PathVariable Long id, @RequestBody QuestionDTO questionDTO) {
        return ResponseEntity.ok(questionService.updateQuestion(id, questionDTO));
    }

    @DeleteMapping("/deleteQuestion/{id}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }
}
