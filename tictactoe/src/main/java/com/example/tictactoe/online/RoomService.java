package com.example.tictactoe.online;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;
import com.example.tictactoe.dto.GameResponse;
import com.example.tictactoe.exception.InvalidMoveException;
import com.example.tictactoe.exception.RoomFullException;
import com.example.tictactoe.exception.RoomNotFoundException;
import com.example.tictactoe.service.GameService;

/**
 * Rooms for online two-player games: create a room, share its code, the friend joins, then play.
 *
 * This class only holds the rules (who can join, whose turn it is, rematches, leaving).
 * It knows nothing about WebSockets, so it can be unit-tested on its own; the WebSocket layer
 * just turns messages into these method calls and sends the results to both players.
 *
 * Methods are synchronized so two moves arriving at the same moment are handled one at a time.
 */
@Service
public class RoomService {

    // No 0/O or 1/I/L, so codes are easy to read out and type
    private static final String CODE_CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;

    private final GameService gameService;
    private final Random random;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomOfPlayer = new ConcurrentHashMap<>();

    @Autowired
    public RoomService(GameService gameService) {
        this(gameService, new Random());
    }

    RoomService(GameService gameService, Random random) {
        this.gameService = gameService;
        this.random = random;
    }

    /** Creates a room with a new code. The creator plays X and waits for a friend to join. */
    public synchronized Room createRoom(String playerId) {
        leave(playerId); // a player can only be in one room at a time
        String code;
        do {
            code = randomCode();
        } while (rooms.containsKey(code));

        Room room = new Room(code, playerId);
        rooms.put(code, room);
        roomOfPlayer.put(playerId, code);
        return room;
    }

    /** Joins a friend's room as O and starts the first game. */
    public synchronized Room joinRoom(String code, String playerId) {
        Room room = rooms.get(normalise(code));
        if (room == null) {
            throw new RoomNotFoundException(normalise(code));
        }
        if (room.isFull() || playerId.equals(room.getPlayerX())) {
            throw new RoomFullException(room.getCode());
        }
        leave(playerId);
        room.setPlayerO(playerId);
        roomOfPlayer.put(playerId, room.getCode());
        startNewGame(room);
        return room;
    }

    /** Plays a move for this player. Only the player whose turn it is may move. */
    public synchronized GameResponse makeMove(String playerId, int cellIndex) {
        Room room = roomOf(playerId).orElseThrow(() -> new InvalidMoveException("You're not in a room"));
        if (room.getGameId() == null) {
            throw new InvalidMoveException("Waiting for your friend to join");
        }
        GameResponse game = gameService.getGameById(room.getGameId());
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new InvalidMoveException("Game already finished");
        }
        if (game.getCurrentPlayer() != room.markOf(playerId)) {
            throw new InvalidMoveException("It's not your turn");
        }
        return gameService.makeMove(room.getGameId(), cellIndex);
    }

    /**
     * Asks for a rematch after a game ends. Returns the new game once both players have asked,
     * or empty while still waiting for the other player.
     */
    public synchronized Optional<GameResponse> requestRematch(String playerId) {
        Room room = roomOf(playerId).orElseThrow(() -> new InvalidMoveException("You're not in a room"));
        if (room.getGameId() == null || gameService.getGameById(room.getGameId()).getStatus() == GameStatus.IN_PROGRESS) {
            throw new InvalidMoveException("The current game isn't over yet");
        }
        room.getRematchVotes().add(playerId);
        if (room.getRematchVotes().size() < 2) {
            return Optional.empty();
        }
        return Optional.of(startNewGame(room));
    }

    /**
     * Removes a player from their room (they quit or their connection dropped) and closes the room.
     * Returns the room they left, so the other player can be told; empty if they weren't in one.
     */
    public synchronized Optional<Room> leave(String playerId) {
        String code = roomOfPlayer.remove(playerId);
        if (code == null) {
            return Optional.empty();
        }
        Room room = rooms.remove(code);
        if (room != null) {
            String opponent = room.opponentOf(playerId);
            if (opponent != null) {
                roomOfPlayer.remove(opponent);
            }
        }
        return Optional.ofNullable(room);
    }

    public Optional<Room> roomOf(String playerId) {
        String code = roomOfPlayer.get(playerId);
        return code == null ? Optional.empty() : Optional.ofNullable(rooms.get(code));
    }

    /** The game currently being played in a room. */
    public GameResponse currentGame(Room room) {
        if (room.getGameId() == null) {
            throw new InvalidMoveException("Waiting for your friend to join");
        }
        return gameService.getGameById(room.getGameId());
    }

    /** Number of open rooms (used by tests and could be shown on a status page). */
    public int roomCount() {
        return rooms.size();
    }

    // Each new game in a room starts with the other player: X first, then O, then X...
    private GameResponse startNewGame(Room room) {
        Player starter = room.getLastStarter() == Player.X ? Player.O : Player.X;
        room.setLastStarter(starter);
        room.getRematchVotes().clear();
        GameResponse game = gameService.createOnlineGame(starter);
        room.setGameId(game.getGameId());
        return game;
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
        }
        return code.toString();
    }

    // People type codes in lowercase or with spaces; accept " ab3k " as "AB3K"
    private static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
