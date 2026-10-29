package com.example.tictactoe.online;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.tictactoe.domain.Player;
import com.example.tictactoe.dto.GameResponse;

/**
 * The online game "protocol": turns a message from one player into the messages
 * each player should receive. Plain Java maps in and out, so it can be unit-tested
 * without a browser, a WebSocket or JSON.
 *
 * Messages from a player (field "type"):
 *   CREATE                 create a room and get a code
 *   JOIN   { code }        join a friend's room
 *   MOVE   { cellIndex }   play a move
 *   REMATCH                ask for another game after one ends
 *   LEAVE                  quit the room
 *   PING                   keep-alive so idle connections aren't closed (no reply)
 *
 * Messages sent to players:
 *   ROOM_CREATED      { code, you }        to the creator, while waiting for a friend
 *   GAME_STARTED      { code, you, game }  to both, for the first game and every rematch
 *   GAME_UPDATE       { game }             to both, after every move
 *   REMATCH_REQUESTED { by }               to the other player when someone asks for a rematch
 *   OPPONENT_LEFT                          when the other player quits or disconnects
 *   ERROR             { message }          to the sender only, e.g. "It's not your turn"
 */
@Component
public class OnlineGameCoordinator {

    /** One message to send to one player. */
    public record Outgoing(String playerId, Map<String, Object> message) {
    }

    private final RoomService rooms;

    public OnlineGameCoordinator(RoomService rooms) {
        this.rooms = rooms;
    }

    public List<Outgoing> handle(String playerId, Map<String, Object> message) {
        String type = String.valueOf(message.get("type"));
        try {
            return switch (type) {
                case "CREATE" -> create(playerId);
                case "JOIN" -> join(playerId, String.valueOf(message.get("code")));
                case "MOVE" -> move(playerId, message.get("cellIndex"));
                case "REMATCH" -> rematch(playerId);
                case "LEAVE" -> disconnected(playerId);
                case "PING" -> List.of();
                default -> List.of(error(playerId, "Unknown message type: " + type));
            };
        } catch (RuntimeException e) {
            // Rule violations (wrong turn, full room, bad code...) go back to the sender only
            return List.of(error(playerId, e.getMessage()));
        }
    }

    /** Called when a player's connection closes, whether they quit or lost signal. */
    public List<Outgoing> disconnected(String playerId) {
        Optional<Room> left = rooms.leave(playerId);
        String opponent = left.map(room -> room.opponentOf(playerId)).orElse(null);
        return opponent == null ? List.of() : List.of(new Outgoing(opponent, message("OPPONENT_LEFT")));
    }

    private List<Outgoing> create(String playerId) {
        List<Outgoing> out = new ArrayList<>(disconnected(playerId)); // leaving an old room tells that opponent
        Room room = rooms.createRoom(playerId);
        Map<String, Object> created = message("ROOM_CREATED");
        created.put("code", room.getCode());
        created.put("you", Player.X.name());
        out.add(new Outgoing(playerId, created));
        return out;
    }

    private List<Outgoing> join(String playerId, String code) {
        Room room = rooms.joinRoom(code, playerId);
        GameResponse game = rooms.currentGame(room);
        return gameStarted(room, game);
    }

    private List<Outgoing> move(String playerId, Object cellIndex) {
        if (!(cellIndex instanceof Number number) || number.intValue() < 0 || number.intValue() > 8) {
            return List.of(error(playerId, "cellIndex must be a number from 0 to 8"));
        }
        GameResponse game = rooms.makeMove(playerId, number.intValue());
        Room room = rooms.roomOf(playerId).orElseThrow();
        Map<String, Object> update = message("GAME_UPDATE");
        update.put("game", gameJson(game));
        return List.of(new Outgoing(room.getPlayerX(), update), new Outgoing(room.getPlayerO(), update));
    }

    private List<Outgoing> rematch(String playerId) {
        Room room = rooms.roomOf(playerId).orElseThrow(() -> new IllegalStateException("You're not in a room"));
        Optional<GameResponse> newGame = rooms.requestRematch(playerId);
        if (newGame.isPresent()) {
            return gameStarted(room, newGame.get());
        }
        Map<String, Object> asked = message("REMATCH_REQUESTED");
        asked.put("by", room.markOf(playerId).name());
        return List.of(new Outgoing(room.opponentOf(playerId), asked));
    }

    // Both players get the same game, each told which mark is theirs
    private List<Outgoing> gameStarted(Room room, GameResponse game) {
        List<Outgoing> out = new ArrayList<>();
        for (String player : List.of(room.getPlayerX(), room.getPlayerO())) {
            Map<String, Object> started = message("GAME_STARTED");
            started.put("code", room.getCode());
            started.put("you", room.markOf(player).name());
            started.put("game", gameJson(game));
            out.add(new Outgoing(player, started));
        }
        return out;
    }

    // Same shape as the REST API's game response, so the frontend can reuse its board code
    static Map<String, Object> gameJson(GameResponse game) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("gameId", game.getGameId());
        json.put("board", game.getBoard());
        json.put("currentPlayer", game.getCurrentPlayer() == null ? null : game.getCurrentPlayer().name());
        json.put("status", game.getStatus().name());
        json.put("difficulty", null);
        return json;
    }

    private static Map<String, Object> message(String type) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        return message;
    }

    private static Outgoing error(String playerId, String text) {
        Map<String, Object> error = message("ERROR");
        error.put("message", text);
        return new Outgoing(playerId, error);
    }
}
