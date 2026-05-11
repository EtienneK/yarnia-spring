package com.etiennek.yarnia.party;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotRequest;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotResponse;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;

import lombok.Data;
import lombok.RequiredArgsConstructor;;

@Controller
@MessageMapping("/party/{partyId}")
public class PartyWsController {
    private static final String ALL = "/topic/party/{partyId}/snapshot";

    private @Autowired PartyService partyService;
    private @Autowired PartyMemberRepository partyMemberRepository;

    @MessageMapping("user-snapshot")
    // @SendToUser(destinations = "/queue/snapshot", broadcast = false)
    @SendTo(ALL)
    public GetPartySnapshotResponse getSnapshot(@DestinationVariable String partyId, StompHeaderAccessor headers) {
        checkAuth(partyId, headers);
        return partyService.getPartySnapshot(new GetPartySnapshotRequest(partyId));
    }

    @MessageMapping("setName")
    @SendTo(ALL)
    public GetPartySnapshotResponse setName(@DestinationVariable String partyId, String name,
            StompHeaderAccessor headers) {
        final var authRes = checkAuth(partyId, headers);
        final var partyMember = partyMemberRepository.findById(authRes.getPlayerId())
                .orElseThrow(() -> new IllegalStateException("party member not found"))
                .withName(name);
        partyMemberRepository.save(partyMember);
        return partyService.getPartySnapshot(new GetPartySnapshotRequest(partyId));
    }

    //
    //
    // https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-annotations.html#websocket-stomp-exception-handler
    //
    //
    // @MessageExceptionHandler
    // public ApplicationError handleException(MyException exception) {
    // // ...
    // return appError;
    // }

    @Data
    @RequiredArgsConstructor
    private class CheckAuthResponse {
        private final UUID joinToken;
        private final UUID playerId;
    }

    private CheckAuthResponse checkAuth(String partyId, StompHeaderAccessor headers) {
        final var joinToken = UUID.fromString((String) headers.getSessionAttributes().get(partyId + ".joinToken"));
        final var playerId = UUID.fromString((String) headers.getSessionAttributes().get(partyId + ".playerId"));
        if (joinToken == null || playerId == null) {
            throw new IllegalStateException("forbidden");
        }
        return new CheckAuthResponse(joinToken, playerId);
    }
}
