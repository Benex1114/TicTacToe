package com.example.tictactoe.dto;

import com.example.tictactoe.entity.GameMode;

public class CreateGameRequest {
    
    private GameMode gameMode;

    public GameMode getGameMode() {
        return gameMode;
    }

}
