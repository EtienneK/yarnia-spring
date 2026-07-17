package com.etiennek.yarnia.game.repos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.etiennek.yarnia.game.GameEntities.RoundSubmission;

public interface RoundSubmissionRepository extends JpaRepository<RoundSubmission, UUID> {
    List<RoundSubmission> findByPartyIdAndRoundNumber(UUID partyId, int roundNumber);

    Optional<RoundSubmission> findByPartyIdAndRoundNumberAndPlayerId(UUID partyId, int roundNumber, UUID playerId);

    void deleteByPartyId(UUID partyId);
}
