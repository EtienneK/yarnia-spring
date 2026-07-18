package com.etiennek.yarnia.chat;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Controller
@MessageMapping("/party/{partyId}")
public class ChatWsController {
    private @Autowired ChatService chatService;
    private @Autowired SimpMessagingTemplate template;

    @MessageMapping("chat")
    public void chat(@DestinationVariable String partyId, StompHeaderAccessor headers, String text) {
        final var auth = checkAuth(partyId, headers);
        chatService.send(auth.partyId(), auth.playerId(), text);
    }

    @MessageMapping("chat-history")
    public void chatHistory(@DestinationVariable String partyId, StompHeaderAccessor headers, String _body) {
        final var auth = checkAuth(partyId, headers);
        // Per-player reply topic so history isn't rebroadcast to the whole party.
        template.convertAndSend(
                "/topic/party/" + partyId + "/chat-history-" + auth.playerId(),
                chatService.history(auth.partyId()));
    }

    private record AuthResult(UUID partyId, UUID playerId) {
    }

    private AuthResult checkAuth(String partyId, StompHeaderAccessor headers) {
        final var joinToken = (String) headers.getSessionAttributes().get(partyId + ".joinToken");
        final var playerId = (String) headers.getSessionAttributes().get(partyId + ".playerId");
        if (joinToken == null || playerId == null) {
            throw new IllegalStateException("forbidden");
        }
        return new AuthResult(UUID.fromString(partyId), UUID.fromString(playerId));
    }
}
