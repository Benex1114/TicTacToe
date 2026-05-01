package com.example.tictactoe.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Player;
import com.example.tictactoe.dto.GameResponse;
import com.example.tictactoe.entity.Game;
import com.example.tictactoe.entity.GameMode;
import com.example.tictactoe.exception.GameNotFoundException;
import com.example.tictactoe.exception.InvalidMoveException;
import com.example.tictactoe.repository.GameRepository;

@Service
public class GameService {

    private final GameRepository gameRepository;
    private Player lastStartingPlayer = null;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    //Create Game
    public GameResponse createGame(GameMode gameMode) {
        
        Player starter = lastStartingPlayer == null || lastStartingPlayer == Player.O ? Player.X : Player.O;

        Game game = new Game();
        game.setBoardState("_,_,_,_,_,_,_,_,_");
        game.setStartingPlayer(starter);
        game.setCurrentPlayer(starter);
        game.setStatus(GameStatus.IN_PROGRESS);
        game.setGameMode(gameMode != null ? gameMode : GameMode.MULTI_PLAYER);
        if(game.getGameMode()==GameMode.MULTI_PLAYER) {lastStartingPlayer = starter;}
        game.setCreatedAt(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());

        Game savedGame = gameRepository.save(game);

        List<String> board =
                Arrays.asList(savedGame.getBoardState().split(","));

        return new GameResponse(
                savedGame.getId(),
                board,
                savedGame.getCurrentPlayer(),
                savedGame.getStatus()
        );
        
    }

    //Get Game by Id
    public GameResponse getGameById(Long gameId) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));

        List<String> board =
                Arrays.asList(game.getBoardState().split(","));

        return new GameResponse(
                game.getId(),
                board,
                game.getCurrentPlayer(),
                game.getStatus()
        );
    }

    //Winning condition checker
    private boolean hasPlayerWon(String[] board, Player player) {

    String p = player.name();    

    int[][] winPatterns = {
            {0, 1, 2},
            {3, 4, 5},
            {6, 7, 8},
            {0, 3, 6},
            {1, 4, 7},
            {2, 5, 8},
            {0, 4, 8},
            {2, 4, 6}
    };

    for (int[] pattern : winPatterns) {
        if (board[pattern[0]].equals(p) &&
            board[pattern[1]].equals(p) &&
            board[pattern[2]].equals(p)) {
            return true;
        }
    }
    return false;
    }

    //Checking Draw condition
    private boolean isDraw(String[] board) {
    for (String cell : board) {
        if (cell.equals("_")) {
            return false;
        }
    }
    return true;
    }

    //Make Move
    public GameResponse makeMove(Long gameId, int cellIndex) {

    Game game = gameRepository.findById(gameId)
            .orElseThrow(() ->
                    new RuntimeException("Game not found with id: " + gameId));

    if (!game.getStatus().equals(GameStatus.IN_PROGRESS)) {
        throw new InvalidMoveException("Game already finished");
    }

    String[] board = game.getBoardState().split(",");

    if (cellIndex < 0 || cellIndex > 8) {
        throw new InvalidMoveException("Invalid cell index");
    }

    if (!board[cellIndex].equals("_")) {
        throw new InvalidMoveException("Cell already occupied");
    }

    Player currentPlayer = game.getCurrentPlayer();
    board[cellIndex] = currentPlayer.toString(); //patching

    // Check win
    if (hasPlayerWon(board, currentPlayer)) {
        game.setStatus(currentPlayer == Player.X ? GameStatus.X_WON : GameStatus.O_WON);
        game.setCurrentPlayer(null);
    }
    // Check draw
    else if (isDraw(board)) {
        game.setStatus(GameStatus.DRAW);
        game.setCurrentPlayer(null);
    }
    // Continue game
    else {
        game.setCurrentPlayer(currentPlayer == Player.X ? Player.O : Player.X);
    }

    game.setBoardState(String.join(",", board));

    if (game.getGameMode() == GameMode.SINGLE_PLAYER &&
        game.getStatus() == GameStatus.IN_PROGRESS &&
        game.getCurrentPlayer() == Player.O) {

        makeAiMove(game);
    }

    Game savedGame = gameRepository.save(game);

    return new GameResponse(
            savedGame.getId(),
            Arrays.asList(savedGame.getBoardState().split(",")),
            savedGame.getCurrentPlayer(),
            savedGame.getStatus()
    );
    }

    private void makeAiMove(Game game) {
    String[] board = game.getBoardState().split(",");

    int bestMove = findBestMove(board);
    board[bestMove] = Player.O.name();

    if (hasPlayerWon(board, Player.O)) {
        game.setStatus(GameStatus.O_WON);
        game.setCurrentPlayer(null);
    } else if (isDraw(board)) {
        game.setStatus(GameStatus.DRAW);
        game.setCurrentPlayer(null);
    } else {
        game.setCurrentPlayer(Player.X);
    }

    game.setBoardState(String.join(",", board));
    }

        private int findBestMove(String[] board) {
        int bestScore = Integer.MIN_VALUE;
        int move = -1;

        for (int i = 0; i < 9; i++) {
            if (board[i].equals("_")) {
                board[i] = Player.O.name();
                int score = minimax(board, false);
                board[i] = "_";

                if (score > bestScore) {
                    bestScore = score;
                    move = i;
                }
            }
        }
        return move;
    }

    private int minimax(String[] board, boolean isMaximizing) {

    if (hasPlayerWon(board, Player.O)) return 10;
    if (hasPlayerWon(board, Player.X)) return -10;
    if (isDraw(board)) return 0;

    if (isMaximizing) {
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < 9; i++) {
            if (board[i].equals("_")) {
                board[i] = Player.O.name();
                best = Math.max(best, minimax(board, false));
                board[i] = "_";
            }
        }
        return best;
    } else {
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (board[i].equals("_")) {
                board[i] = Player.X.name();
                best = Math.min(best, minimax(board, true));
                board[i] = "_";
            }
        }
        return best;
    }
}

}