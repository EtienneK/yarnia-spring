package com.etiennek.yarnia.chat;

import java.util.UUID;

public final class ChatDtos {
    public record ChatMessageView(
            UUID id,
            UUID senderId,
            String senderName,
            String senderColor,
            boolean bot,
            String text,
            long at) {
    }
}
