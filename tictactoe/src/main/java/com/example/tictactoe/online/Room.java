package com.example.tictactoe.online;

import java.util.HashSet;
import java.util.Set;

import com.example.tictactoe.domain.Player;

/**
 * An online game between two people on different devices.
 * The player who creates the room is X; the player who joins with the code is O.
 */
public class Room {

    private final String code;
    private final String playerX;
    private String playerO;
    private String gameId;
    private Player lastStarter;
    private final Set<String> rematchVotes = new HashSet<>();

    Room(String code, String playerX) {
        this.code = code;
        this.playerX = playerX;
    }

    public String getCode() {
        return code;
    }

    public String getPlayerX() {
        return playerX;
    }

    public String getPlayerO() {
        return playerO;
    }

    void setPlayerO(String playerO) {
        this.playerO = playerO;
    }

    public String getGameId() {
        return gameId;
    }

    void setGameId(String gameId) {
        this.gameId = gameId;
    }

    Player getLastStarter() {
        return lastStarter;
    }

    void setLastStarter(Player lastStarter) {
        this.lastStarter = lastStarter;
    }

    Set<String> getRematchVotes() {
        return rematchVotes;
    }

    public boolean isFull() {
        return playerO != null;
    }

    /** X or O for a player in this room, or null if they're not in it. */
    public Player markOf(String playerId) {
        if (playerId.equals(playerX)) return Player.X;
        if (playerId.equals(playerO)) return Player.O;
        return null;
    }

    /** The other player in the room, or null if they haven't joined (or this player isn't in the room). */
    public String opponentOf(String playerId) {
        if (playerId.equals(playerX)) return playerO;
        if (playerId.equals(playerO)) return playerX;
        return null;
    }
}
