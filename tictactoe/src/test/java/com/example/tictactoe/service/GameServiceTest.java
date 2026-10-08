package com.example.tictactoe.service;
 
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
 
import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;
import com.example.tictactoe.dto.GameResponse;
import com.example.tictactoe.entity.Difficulty;
import com.example.tictactoe.entity.GameMode;
import com.example.tictactoe.exception.GameNotFoundException;
import com.example.tictactoe.exception.InvalidMoveException;
import com.example.tictactoe.repository.GameRepository;
 
/**
* Unit tests for GameService: game creation, move validation,
* win/draw detection and the minimax AI. No Spring context needed.
*/
class GameServiceTest {
 
    private GameService gameService;
 
    @BeforeEach
    void setUp() {
        gameService = new GameService(new GameRepository());
    }
 
    // ---------- Game creation ----------
 
    @Test
    void newGameHasEmptyBoardAndIsInProgress() {
        GameResponse game = gameService.createGame(GameMode.MULTI_PLAYER);
 
        assertEquals(Collections.nCopies(9, "_"), game.getBoard());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
    }
 
    @Test
    void singlePlayerGameAlwaysStartsWithX() {
        gameService.createGame(GameMode.SINGLE_PLAYER);
        GameResponse second = gameService.createGame(GameMode.SINGLE_PLAYER);
 
        assertEquals(Player.X, second.getCurrentPlayer());
    }
 
    @Test
    void multiPlayerStartingPlayerAlternatesBetweenGames() {
        GameResponse first = gameService.createGame(GameMode.MULTI_PLAYER);
        GameResponse second = gameService.createGame(GameMode.MULTI_PLAYER);
 
        assertNotEquals(first.getCurrentPlayer(), second.getCurrentPlayer());
    }
 
    @Test
    void gettingUnknownGameThrows() {
        assertThrows(GameNotFoundException.class, () -> gameService.getGameById("does-not-exist"));
    }
 
    // ---------- Difficulty ----------

    @Test
    void singlePlayerStoresChosenDifficulty() {
        GameResponse game = gameService.createGame(GameMode.SINGLE_PLAYER, Difficulty.EASY);

        assertEquals(Difficulty.EASY, game.getDifficulty());
        assertEquals(Difficulty.EASY, gameService.getGameById(game.getGameId()).getDifficulty());
    }

    @Test
    void singlePlayerDefaultsToImpossible() {
        GameResponse game = gameService.createGame(GameMode.SINGLE_PLAYER, null);

        assertEquals(Difficulty.IMPOSSIBLE, game.getDifficulty());
    }

    @Test
    void multiPlayerIgnoresDifficulty() {
        GameResponse game = gameService.createGame(GameMode.MULTI_PLAYER, Difficulty.HARD);

        assertNull(game.getDifficulty());
    }

    @Test
    void difficultyProbabilitiesMatchTheSpec() {
        assertEquals(0.2, Difficulty.EASY.getBestMoveProbability());
        assertEquals(0.5, Difficulty.MEDIUM.getBestMoveProbability());
        assertEquals(0.8, Difficulty.HARD.getBestMoveProbability());
        assertEquals(1.0, Difficulty.IMPOSSIBLE.getBestMoveProbability());
    }

    // ---------- Move validation ----------
 
    @Test
    void cannotPlayOnOccupiedCell() {
        String id = gameService.createGame(GameMode.MULTI_PLAYER).getGameId();
        gameService.makeMove(id, 4);
 
        assertThrows(InvalidMoveException.class, () -> gameService.makeMove(id, 4));
    }
 
    @Test
    void cannotPlayOutsideTheBoard() {
        String id = gameService.createGame(GameMode.MULTI_PLAYER).getGameId();
 
        assertThrows(InvalidMoveException.class, () -> gameService.makeMove(id, 9));
    }
 
    @Test
    void playersAlternateTurns() {
        GameResponse game = gameService.createGame(GameMode.MULTI_PLAYER);
        Player first = game.getCurrentPlayer();
 
        GameResponse after = gameService.makeMove(game.getGameId(), 0);
 
        assertNotEquals(first, after.getCurrentPlayer());
    }
 
    // ---------- Win and draw detection ----------
 
    @Test
    void completingARowWinsTheGame() {
        GameResponse game = gameService.createGame(GameMode.MULTI_PLAYER);
        String id = game.getGameId();
        Player starter = game.getCurrentPlayer();
 
        // Starter takes the top row (0,1,2); opponent plays 3,4
        GameResponse result = play(id, 0, 3, 1, 4, 2);
 
        assertEquals(starter == Player.X ? GameStatus.X_WON : GameStatus.O_WON, result.getStatus());
        assertNull(result.getCurrentPlayer());
    }
 
    @Test
    void completingADiagonalWinsTheGame() {
        GameResponse game = gameService.createGame(GameMode.MULTI_PLAYER);
        Player starter = game.getCurrentPlayer();
 
        GameResponse result = play(game.getGameId(), 0, 1, 4, 2, 8);
 
        assertEquals(starter == Player.X ? GameStatus.X_WON : GameStatus.O_WON, result.getStatus());
    }
 
    @Test
    void fullBoardWithNoLineIsADraw() {
        String id = gameService.createGame(GameMode.MULTI_PLAYER).getGameId();
 
        // Final board (S = starter, T = other):
        //  S | T | S
        //  S | T | T
        //  T | S | S
        GameResponse result = play(id, 0, 1, 2, 4, 3, 5, 7, 6, 8);
 
        assertEquals(GameStatus.DRAW, result.getStatus());
    }
 
    @Test
    void noMovesAllowedAfterGameEnds() {
        String id = gameService.createGame(GameMode.MULTI_PLAYER).getGameId();
        play(id, 0, 3, 1, 4, 2);
 
        assertThrows(InvalidMoveException.class, () -> gameService.makeMove(id, 8));
    }
 
    // ---------- Minimax AI ----------
 
    @Test
    void aiRepliesImmediatelyInSinglePlayer() {
        String id = gameService.createGame(GameMode.SINGLE_PLAYER).getGameId();
 
        GameResponse after = gameService.makeMove(id, 0);
 
        assertEquals(1, Collections.frequency(after.getBoard(), "O"));
        assertEquals(Player.X, after.getCurrentPlayer());
    }
 
    @Test
    void aiBlocksAnImmediateWin() {
        String id = gameService.createGame(GameMode.SINGLE_PLAYER).getGameId();
        // X plays the centre; minimax answers with a corner
        GameResponse board = gameService.makeMove(id, 4);
        int aiCorner = board.getBoard().indexOf("O");
 
        // X threatens a line through the centre; AI must block the opposite cell
        int x = (aiCorner == 0) ? 2 : 0;
        int block = 8 - x;
        board = gameService.makeMove(id, x);
 
        assertEquals("O", board.getBoard().get(block));
    }
 
    @Test
    void aiNeverLosesAgainstAnyOpponent() {
        // Exhaustively try every possible sequence of X moves
        assertAiNeverLoses(List.of());
    }

    @Test
    void movingInUnknownGameThrowsGameNotGound() {
        assertThrows(GameNotFoundException.class, () -> gameService.makeMove("does-not-exist", 0));
    }
 
    // ---------- Difficulty-based AI ----------

    /** A Random that always returns the same values, so the AI's choice is predictable. */
    private static Random fixedRandom(double nextDouble) {
        return new Random() {
            @Override
            public double nextDouble() {
                return nextDouble;
            }

            @Override
            public int nextInt(int bound) {
                return bound - 1; // always the last empty cell
            }
        };
    }

    @Test
    void aiPlaysBestMoveWhenRollIsUnderProbability() {
        GameService service = new GameService(new GameRepository(), fixedRandom(0.0));
        String id = service.createGame(GameMode.SINGLE_PLAYER, Difficulty.EASY).getGameId();

        GameResponse board = service.makeMove(id, 4);

        // Against a centre opening, minimax answers with the first corner
        assertEquals("O", board.getBoard().get(0));
    }

    @Test
    void aiPlaysRandomMoveWhenRollIsOverProbability() {
        GameService service = new GameService(new GameRepository(), fixedRandom(0.99));
        String id = service.createGame(GameMode.SINGLE_PLAYER, Difficulty.EASY).getGameId();

        GameResponse board = service.makeMove(id, 4);

        // Random path picks the last empty cell (8), not the minimax corner (0)
        assertEquals("O", board.getBoard().get(8));
        assertEquals("_", board.getBoard().get(0));
    }

    @Test
    void impossibleAlwaysPlaysBestMoveEvenWithHighRoll() {
        GameService service = new GameService(new GameRepository(), fixedRandom(0.99));
        String id = service.createGame(GameMode.SINGLE_PLAYER, Difficulty.IMPOSSIBLE).getGameId();

        GameResponse board = service.makeMove(id, 4);

        assertEquals("O", board.getBoard().get(0));
    }

    @Test
    void easyPlaysBestMoveLessOftenThanHard() {
        double easy = bestReplyRate(Difficulty.EASY);
        double hard = bestReplyRate(Difficulty.HARD);

        // Expected about 0.30 for EASY and 0.83 for HARD (random moves can also hit the best cell)
        assertTrue(easy > 0.2 && easy < 0.45, "EASY rate was " + easy);
        assertTrue(hard > 0.75 && hard < 0.9, "HARD rate was " + hard);
    }

    /** Plays 1000 games opening in the centre and returns how often the AI found the best reply. */
    private double bestReplyRate(Difficulty difficulty) {
        GameService service = new GameService(new GameRepository(), new Random(42));
        int best = 0;
        int games = 1000;
        for (int i = 0; i < games; i++) {
            String id = service.createGame(GameMode.SINGLE_PLAYER, difficulty).getGameId();
            if (service.makeMove(id, 4).getBoard().get(0).equals("O")) {
                best++;
            }
        }
        return (double) best / games;
    }

    // ---------- Helpers ----------
 
    private GameResponse play(String gameId, int... cells) {
        GameResponse response = null;
        for (int cell : cells) {
            response = gameService.makeMove(gameId, cell);
        }
        return response;
    }
 
    private void assertAiNeverLoses(List<Integer> xMoves) {
        GameService fresh = new GameService(new GameRepository());
        String id = fresh.createGame(GameMode.SINGLE_PLAYER).getGameId();
        GameResponse board = fresh.getGameById(id);
        for (int cell : xMoves) {
            board = fresh.makeMove(id, cell);
        }
 
        assertNotEquals(GameStatus.X_WON, board.getStatus(), "AI lost after X moves " + xMoves);
        if (board.getStatus() != GameStatus.IN_PROGRESS) return;
 
        for (int cell = 0; cell < 9; cell++) {
            if (board.getBoard().get(cell).equals("_")) {
                List<Integer> next = new java.util.ArrayList<>(xMoves);
                next.add(cell);
                assertAiNeverLoses(next);
            }
        }
    }
}