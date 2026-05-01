package com.example.tictactoe.dto;

import java.util.List;

import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;

public class GameResponse {

    private Long gameId;
    private List<String> board;
    private Player currentPlayer;
    private GameStatus status;

    public GameResponse(Long gameId, List<String> board, Player currentPlayer, GameStatus status) {
        this.gameId = gameId;
        this.board = board;
        this.currentPlayer = currentPlayer;
        this.status = status;
    }

    public Long getGameId() {
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
}