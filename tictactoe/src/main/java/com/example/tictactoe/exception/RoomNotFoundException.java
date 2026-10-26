package com.example.tictactoe.exception;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(String code) {
        super("No room with code " + code);
    }
}
