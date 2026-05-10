package com.etiennek.yarnia.party;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.etiennek.yarnia.party.Entities.PartyState;

public interface PartyStateRepository extends JpaRepository<PartyState, UUID> {
}
