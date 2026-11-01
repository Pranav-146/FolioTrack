package com.foliotrack.web;

import com.foliotrack.dto.AskRequest;
import com.foliotrack.dto.AskResponse;
import com.foliotrack.service.AskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/ask")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    // POST rather than GET: the question is free text that may be long, and each call costs LLM requests.
    // 200 (not 201): nothing is created, this returns a computed answer.
    @PostMapping
    public AskResponse ask(@PathVariable Long userId, @Valid @RequestBody AskRequest request) {
        return askService.ask(userId, request.question());
    }
}
