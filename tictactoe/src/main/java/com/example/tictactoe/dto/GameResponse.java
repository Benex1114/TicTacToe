package com.example.tictactoe.dto;

import java.util.List;

import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;
import com.example.tictactoe.entity.Difficulty;

public class GameResponse {

    private String gameId;
    private List<String> board;
    private Player currentPlayer;
    private GameStatus status;
    private Difficulty difficulty;

    public GameResponse(String gameId, List<String> board, Player currentPlayer, GameStatus status, Difficulty difficulty) {
        this.gameId = gameId;
        this.board = board;
        this.currentPlayer = currentPlayer;
        this.status = status;
        this.difficulty = difficulty;
    }

    public String getGameId() {
        return gameId;
    }

    public List<String> getBoard() {
        return board;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public GameStatus getStatus() {
        return status;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }
}