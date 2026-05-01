package com.example.tictactoe.exception;

public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(Long gameId) {
        super("Game not found with id: " + gameId);
    }
}