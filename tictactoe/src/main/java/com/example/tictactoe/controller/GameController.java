package com.example.tictactoe.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.tictactoe.dto.CreateGameRequest;
import com.example.tictactoe.dto.GameResponse;
import com.example.tictactoe.dto.MakeMoveRequest;
import com.example.tictactoe.service.GameService;

import jakarta.validation.Valid;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    public ResponseEntity<GameResponse> createGame(@RequestBody CreateGameRequest request) {
        return ResponseEntity.ok(gameService.createGame(request.getGameMode()));
    }


    @GetMapping("/{gameId}")
    public ResponseEntity<GameResponse> getGame(@PathVariable Long gameId) {

        GameResponse response = gameService.getGameById(gameId);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/{gameId}/moves")
    public ResponseEntity<GameResponse> makeMove(
            @PathVariable Long gameId,
            @Valid @RequestBody MakeMoveRequest request
    ) {
        GameResponse response =
                gameService.makeMove(gameId, request.getCellIndex());
        return ResponseEntity.ok(response);
    }

}