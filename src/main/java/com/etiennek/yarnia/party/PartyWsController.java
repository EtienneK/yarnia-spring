package com.etiennek.yarnia.party;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotRequest;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotResponse;;

@Controller
public class PartyWsController {
    private @Autowired PartyService partyService;

    @MessageMapping("/party/{partyId}/snapshot")
    @SendToUser(destinations = "/queue/snapshot", broadcast = false)
    public GetPartySnapshotResponse getSnapshot(@DestinationVariable String partyId) {
        System.out.println("WTF?????????????");
        return partyService.getPartySnapshot(new GetPartySnapshotRequest(partyId));
    }
}
