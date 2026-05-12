package com.etiennek.yarnia.party.repos;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.etiennek.yarnia.party.Entities.PartyMember;

import jakarta.persistence.LockModeType;

public interface PartyMemberRepository extends JpaRepository<PartyMember, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PartyMember> findById(UUID id);

    boolean existsByIdAndIsHost(UUID id, boolean isHost);

    public Set<PartyMember> findByPartyStateId(UUID partyId);

    void deleteByPartyStateId(UUID partyId);
}
