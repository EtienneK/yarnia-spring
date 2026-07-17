package com.etiennek.yarnia.party;

import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.etiennek.yarnia.party.Constants.PartyPhase;
import com.etiennek.yarnia.party.Entities.Party;
import com.etiennek.yarnia.party.Entities.PartyState;
import com.etiennek.yarnia.party.Entities.PartyMember;
import com.etiennek.yarnia.party.Entities.PartyJoinToken;
import com.etiennek.yarnia.party.ReqRes.AddBotRequest;
import com.etiennek.yarnia.party.ReqRes.AddMemberRequest;
import com.etiennek.yarnia.party.ReqRes.CreatePartyResponse;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotRequest;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotResponse;
import com.etiennek.yarnia.party.ReqRes.JoinPartyRequest;
import com.etiennek.yarnia.party.ReqRes.JoinPartyResponse;
import com.etiennek.yarnia.party.ReqRes.PartyMemberSnapshotResponse;
import com.etiennek.yarnia.party.ReqRes.RemoveMemberRequest;
import com.etiennek.yarnia.party.repos.PartyJoinTokenRepository;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;
import com.etiennek.yarnia.party.repos.PartyRepository;
import com.etiennek.yarnia.party.repos.PartyStateRepository;

import jakarta.validation.Valid;

@Service
@Validated
@Transactional
public class PartyService {
    private @Autowired SimpMessagingTemplate template;
    private @Autowired ApplicationEventPublisher eventPublisher;

    private @Autowired PartyRepository partyRepository;
    private @Autowired PartyStateRepository partyStateRepository;
    private @Autowired PartyMemberRepository partyMemberRepository;
    private @Autowired PartyJoinTokenRepository partyJoinTokenRepository;
    private @Autowired AddMemberService addMemberService;

    public CreatePartyResponse createParty() {
        final var playerId = UUID.randomUUID();

        String joinCode;
        var uniqueJoinCodeFound = false;
        var uniqueJoinCodeFoundRetries = 0;
        do {
            joinCode = generateJoinCode();
            uniqueJoinCodeFound = !partyRepository.existsByJoinCode(joinCode);
        } while (!uniqueJoinCodeFound && ++uniqueJoinCodeFoundRetries < 5);

        final var party = partyRepository.save(new Party(joinCode, 1));
        final var partyState = new PartyState(party.getId(), PartyPhase.WAITING);
        partyStateRepository.save(partyState);

        final var partyJoinToken = partyJoinTokenRepository.save(new PartyJoinToken(party.getId(), playerId));

        return new CreatePartyResponse(
                party.getId(),
                playerId,
                party.getJoinCode(),
                partyJoinToken.getId());
    }

    public JoinPartyResponse joinParty(@Valid JoinPartyRequest request) {
        final var party = partyRepository.findByJoinCode(request.getJoinCode()).orElseThrow();
        final var playerId = UUID.randomUUID();
        final var partyJoinToken = partyJoinTokenRepository.save(new PartyJoinToken(party.getId(), playerId));

        return new JoinPartyResponse(
                party.getId(),
                playerId,
                partyJoinToken.getId());
    }

    public void addBot(AddBotRequest request) {
        final var partyId = request.getPartyId();
        final var playerId = UUID.randomUUID();
        final var playerName = Utils.generateBotName();
        final var addMemberReq = new AddMemberRequest(partyId, playerId, playerName, true);
        addMemberReq.setBotPersona(Utils.generateBotPersona());
        addMemberService.addMember(addMemberReq);
    }

    public void removeMember(RemoveMemberRequest request) {
        final var partyId = request.getPartyId();
        final var playerId = request.getPlayerId();
        // Pessimistic lock
        final var partyState = partyStateRepository
                .findById(partyId)
                .orElseThrow(() -> new IllegalStateException("party state does not exist"));

        final var members = partyMemberRepository.findByPartyStateId(partyId);
        final var me = members.stream().filter(m -> m.getId().equals(playerId)).findFirst().orElseThrow();

        final var playing = partyState.getPartyPhase().equals(PartyPhase.PLAYING);

        if (playing) {
            // Keep the member around so they can reconnect and keep their score/submissions.
            partyMemberRepository.save(me.withConnected(false).withHost(false));
            eventPublisher.publishEvent(new PartyEvents.MemberDisconnectedEvent(partyId));
        } else {
            partyMemberRepository.delete(me);
            partyJoinTokenRepository.deleteByPartyIdAndPlayerId(partyId, playerId);
        }

        // Humans still present (during a game, disconnected humans don't count).
        final var remainingHumans = members.stream()
                .filter(m -> !m.isBot() && !m.getId().equals(playerId))
                .filter(m -> !playing || m.isConnected())
                .collect(Collectors.toList());

        if (remainingHumans.isEmpty()) {
            deleteParty(partyId);
            return;
        }

        if (me.isHost()) {
            partyMemberRepository.save(remainingHumans.get(0).withHost(true));
        }

        template.convertAndSend("/topic/party/" + partyId + "/snapshot",
                getPartySnapshot(new GetPartySnapshotRequest(partyId)));
    }

    private void deleteParty(UUID partyId) {
        // Let other modules (the game engine) clean up their state first.
        eventPublisher.publishEvent(new PartyEvents.PartyDeletedEvent(partyId));
        partyMemberRepository.deleteByPartyStateId(partyId);
        partyStateRepository.deleteById(partyId);
        partyJoinTokenRepository.deleteByPartyId(partyId);
        partyRepository.deleteById(partyId);
    }

    public GetPartySnapshotResponse getPartySnapshot(@Valid GetPartySnapshotRequest request) {
        final var partyState = partyStateRepository
                .findById(request.getPartyId())
                .orElseThrow(() -> new IllegalStateException("party state does not exist"));

        final var members = partyMemberRepository.findByPartyStateId(request.getPartyId());

        final var ret = new GetPartySnapshotResponse(partyState.getPartyPhase());
        for (PartyMember partyMember : members) {
            ret.getMembers().put(partyMember.getId(),
                    new PartyMemberSnapshotResponse(
                            partyMember.getName(),
                            partyMember.getColor(),
                            partyMember.isHost(),
                            partyMember.isReady(),
                            partyMember.isConnected(),
                            partyMember.isBot(),
                            request.getPartyId()));
        }

        return ret;
    }

    private static final int PARTY_CODE_LENGTH = 6;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private String generateJoinCode() {
        var code = "";
        for (int i = 0; i < PARTY_CODE_LENGTH; i++) {
            code += CODE_CHARS.charAt((int) Math.floor(Math.random() * CODE_CHARS.length()));
        }
        return code;
    }

    @Component
    public class CustomSpringEventListener implements ApplicationListener<SessionDisconnectEvent> {
        @Override
        public void onApplicationEvent(SessionDisconnectEvent event) {
            if (event.getUser() == null || event.getUser().getName() == null) {
                return;
            }
            final var partyAndPlayer = event.getUser().getName().split("\\|");
            removeMember(new RemoveMemberRequest(partyAndPlayer[0], partyAndPlayer[1]));
        }
    }

}
