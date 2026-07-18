package com.etiennek.yarnia.chat.repos;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.etiennek.yarnia.chat.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findTop100ByPartyIdOrderByCreatedAtDesc(UUID partyId);

    List<ChatMessage> findTop12ByPartyIdOrderByCreatedAtDesc(UUID partyId);

    void deleteByPartyId(UUID partyId);
}
