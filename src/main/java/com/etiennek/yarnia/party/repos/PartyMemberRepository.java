package com.etiennek.yarnia.party.repos;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.etiennek.yarnia.party.Entities.PartyMember;

public interface PartyMemberRepository extends JpaRepository<PartyMember, UUID> {
    public Set<PartyMember> findByPartyStateId(UUID partyId);
}
