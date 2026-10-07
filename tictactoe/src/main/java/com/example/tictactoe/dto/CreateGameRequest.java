package com.example.tictactoe.dto;

import com.example.tictactoe.entity.Difficulty;
import com.example.tictactoe.entity.GameMode;

public class CreateGameRequest {
    
    private GameMode gameMode;

    private Difficulty difficulty;

    public GameMode getGameMode() {
        return gameMode;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

}
