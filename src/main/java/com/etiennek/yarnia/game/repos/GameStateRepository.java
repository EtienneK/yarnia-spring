package com.etiennek.yarnia.game.repos;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.etiennek.yarnia.game.GameEntities.GameState;

import jakarta.persistence.LockModeType;

public interface GameStateRepository extends JpaRepository<GameState, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GameState> findById(UUID id);
}
