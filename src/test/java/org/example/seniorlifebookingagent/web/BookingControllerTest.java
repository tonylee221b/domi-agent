package org.example.seniorlifebookingagent.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.embabel.agent.core.AgentPlatform;
import java.util.UUID;
import org.example.seniorlifebookingagent.agent.HospitalAppointmentAgent;
import org.example.seniorlifebookingagent.agent.TransportBookingAgent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BookingControllerTest {

    @Test
    void rejectsReservationWithoutExplicitApproval() {
        var hospitalAgent = mock(HospitalAppointmentAgent.class);
        var transportAgent = mock(TransportBookingAgent.class);
        var controller = new BookingController(mock(AgentPlatform.class), hospitalAgent, transportAgent);

        var exception = assertThrows(ResponseStatusException.class,
            () -> controller.approve(new BookingController.ApprovalRequest(UUID.randomUUID(), false)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(hospitalAgent, transportAgent);
    }
}
