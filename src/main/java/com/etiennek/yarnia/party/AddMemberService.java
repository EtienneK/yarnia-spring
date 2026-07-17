package com.etiennek.yarnia.party;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.etiennek.yarnia.party.Entities.PartyMember;
import com.etiennek.yarnia.party.ReqRes.AddMemberRequest;
import com.etiennek.yarnia.party.ReqRes.AddMemberResponse;
import com.etiennek.yarnia.party.repos.PartyMemberRepository;
import com.etiennek.yarnia.party.repos.PartyStateRepository;

import jakarta.transaction.Transactional;

@Service
@Validated
@Transactional
public class AddMemberService {
    private @Autowired PartyStateRepository partyStateRepository;
    private @Autowired PartyMemberRepository partyMemberRepository;

    public AddMemberResponse addMember(AddMemberRequest request) {
            final var partyId = request.getPartyId();
            final var playerId = request.getPlayerId();
            // Pessimistic lock
            final var partyState = partyStateRepository
                    .findById(partyId)
                    .orElseThrow(() -> new IllegalStateException("party state does not exist"));

            final var members = partyMemberRepository.findByPartyStateId(partyId);

            final var existing = members.stream().filter(m -> m.getId().equals(playerId)).findFirst();
            if (existing.isPresent()) {
                // Reconnect: keep name/host/ready/score, just mark connected again.
                partyMemberRepository.save(existing.get().withConnected(true));
                return new AddMemberResponse(true, null);
            }

            if (!partyState.getPartyPhase().equals(Constants.PartyPhase.WAITING)) {
                return new AddMemberResponse(false, "in_progress");
            }

            if (members.size() >= Constants.MAX_PARTY_SIZE) {
                return new AddMemberResponse(false, "full");
            }

            if (request.isBot() && members.stream().filter(m -> m.isBot()).count() >= Constants.MAX_BOTS_IN_PARTY) {
                return new AddMemberResponse(false, "full_bots");
            }

            final var playerName = request.getPlayerName() == null ? Utils.generatePlayerName() : request.getPlayerName();
            final var isHost = members.stream().filter(m -> m.isHost() && !m.getId().equals(playerId)).count() <= 0;
            final var takenColors = members.stream().map(PartyMember::getColor).toList();

            partyMemberRepository.save(new PartyMember(
                    playerId,
                    playerName,
                    Utils.pickPlayerColor(takenColors),
                    isHost,
                    request.isBot(),
                    true,
                    request.isBot(),
                    request.getBotPersona(),
                    0,
                    partyState));

            return new AddMemberResponse(true, null);
        }
}
