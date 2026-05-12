package com.etiennek.yarnia.party;

import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
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
        addMemberReq.setBotPersona("You are a witty British man."); // TODO
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
        final var membersNotMeNotBots = members.stream().filter(m -> !m.isBot() && !m.getId().equals(playerId)).collect(Collectors.toList());
        final var notMeAndBotCount = membersNotMeNotBots.size();

        if (partyState.getPartyPhase().equals(PartyPhase.PLAYING)) {
            partyMemberRepository.save(me.withConnected(false).withHost(false));
        } else {
            if (notMeAndBotCount == 0) {
                partyMemberRepository.deleteByPartyStateId(partyId);
                partyStateRepository.deleteById(partyId);
                partyJoinTokenRepository.deleteByPartyId(partyId);
                partyRepository.deleteById(partyId);
                return;
            } else {
                partyMemberRepository.delete(me);
                partyJoinTokenRepository.deleteByPartyId(partyId);
            }
        }

        if (me.isHost() && notMeAndBotCount > 0) {
            // find a new host
            for (var i = 0; i < notMeAndBotCount; i++) {
                final var member = membersNotMeNotBots.get(i);
                if (i == 0) partyMemberRepository.save(member.withHost(true));
                else partyMemberRepository.save(member.withHost(false));
            }
        }

        template.convertAndSend("/topic/party/" + partyId + "/snapshot",
                getPartySnapshot(new GetPartySnapshotRequest(partyId)));
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
