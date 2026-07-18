package com.etiennek.yarnia.party;

import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import com.etiennek.yarnia.party.ReqRes.CreatePartyResponse;
import com.etiennek.yarnia.party.ReqRes.JoinPartyRequest;
import com.etiennek.yarnia.party.ReqRes.JoinPartyResponse;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/party")
public class PartyController {
    private @Autowired PartyService partyService;

    @PostMapping("create")
    public CreatePartyResponse createParty(
            @RequestBody(required = false) ReqRes.CreatePartyRequest request) {
        return partyService.createParty(request != null && request.isPublicGame());
    }

    @PostMapping("join")
    public ResponseEntity<JoinPartyResponse> joinParty(@Valid @RequestBody JoinPartyRequest request) {
        try {
            return ResponseEntity.ok(partyService.joinParty(request));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Matchmaking: join any open public game. 404 when none are available. */
    @PostMapping("join-public")
    public ResponseEntity<JoinPartyResponse> joinPublicParty() {
        try {
            return ResponseEntity.ok(partyService.joinPublicParty());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

}
