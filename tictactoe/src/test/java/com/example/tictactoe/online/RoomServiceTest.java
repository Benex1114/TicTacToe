package com.example.tictactoe.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;
import com.example.tictactoe.dto.GameResponse;
import com.example.tictactoe.exception.InvalidMoveException;
import com.example.tictactoe.exception.RoomFullException;
import com.example.tictactoe.exception.RoomNotFoundException;
import com.example.tictactoe.repository.GameRepository;
import com.example.tictactoe.service.GameService;

/**
 * Unit tests for online rooms: creating and joining with a code, whose turn it is,
 * rematches and leaving. Plain Java, no WebSockets or Spring needed.
 */
class RoomServiceTest {

    private GameService gameService;
    private RoomService rooms;

    @BeforeEach
    void setUp() {
        gameService = new GameService(new GameRepository());
        rooms = new RoomService(gameService, new Random(1));
    }

    /** Creates a room for "alice" and lets "bob" join, so a game is running. */
    private Room startedRoom() {
        Room room = rooms.createRoom("alice");
        return rooms.joinRoom(room.getCode(), "bob");
    }

    /** The player whose turn it is in the room right now. */
    private String playerToMove(Room room) {
        Player turn = gameService.getGameById(room.getGameId()).getCurrentPlayer();
        return turn == Player.X ? room.getPlayerX() : room.getPlayerO();
    }

    // ---------- Creating and joining ----------

    @Test
    void creatorPlaysXAndWaitsForAFriend() {
        Room room = rooms.createRoom("alice");

        assertEquals(Player.X, room.markOf("alice"));
        assertFalse(room.isFull());
        assertNull(room.getGameId());
    }

    @Test
    void roomCodesAreFourEasyToReadCharacters() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = rooms.createRoom("player" + i).getCode();
            assertTrue(code.matches("[A-HJ-NP-Z2-9]{4}"), "unexpected code " + code);
            codes.add(code);
        }
        assertEquals(200, codes.size(), "every open room should have its own code");
    }

    @Test
    void joiningWithTheCodeMakesYouOAndStartsTheGame() {
        Room room = rooms.createRoom("alice");

        Room joined = rooms.joinRoom(room.getCode(), "bob");

        assertEquals(Player.O, joined.markOf("bob"));
        assertTrue(joined.isFull());
        assertEquals(GameStatus.IN_PROGRESS, gameService.getGameById(joined.getGameId()).getStatus());
    }

    @Test
    void codesAreNotCaseSensitive() {
        Room room = rooms.createRoom("alice");

        Room joined = rooms.joinRoom("  " + room.getCode().toLowerCase() + " ", "bob");

        assertEquals(room.getCode(), joined.getCode());
    }

    @Test
    void joiningAnUnknownCodeFails() {
        assertThrows(RoomNotFoundException.class, () -> rooms.joinRoom("ZZZZ", "bob"));
    }

    @Test
    void aThirdPlayerCannotJoin() {
        Room room = startedRoom();

        assertThrows(RoomFullException.class, () -> rooms.joinRoom(room.getCode(), "carol"));
    }

    @Test
    void youCannotJoinYourOwnRoom() {
        Room room = rooms.createRoom("alice");

        assertThrows(RoomFullException.class, () -> rooms.joinRoom(room.getCode(), "alice"));
    }

    // ---------- Turns ----------

    @Test
    void theFirstGameStartsWithX() {
        Room room = startedRoom();

        assertEquals(Player.X, gameService.getGameById(room.getGameId()).getCurrentPlayer());
    }

    @Test
    void onlyThePlayerWhoseTurnItIsCanMove() {
        Room room = startedRoom(); // X (alice) to move

        assertThrows(InvalidMoveException.class, () -> rooms.makeMove("bob", 4));

        GameResponse afterAlice = rooms.makeMove("alice", 4);
        assertEquals("X", afterAlice.getBoard().get(4));
        assertThrows(InvalidMoveException.class, () -> rooms.makeMove("alice", 0));

        GameResponse afterBob = rooms.makeMove("bob", 0);
        assertEquals("O", afterBob.getBoard().get(0));
    }

    @Test
    void youCannotMoveBeforeYourFriendJoins() {
        rooms.createRoom("alice");

        assertThrows(InvalidMoveException.class, () -> rooms.makeMove("alice", 4));
    }

    @Test
    void someoneOutsideTheRoomCannotMove() {
        startedRoom();

        assertThrows(InvalidMoveException.class, () -> rooms.makeMove("mallory", 4));
    }

    @Test
    void aFullGameCanBePlayedToAWin() {
        Room room = startedRoom();
        // alice (X) takes the top row, bob (O) plays the middle row
        rooms.makeMove("alice", 0);
        rooms.makeMove("bob", 3);
        rooms.makeMove("alice", 1);
        rooms.makeMove("bob", 4);
        GameResponse result = rooms.makeMove("alice", 2);

        assertEquals(GameStatus.X_WON, result.getStatus());
        assertThrows(InvalidMoveException.class, () -> rooms.makeMove("bob", 5));
        assertEquals(room.getGameId(), result.getGameId());
    }

    // ---------- Rematch ----------

    private void finishGame(Room room) {
        // Whoever starts takes the top row while the other plays the middle row
        String first = playerToMove(room);
        String second = room.opponentOf(first);
        rooms.makeMove(first, 0);
        rooms.makeMove(second, 3);
        rooms.makeMove(first, 1);
        rooms.makeMove(second, 4);
        rooms.makeMove(first, 2);
    }

    @Test
    void rematchStartsOnlyWhenBothPlayersAsk() {
        Room room = startedRoom();
        String firstGame = room.getGameId();
        finishGame(room);

        Optional<GameResponse> afterAlice = rooms.requestRematch("alice");
        assertTrue(afterAlice.isEmpty(), "should wait for bob");

        Optional<GameResponse> afterBob = rooms.requestRematch("bob");
        assertTrue(afterBob.isPresent());
        assertNotEquals(firstGame, afterBob.get().getGameId());
        assertEquals(GameStatus.IN_PROGRESS, afterBob.get().getStatus());
    }

    @Test
    void theStartingPlayerAlternatesBetweenGames() {
        Room room = startedRoom();
        assertEquals(Player.X, gameService.getGameById(room.getGameId()).getCurrentPlayer());

        finishGame(room);
        rooms.requestRematch("alice");
        GameResponse second = rooms.requestRematch("bob").orElseThrow();
        assertEquals(Player.O, second.getCurrentPlayer());

        finishGame(room);
        rooms.requestRematch("bob");
        GameResponse third = rooms.requestRematch("alice").orElseThrow();
        assertEquals(Player.X, third.getCurrentPlayer());
    }

    @Test
    void rematchIsNotAllowedWhileTheGameIsStillGoing() {
        startedRoom();

        assertThrows(InvalidMoveException.class, () -> rooms.requestRematch("alice"));
    }

    @Test
    void askingForARematchTwiceDoesNotCountAsBothPlayers() {
        Room room = startedRoom();
        finishGame(room);

        rooms.requestRematch("alice");

        assertTrue(rooms.requestRematch("alice").isEmpty());
    }

    // ---------- Leaving ----------

    @Test
    void leavingClosesTheRoomAndTellsYouWhoWasLeft() {
        Room room = startedRoom();

        Optional<Room> left = rooms.leave("alice");

        assertTrue(left.isPresent());
        assertEquals("bob", left.get().opponentOf("alice"));
        assertEquals(0, rooms.roomCount());
        assertTrue(rooms.roomOf("bob").isEmpty(), "bob is no longer in a room");
        assertThrows(RoomNotFoundException.class, () -> rooms.joinRoom(room.getCode(), "carol"));
    }

    @Test
    void leavingWhenNotInARoomDoesNothing() {
        assertTrue(rooms.leave("nobody").isEmpty());
    }

    @Test
    void creatingANewRoomLeavesYourOldOne() {
        Room first = rooms.createRoom("alice");

        Room second = rooms.createRoom("alice");

        assertEquals(1, rooms.roomCount());
        assertEquals(second.getCode(), rooms.roomOf("alice").orElseThrow().getCode());
        assertThrows(RoomNotFoundException.class, () -> rooms.joinRoom(first.getCode(), "bob"));
    }

    @Test
    void roomsAreIndependent() {
        Room one = startedRoom();
        Room two = rooms.joinRoom(rooms.createRoom("carol").getCode(), "dave");

        rooms.makeMove("alice", 4);

        assertEquals("_", gameService.getGameById(two.getGameId()).getBoard().get(4));
        assertEquals("X", gameService.getGameById(one.getGameId()).getBoard().get(4));
    }
}
