package org.example.seniorlifebookingagent.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.embabel.agent.core.AgentPlatform;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.example.seniorlifebookingagent.agent.HospitalAppointmentAgent;
import org.example.seniorlifebookingagent.agent.TransportBookingAgent;
import org.example.seniorlifebookingagent.domain.hospital.ApprovedHospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalRequest;
import org.example.seniorlifebookingagent.domain.hospital.HospitalReservationCompleted;
import org.example.seniorlifebookingagent.domain.transport.TransportLeg;
import org.example.seniorlifebookingagent.domain.transport.TransportMode;
import org.example.seniorlifebookingagent.support.KoreanDateTime;
import org.example.seniorlifebookingagent.tool.HospitalTool;
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

    @Test
    void reSearchesWithOriginalConditionsAndRequiresApprovalForChangedAppointment() {
        var request = new HospitalRequest(
            "서울", "서울대병원", "정형외과", LocalDate.of(2026, 8, 26), "오전");
        var failed = appointment(request, 10, 30);
        var replacement = appointment(request, 11, 20);
        var hospitalAgent = mock(HospitalAppointmentAgent.class);
        var controller = new BookingController(
            mock(AgentPlatform.class), hospitalAgent, mock(TransportBookingAgent.class));
        var failedId = controller.remember(BookingController.BookingType.HOSPITAL, failed, request);
        when(hospitalAgent.reserve(any(ApprovedHospitalAppointment.class)))
            .thenThrow(new IllegalStateException("방금 마감되었습니다."));
        when(hospitalAgent.searchPage(request, 1, 10))
            .thenReturn(new HospitalTool.SearchPage(List.of(failed, replacement), false));

        var response = controller.approve(new BookingController.ApprovalRequest(failedId, true));

        assertTrue(response.reservationNumbers().isEmpty());
        assertEquals(1, response.retryPreview().options().size());
        var retry = response.retryPreview().options().getFirst();
        assertNotEquals(failedId, retry.id());
        assertTrue(retry.warning().contains("진료 시간"));
        assertTrue(retry.warning().contains(KoreanDateTime.format(failed.appointmentTime())));
        assertTrue(retry.warning().contains(KoreanDateTime.format(replacement.appointmentTime())));
        verify(hospitalAgent, times(1)).reserve(any(ApprovedHospitalAppointment.class));
        verify(hospitalAgent).searchPage(request, 1, 10);
    }

    @Test
    void reusesCompletedHospitalDestinationAndTimeForTransport() {
        var request = new HospitalRequest(
            "서울", "서울대병원", "정형외과", LocalDate.of(2026, 8, 26), "오전");
        var appointment = appointment(request, 10, 30);
        var hospitalAgent = mock(HospitalAppointmentAgent.class);
        var transportAgent = mock(TransportBookingAgent.class);
        var controller = new BookingController(
            mock(AgentPlatform.class), hospitalAgent, transportAgent);
        var id = controller.remember(BookingController.BookingType.HOSPITAL, appointment, request);
        when(hospitalAgent.reserve(any(ApprovedHospitalAppointment.class)))
            .thenReturn(new HospitalReservationCompleted("DEMO-H-1", appointment));

        var response = controller.approve(new BookingController.ApprovalRequest(id, true));

        assertEquals(appointment.address(), response.transportPrefill().destination());
        assertEquals(appointment.appointmentTime().toLocalDate(), response.transportPrefill().date());
        assertEquals(appointment.appointmentTime().toLocalTime(), response.transportPrefill().preferredArrivalTime());

    }

    private HospitalAppointment appointment(HospitalRequest request, int hour, int minute) {
        return new HospitalAppointment(
            request.hospitalName(), request.department(), request.date().atTime(hour, minute), "서울",
            30_000, LocalTime.of(9, 0), LocalTime.of(17, 30));
    }
}
