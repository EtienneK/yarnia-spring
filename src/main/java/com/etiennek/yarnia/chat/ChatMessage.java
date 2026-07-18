package com.etiennek.yarnia.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
@RequiredArgsConstructor
@Table(indexes = {
        @Index(columnList = "partyId, createdAt"),
})
public class ChatMessage {
    @Id
    private final UUID id;

    @Column(nullable = false)
    private final UUID partyId;

    @Column(nullable = false)
    private final UUID senderId;

    @Column(nullable = false, length = 500)
    private final String text;

    @Column(nullable = false)
    private final Instant createdAt;
}
