package com.etiennek.yarnia.game.repos;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.etiennek.yarnia.game.GameEntities.StorySegment;

public interface StorySegmentRepository extends JpaRepository<StorySegment, UUID> {
    List<StorySegment> findByPartyIdOrderByPositionAsc(UUID partyId);

    void deleteByPartyId(UUID partyId);
}
