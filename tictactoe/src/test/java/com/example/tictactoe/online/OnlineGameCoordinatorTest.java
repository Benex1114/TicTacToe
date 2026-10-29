package com.example.tictactoe.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.tictactoe.repository.GameRepository;
import com.example.tictactoe.service.GameService;

/**
 * Tests the online message protocol between two pretend players, "alice" and "bob",
 * exactly as the WebSocket handler would use it (minus the JSON and network).
 */
class OnlineGameCoordinatorTest {

    private OnlineGameCoordinator coordinator;

    @BeforeEach
    void setUp() {
        GameService games = new GameService(new GameRepository());
        coordinator = new OnlineGameCoordinator(new RoomService(games, new Random(1)));
    }

    private List<OnlineGameCoordinator.Outgoing> send(String player, Object... keyValues) {
        Map<String, Object> message = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            message.put((String) keyValues[i], keyValues[i + 1]);
        }
        return coordinator.handle(player, message);
    }

    /** The single message sent to a player, failing if there isn't exactly one. */
    private static Map<String, Object> to(String player, List<OnlineGameCoordinator.Outgoing> out) {
        List<Map<String, Object>> messages = out.stream().filter(o -> o.playerId().equals(player)).map(OnlineGameCoordinator.Outgoing::message).toList();
        assertEquals(1, messages.size(), "expected one message for " + player + " but got " + messages);
        return messages.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> game(Map<String, Object> message) {
        return (Map<String, Object>) message.get("game");
    }

    /** alice creates a room and bob joins it; returns the room code. */
    private String startGame() {
        String code = (String) to("alice", send("alice", "type", "CREATE")).get("code");
        send("bob", "type", "JOIN", "code", code);
        return code;
    }

    @Test
    void createSendsTheCodeToTheCreatorOnly() {
        List<OnlineGameCoordinator.Outgoing> out = send("alice", "type", "CREATE");

        Map<String, Object> created = to("alice", out);
        assertEquals("ROOM_CREATED", created.get("type"));
        assertEquals("X", created.get("you"));
        assertTrue(((String) created.get("code")).matches("[A-Z2-9]{4}"));
        assertEquals(1, out.size());
    }

    @Test
    void joiningStartsTheGameForBothPlayersWithTheirOwnMark() {
        String code = (String) to("alice", send("alice", "type", "CREATE")).get("code");

        List<OnlineGameCoordinator.Outgoing> out = send("bob", "type", "JOIN", "code", code.toLowerCase());

        Map<String, Object> forAlice = to("alice", out);
        Map<String, Object> forBob = to("bob", out);
        assertEquals("GAME_STARTED", forAlice.get("type"));
        assertEquals("X", forAlice.get("you"));
        assertEquals("O", forBob.get("you"));
        assertEquals(game(forAlice).get("gameId"), game(forBob).get("gameId"));
        assertEquals("X", game(forAlice).get("currentPlayer"));
        assertEquals("IN_PROGRESS", game(forAlice).get("status"));
    }

    @Test
    void aMoveIsSentToBothPlayers() {
        startGame();

        List<OnlineGameCoordinator.Outgoing> out = send("alice", "type", "MOVE", "cellIndex", 4);

        for (String player : List.of("alice", "bob")) {
            Map<String, Object> update = to(player, out);
            assertEquals("GAME_UPDATE", update.get("type"));
            assertEquals("X", ((List<?>) game(update).get("board")).get(4));
            assertEquals("O", game(update).get("currentPlayer"));
        }
    }

    @Test
    void movingOutOfTurnOnlyTellsTheSender() {
        startGame();

        List<OnlineGameCoordinator.Outgoing> out = send("bob", "type", "MOVE", "cellIndex", 4);

        Map<String, Object> error = to("bob", out);
        assertEquals("ERROR", error.get("type"));
        assertEquals("It's not your turn", error.get("message"));
        assertEquals(1, out.size());
    }

    @Test
    void badCellIndexIsRejected() {
        startGame();

        assertEquals("ERROR", to("alice", send("alice", "type", "MOVE", "cellIndex", 9)).get("type"));
        assertEquals("ERROR", to("alice", send("alice", "type", "MOVE", "cellIndex", "four")).get("type"));
        assertEquals("ERROR", to("alice", send("alice", "type", "MOVE")).get("type"));
    }

    @Test
    void joiningAWrongCodeGivesAClearError() {
        Map<String, Object> error = to("bob", send("bob", "type", "JOIN", "code", "ZZZZ"));

        assertEquals("ERROR", error.get("type"));
        assertEquals("No room with code ZZZZ", error.get("message"));
    }

    @Test
    void pingKeepsTheConnectionAliveWithoutAReply() {
        startGame();

        assertTrue(send("alice", "type", "PING").isEmpty());
    }

    @Test
    void unknownMessagesGetAnError() {
        assertEquals("ERROR", to("alice", send("alice", "type", "DANCE")).get("type"));
        assertEquals("ERROR", to("alice", send("alice")).get("type"));
    }

    @Test
    void rematchWaitsForBothThenStartsANewGameForBoth() {
        startGame();
        // alice (X) wins along the top row
        send("alice", "type", "MOVE", "cellIndex", 0);
        send("bob", "type", "MOVE", "cellIndex", 3);
        send("alice", "type", "MOVE", "cellIndex", 1);
        send("bob", "type", "MOVE", "cellIndex", 4);
        Map<String, Object> last = to("bob", send("alice", "type", "MOVE", "cellIndex", 2));
        assertEquals("X_WON", game(last).get("status"));

        Map<String, Object> asked = to("alice", send("bob", "type", "REMATCH"));
        assertEquals("REMATCH_REQUESTED", asked.get("type"));
        assertEquals("O", asked.get("by"));

        List<OnlineGameCoordinator.Outgoing> started = send("alice", "type", "REMATCH");
        assertEquals("GAME_STARTED", to("alice", started).get("type"));
        assertEquals("GAME_STARTED", to("bob", started).get("type"));
        assertEquals("O", game(to("bob", started)).get("currentPlayer"), "O starts the second game");
    }

    @Test
    void whenAPlayerDisconnectsTheOtherIsTold() {
        startGame();

        List<OnlineGameCoordinator.Outgoing> out = coordinator.disconnected("alice");

        assertEquals("OPPONENT_LEFT", to("bob", out).get("type"));
        assertEquals(1, out.size());
    }

    @Test
    void leavingWorksTheSameAsDisconnecting() {
        startGame();

        assertEquals("OPPONENT_LEFT", to("alice", send("bob", "type", "LEAVE")).get("type"));
    }

    @Test
    void disconnectingWhileWaitingTellsNobody() {
        send("alice", "type", "CREATE");

        assertTrue(coordinator.disconnected("alice").isEmpty());
    }

    @Test
    void creatingANewRoomMidGameTellsTheOldOpponent() {
        startGame();

        List<OnlineGameCoordinator.Outgoing> out = send("alice", "type", "CREATE");

        assertEquals("OPPONENT_LEFT", to("bob", out).get("type"));
        assertEquals("ROOM_CREATED", to("alice", out).get("type"));
    }
}
