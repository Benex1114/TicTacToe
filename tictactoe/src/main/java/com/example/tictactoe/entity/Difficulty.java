package com.example.tictactoe.entity;

/**
 * AI difficulty for single-player games.
 * Each level is the probability that the AI plays the best (minimax) move
 * instead of a random empty cell.
 */
public enum Difficulty {
    EASY(0.2),
    MEDIUM(0.5),
    HARD(0.8),
    IMPOSSIBLE(1.0);

    private final double bestMoveProbability;

    Difficulty(double bestMoveProbability) {
        this.bestMoveProbability = bestMoveProbability;
    }

    public double getBestMoveProbability() {
        return bestMoveProbability;
    }
}
