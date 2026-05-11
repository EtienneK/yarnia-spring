package com.etiennek.yarnia.party.repos;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.etiennek.yarnia.party.Entities.PartyState;

import jakarta.persistence.LockModeType;

public interface PartyStateRepository extends JpaRepository<PartyState, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PartyState> findById(UUID id);
}
