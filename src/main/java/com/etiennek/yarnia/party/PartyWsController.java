package com.etiennek.yarnia.party;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import com.etiennek.yarnia.party.Entities.PartyMember;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotRequest;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotResponse;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;

import lombok.Data;
import lombok.RequiredArgsConstructor;;

@Transactional
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
    public GetPartySnapshotResponse setName(
            @DestinationVariable String partyId,
            StompHeaderAccessor headers,
            String name) {
        if (name == null || name.trim().length() == 0) {
            name = Utils.generatePlayerName();
        }
        name = name.trim();

        if (name.length() > Constants.MAX_NAME_LENGTH) {
            name = name.substring(0, Constants.MAX_NAME_LENGTH);
        }

        final var partyMember = partyMember(partyId, headers).withName(name.trim());
        partyMemberRepository.save(partyMember);
        return partyService.getPartySnapshot(new GetPartySnapshotRequest(partyId));
    }

    @MessageMapping("setReady")
    @SendTo(ALL)
    public GetPartySnapshotResponse setReady(
            @DestinationVariable String partyId,
            StompHeaderAccessor headers,
            boolean ready) {
        final var partyMember = partyMember(partyId, headers);
        partyMemberRepository.save(partyMember.withReady(ready));
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

    private PartyMember partyMember(String partyId, StompHeaderAccessor headers) {
        final var authRes = checkAuth(partyId, headers);
        return partyMemberRepository.findById(authRes.getPlayerId())
                .orElseThrow(() -> new IllegalStateException("party member not found"));
    }

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
