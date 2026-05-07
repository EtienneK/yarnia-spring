package com.etiennek.yarnia.party;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.etiennek.yarnia.party.Entities.Party;

public interface PartyRepository extends JpaRepository<Party, UUID> {
    boolean existsByJoinCode(String joinCode);
    Optional<Party> findByJoinCode(String joinCode);
}
