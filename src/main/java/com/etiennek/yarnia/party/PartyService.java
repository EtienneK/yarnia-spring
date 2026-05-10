package com.etiennek.yarnia.party;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.etiennek.yarnia.party.Constants.PartyPhase;
import com.etiennek.yarnia.party.Entities.Party;
import com.etiennek.yarnia.party.Entities.PartyState;
import com.etiennek.yarnia.party.Entities.PartyMember;
import com.etiennek.yarnia.party.Entities.PartyJoinToken;
import com.etiennek.yarnia.party.ReqRes.ClosePartyRequest;
import com.etiennek.yarnia.party.ReqRes.CreatePartyRequest;
import com.etiennek.yarnia.party.ReqRes.CreatePartyResponse;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotRequest;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotResponse;
import com.etiennek.yarnia.party.ReqRes.JoinPartyRequest;
import com.etiennek.yarnia.party.ReqRes.JoinPartyResponse;
import com.etiennek.yarnia.party.ReqRes.PartyMemberSnapshotResponse;
import com.etiennek.yarnia.party.ReqRes.VerifyJoinRequest;
import com.etiennek.yarnia.party.ReqRes.VerifyJoinResponse;
import com.etiennek.yarnia.party.repos.PartyJoinTokenRepository;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;
import com.etiennek.yarnia.party.repos.PartyRepository;
import com.etiennek.yarnia.party.repos.PartyStateRepository;

import jakarta.validation.Valid;

@Service
@Validated
public class PartyService {

    private @Autowired PartyRepository partyRepository;
    private @Autowired PartyStateRepository partyStateRepository;
    private @Autowired PartyMemberRepository partyMemberRepository;
    private @Autowired PartyJoinTokenRepository partyJoinTokenRepository;

    public CreatePartyResponse createParty(@Valid CreatePartyRequest request) {
        final var playerName = request.getPlayerName() == null ? generatePlayerName() : request.getPlayerName();
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
        partyMemberRepository.save(new PartyMember(
                playerId,
                playerName,
                "#ff0000",
                true,
                false,
                true,
                partyState));

        final var partyJoinToken = partyJoinTokenRepository.save(new PartyJoinToken(party.getId(), playerId));

        return new CreatePartyResponse(
                party.getId(),
                playerId,
                playerName,
                party.getJoinCode(),
                partyJoinToken.getId());
    }

    public JoinPartyResponse joinParty(@Valid JoinPartyRequest request) {
        final var party = partyRepository.findByJoinCode(request.getJoinCode()).orElseThrow();

        final var playerName = request.getPlayerName();
        final var playerId = UUID.randomUUID();
        final var partyJoinToken = partyJoinTokenRepository.save(new PartyJoinToken(party.getId(), playerId));

        return new JoinPartyResponse(
                party.getId(),
                playerId,
                playerName == null ? generatePlayerName() : playerName,
                partyJoinToken.getId());
    }

    public VerifyJoinResponse verifyJoin(@Valid VerifyJoinRequest request) {
        final var allowed = partyJoinTokenRepository.existsByIdAndPartyIdAndPlayerId(
                request.getJoinToken(),
                request.getPartyId(),
                request.getPlayerId());
        return new VerifyJoinResponse(
                allowed);
    }

    public void updatePartySize() {
    }

    public void closeParty(@Valid ClosePartyRequest request) {
        partyRepository.deleteById(request.getPartyId());
        partyJoinTokenRepository.deleteByPartyId(request.getPartyId());
    }

    public GetPartySnapshotResponse getPartySnapshot(@Valid GetPartySnapshotRequest request) {
        final var partyState = partyStateRepository
                .findById(request.getPartyId())
                .orElseThrow(() -> new IllegalStateException("party state doesn't exist"));

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

    private String generatePlayerName() {
        return "Player#" + Integer.toString((int) Math.floor(Math.random() * 10000));
    }
}
