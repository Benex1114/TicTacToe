
package com.example.tictactoe.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.example.tictactoe.entity.Game;

@Repository
public class GameRepository {

    private final Map<String, Game> games = new ConcurrentHashMap<>();

    public Game save(Game game) {
        games.put(game.getId(), game);
        return game;
    }

    public Optional<Game> findById(String id) {
        return Optional.ofNullable(games.get(id));
    }

    public List<Game> findAll() {
        return new ArrayList<>(games.values());
    }
}