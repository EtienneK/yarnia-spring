package com.etiennek.yarnia.game;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import com.etiennek.yarnia.game.GameDtos.GameSnapshot;

/**
 * In-game messages. submit/vote don't use @SendTo because GameService
 * broadcasts the updated snapshot itself (the same path timers and bots use).
 */
@Transactional
@Controller
@MessageMapping("/party/{partyId}/game")
public class GameWsController {
    private @Autowired GameService gameService;

    @MessageMapping("snapshot")
    @SendTo("/topic/party/{partyId}/game")
    public GameSnapshot snapshot(@DestinationVariable String partyId, StompHeaderAccessor headers) {
        final var auth = checkAuth(partyId, headers);
        return gameService.snapshot(auth.partyId());
    }

    @MessageMapping("submit")
    public void submit(@DestinationVariable String partyId, StompHeaderAccessor headers, String text) {
        final var auth = checkAuth(partyId, headers);
        gameService.submit(auth.partyId(), auth.playerId(), text);
    }

    @MessageMapping("vote")
    public void vote(@DestinationVariable String partyId, StompHeaderAccessor headers, String submissionId) {
        final var auth = checkAuth(partyId, headers);
        final UUID submissionUuid;
        try {
            submissionUuid = UUID.fromString(submissionId.trim().replace("\"", ""));
        } catch (Exception e) {
            return;
        }
        gameService.vote(auth.partyId(), auth.playerId(), submissionUuid);
    }

    @MessageMapping("playAgain")
    public void playAgain(@DestinationVariable String partyId, StompHeaderAccessor headers, boolean _body) {
        final var auth = checkAuth(partyId, headers);
        gameService.playAgain(auth.partyId(), auth.playerId());
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
