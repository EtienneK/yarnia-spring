package com.etiennek.yarnia.game.repos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.etiennek.yarnia.game.GameEntities.RoundVote;

public interface RoundVoteRepository extends JpaRepository<RoundVote, UUID> {
    List<RoundVote> findByPartyIdAndRoundNumber(UUID partyId, int roundNumber);

    Optional<RoundVote> findByPartyIdAndRoundNumberAndVoterId(UUID partyId, int roundNumber, UUID voterId);

    void deleteByPartyId(UUID partyId);
}
