package com.etiennek.yarnia.party;

import com.etiennek.yarnia.party.repos.PartyStateRepository;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import com.etiennek.yarnia.party.Constants.PartyPhase;
import com.etiennek.yarnia.party.Entities.PartyMember;
import com.etiennek.yarnia.party.ReqRes.AddBotRequest;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotRequest;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotResponse;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;

import lombok.Data;
import lombok.RequiredArgsConstructor;;

@Transactional
@Controller
@MessageMapping("/party/{partyId}")
public class PartyWsController {
    private final PartyStateRepository partyStateRepository;

    private static final String ALL = "/topic/party/{partyId}/snapshot";

    private @Autowired PartyService partyService;
    private @Autowired PartyMemberRepository partyMemberRepository;
    private @Autowired org.springframework.context.ApplicationEventPublisher eventPublisher;

    PartyWsController(PartyStateRepository partyStateRepository) {
        this.partyStateRepository = partyStateRepository;
    }

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
        return snapshot(partyId);
    }

    @MessageMapping("setReady")
    @SendTo(ALL)
    public GetPartySnapshotResponse setReady(
            @DestinationVariable String partyId,
            StompHeaderAccessor headers,
            boolean ready) {
        final var partyMember = partyMember(partyId, headers);
        partyMemberRepository.save(partyMember.withReady(ready));
        return snapshot(partyId);
    }

    @MessageMapping("startGame")
    @SendTo(ALL)
    public GetPartySnapshotResponse startGame(
            @DestinationVariable String partyId,
            StompHeaderAccessor headers,
            boolean _body) {
        final var authRes = checkAuth(partyId, headers);
        if (!partyMemberRepository.existsByIdAndIsHost(authRes.getPlayerId(), true)) {
            return null;
        }
        UUID partyIdUuid;
        try {
            partyIdUuid = UUID.fromString(partyId);
        } catch (Exception e) {
            return null;
        }

        final var partyState = partyStateRepository.findById(partyIdUuid)
            .orElseThrow(() -> new IllegalStateException("party state not found"));

        if (!partyState.getPartyPhase().equals(PartyPhase.WAITING)) {
            return null;
        }

        final var partyMembers = partyMemberRepository.findByPartyStateId(partyIdUuid);

        if (partyMembers.size() < Constants.MIN_PARTY_SIZE
            || partyMembers.stream().filter(m -> !m.isReady()).findAny().isPresent()) {
            return null;
        }

        partyStateRepository.save(partyState.withPartyPhase(PartyPhase.PLAYING));
        eventPublisher.publishEvent(new PartyEvents.GameStartedEvent(partyIdUuid));

        return snapshot(partyId);
    }

    @MessageMapping("addBot")
    @SendTo(ALL)
    public GetPartySnapshotResponse addBot(
            @DestinationVariable String partyId,
            StompHeaderAccessor headers,
            boolean body) {
        final var authRes = checkAuth(partyId, headers);
        if (!partyMemberRepository.existsByIdAndIsHost(authRes.getPlayerId(), true)) {
            return null;
        }
        partyService.addBot(new AddBotRequest(partyId));
        return snapshot(partyId);
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

    private GetPartySnapshotResponse snapshot(String partyId) {
        return partyService.getPartySnapshot(new GetPartySnapshotRequest(partyId));
    }

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
