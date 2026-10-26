package com.example.tictactoe.exception;

public class RoomFullException extends RuntimeException {
    public RoomFullException(String code) {
        super("Room " + code + " already has two players");
    }
}
