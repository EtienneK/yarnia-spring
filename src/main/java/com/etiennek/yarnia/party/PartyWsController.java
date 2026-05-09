package com.etiennek.yarnia.party;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotRequest;
import com.etiennek.yarnia.party.ReqRes.GetPartySnapshotResponse;;

@Controller
@MessageMapping("/party/{partyId}")
public class PartyWsController {
    private @Autowired PartyService partyService;

    @MessageMapping("snapshot")
    @SendToUser(destinations = "/queue/snapshot", broadcast = false)
    public GetPartySnapshotResponse getSnapshot(@DestinationVariable String partyId, StompHeaderAccessor headers) {
        checkAuth(partyId, headers);
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

    private void checkAuth(String partyId, StompHeaderAccessor headers) {
        final var joinToken = headers.getSessionAttributes().get(partyId + ".joinToken");
        final var playerId = headers.getSessionAttributes().get(partyId + ".playerId");
        if (joinToken == null || playerId == null) {
            throw new IllegalStateException("forbidden");
        }
    }
}
