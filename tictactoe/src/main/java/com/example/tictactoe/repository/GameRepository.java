
package com.example.tictactoe.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.tictactoe.entity.Game;

public interface GameRepository extends JpaRepository<Game, Long> {
}
