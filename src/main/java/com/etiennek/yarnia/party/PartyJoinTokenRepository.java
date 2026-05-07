package com.etiennek.yarnia.party;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.etiennek.yarnia.party.Entities.PartyJoinToken;

public interface PartyJoinTokenRepository extends JpaRepository<PartyJoinToken, UUID> {
    boolean existsByIdAndPartyIdAndPlayerId(UUID id, UUID partyId, UUID playerId);
    void deleteByPartyId(UUID partyId);
}
