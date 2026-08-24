package org.example.seniorlifebookingagent.web;

import com.embabel.agent.api.common.AgentPlatformTypedOps;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.example.seniorlifebookingagent.agent.HospitalAppointmentAgent;
import org.example.seniorlifebookingagent.agent.PolicyGuideAgent.PolicyAnswer;
import org.example.seniorlifebookingagent.agent.TransportBookingAgent;
import org.example.seniorlifebookingagent.domain.hospital.ApprovedHospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalRequest;
import org.example.seniorlifebookingagent.domain.transport.ApprovedTransportPlan;
import org.example.seniorlifebookingagent.domain.transport.TransportMode;
import org.example.seniorlifebookingagent.domain.transport.TransportOptions;
import org.example.seniorlifebookingagent.domain.transport.TransportPlan;
import org.example.seniorlifebookingagent.domain.transport.TransportRequest;
import org.example.seniorlifebookingagent.domain.transport.TransportRecommendations;
import org.example.seniorlifebookingagent.support.KoreanDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final AgentPlatformTypedOps agents;
    private final HospitalAppointmentAgent hospitalAgent;
    private final TransportBookingAgent transportAgent;
    // ponytail: demo-only in-memory state; replace with expiring persistence when multiple servers or real bookings exist.
    private final ConcurrentHashMap<UUID, PendingBooking> pendingBookings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, HospitalRequest> hospitalSearches = new ConcurrentHashMap<>();

    public BookingController(
        AgentPlatform agentPlatform,
        HospitalAppointmentAgent hospitalAgent,
        TransportBookingAgent transportAgent
    ) {
        this.agents = new AgentPlatformTypedOps(agentPlatform);
        this.hospitalAgent = hospitalAgent;
        this.transportAgent = transportAgent;
    }

    @PostMapping("/preview")
    public PreviewResponse preview(@Valid @RequestBody PreviewRequest request) {
        return switch (request.type()) {
            case HOSPITAL -> previewHospital(request.message());
            case TRANSPORT -> previewTransport(request.message());
        };
    }

    @PostMapping("/question")
    public PolicyAnswer question(@Valid @RequestBody QuestionRequest request) {
        return agents.<UserInput, PolicyAnswer>asFunction(PolicyAnswer.class)
            .apply(new UserInput(request.question()), ProcessOptions.DEFAULT);
    }

    @PostMapping("/approve")
    public ReservationResponse approve(@Valid @RequestBody ApprovalRequest request) {
        if (!request.approved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "예약과 결제에 동의해야 진행할 수 있습니다.");
        }
        var pending = pendingBookings.remove(request.previewId());
        if (pending == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "추천 일정이 만료되었습니다. 다시 찾아주세요.");
        }

        return switch (pending.type()) {
            case HOSPITAL -> {
                try {
                    var completed = hospitalAgent.reserve(new ApprovedHospitalAppointment(
                        (HospitalAppointment) pending.plan()));
                    var appointment = completed.appointment();
                    yield new ReservationResponse(
                        "병원 예약이 완료됐어요.",
                        List.of(completed.reservationNumber()),
                        null,
                        new TransportPrefill(
                            appointment.address(),
                            appointment.appointmentTime().toLocalDate(),
                            appointment.appointmentTime().toLocalTime())
                    );
                } catch (IllegalStateException reservationFailure) {
                    yield new ReservationResponse(
                        "선택한 예약이 방금 마감되어 같은 조건으로 다시 찾았습니다.",
                        List.of(),
                        retryHospital(pending),
                        null
                    );
                }
            }
            case TRANSPORT -> {
                var completed = transportAgent.reserve(new ApprovedTransportPlan((TransportPlan) pending.plan()));
                yield new ReservationResponse(
                    "교통편 예약이 완료됐어요.", List.of(completed.reservationNumber()), null, null);
            }
        };
    }

    @PostMapping("/preview/hospital-page")
    public PreviewResponse hospitalPage(@Valid @RequestBody HospitalPageRequest request) {
        var search = hospitalSearches.get(request.searchId());
        if (search == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "병원 검색이 만료되었습니다. 다시 찾아주세요.");
        }
        return previewHospitalPage(request.searchId(), search, request.page());
    }

    @PostMapping("/preview/transport-prefill")
    public PreviewResponse transportPrefill(@Valid @RequestBody TransportPrefillRequest request) {
        var transportRequest = new TransportRequest(
            request.origin(), request.destination(), request.date(), null,
            request.preferredArrivalTime().toString());
        return previewTransport(agents.<TransportRequest, TransportRecommendations>asFunction(TransportRecommendations.class)
            .apply(transportRequest, ProcessOptions.DEFAULT));
    }

    private PreviewResponse previewHospital(String message) {
        var function = agents.<UserInput, HospitalRequest>asFunction(HospitalRequest.class);
        var request = function.apply(new UserInput(message), ProcessOptions.DEFAULT);
        var searchId = UUID.randomUUID();
        hospitalSearches.put(searchId, request);
        return previewHospitalPage(searchId, request, 1);
    }

    private PreviewResponse previewHospitalPage(UUID searchId, HospitalRequest request, int page) {
        var appointmentPage = hospitalAgent.searchPage(request, page, 10);
        var options = appointmentPage.appointments().stream()
                                     .map(appointment -> hospitalOption(appointment, request, null))
                                     .toList();
        if (options.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "지금 예약할 수 있는 일정이 없습니다. 날짜나 시간을 바꿔 말씀해 주세요.");
        }
        return new PreviewResponse(BookingType.HOSPITAL, options, searchId, page, appointmentPage.hasMore());
    }

    private PreviewResponse retryHospital(PendingBooking pending) {
        var failed = (HospitalAppointment) pending.plan();
        var request = pending.hospitalRequest();
        var appointmentPage = hospitalAgent.searchPage(request, 1, 10);
        var options = appointmentPage.appointments().stream()
                                     .filter(appointment -> !sameAppointment(failed, appointment))
                                     .map(appointment -> hospitalOption(
                                         appointment, request, hospitalChanges(failed, appointment)))
                                     .toList();
        if (options.isEmpty()) {
            throw new IllegalStateException(
                "선택한 시간이 마감되었고 같은 조건의 다른 예약 시간을 찾지 못했습니다. 날짜나 시간을 바꿔 다시 찾아주세요.");
        }
        var searchId = UUID.randomUUID();
        hospitalSearches.put(searchId, request);
        return new PreviewResponse(BookingType.HOSPITAL, options, searchId, 1, appointmentPage.hasMore());
    }

    private boolean sameAppointment(HospitalAppointment first, HospitalAppointment second) {
        return Objects.equals(first.hospitalName(), second.hospitalName())
            && Objects.equals(first.department(), second.department())
            && Objects.equals(first.appointmentTime(), second.appointmentTime());
    }

    private String hospitalChanges(HospitalAppointment failed, HospitalAppointment replacement) {
        var changes = new ArrayList<String>();
        if (!Objects.equals(failed.hospitalName(), replacement.hospitalName())) {
            changes.add("병원 %s → %s".formatted(failed.hospitalName(), replacement.hospitalName()));
        }
        if (!Objects.equals(failed.department(), replacement.department())) {
            changes.add("진료과 %s → %s".formatted(failed.department(), replacement.department()));
        }
        if (!Objects.equals(failed.appointmentTime(), replacement.appointmentTime())) {
            changes.add("진료 시간 %s → %s".formatted(
                KoreanDateTime.format(failed.appointmentTime()),
                KoreanDateTime.format(replacement.appointmentTime())));
        }
        if (failed.fee() != replacement.fee()) {
            changes.add("진료비 %,d원 → %,d원".formatted(failed.fee(), replacement.fee()));
        }
        return "선택한 예약이 방금 마감되었습니다. 변경점: "
            + String.join(" · ", changes) + ". 다시 승인해 주세요.";
    }

    private PreviewOption hospitalOption(
        HospitalAppointment appointment,
        HospitalRequest request,
        String warning
    ) {
        var id = remember(BookingType.HOSPITAL, appointment, request);
        return new PreviewOption(
            id,
            "%s · %s".formatted(appointment.hospitalName(), appointment.department()),
            "병원 운영시간: " + appointment.operatingHours(),
            null,
            KoreanDateTime.format(appointment.appointmentTime()),
            "",
            appointment.address(),
            "",
            "",
            List.of(
                new Detail("진료", KoreanDateTime.format(appointment.appointmentTime())),
                new Detail("병원 주소", appointment.address()),
                new Detail("진료비", "%,d원".formatted(appointment.fee()))
            ),
            appointment.fee(),
            warning
        );
    }

    private PreviewResponse previewTransport(String message) {
        var function = agents.<UserInput, TransportRecommendations>asFunction(TransportRecommendations.class);
        return previewTransport(function.apply(new UserInput(message), ProcessOptions.DEFAULT));
    }

    private PreviewResponse previewTransport(TransportRecommendations result) {
        var options = result.recommendations().stream()
                            .map(recommendation -> transportOption(recommendation.plan(), recommendation.reason()))
                            .toList();
        if (options.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "이용할 수 있는 교통편이 없습니다. 날짜나 시간을 바꿔 말씀해 주세요.");
        }
        return new PreviewResponse(BookingType.TRANSPORT, options, null, 1, false);
    }

    private PreviewOption transportOption(TransportPlan plan, String recommendationReason) {
        var id = remember(BookingType.TRANSPORT, plan);
        return new PreviewOption(
            id,
            "%s → %s".formatted(plan.legs().getFirst().origin(), plan.legs().getLast().destination()),
            recommendationReason,
            plan.primaryMode(),
            plan.serviceInfo(),
            plan.legs().getFirst().origin(),
            plan.legs().getLast().destination(),
            KoreanDateTime.formatTime(plan.departAt()),
            KoreanDateTime.formatTime(plan.arriveAt()),
            List.of(
                new Detail("이용 날짜", KoreanDateTime.formatDate(plan.departAt())),
                new Detail("이동 구간", "%d개".formatted(plan.legs().size())),
                new Detail("교통비", "%,d원".formatted(plan.totalPrice()))
            ),
            plan.totalPrice(),
            null
        );
    }

    UUID remember(BookingType type, Object plan) {
        return remember(type, plan, null);
    }

    UUID remember(BookingType type, Object plan, HospitalRequest hospitalRequest) {
        var id = UUID.randomUUID();
        pendingBookings.put(id, new PendingBooking(type, plan, hospitalRequest));
        return id;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleFailure(Exception exception) {
        var message = exception.getMessage();
        return new ErrorResponse(message == null || message.isBlank()
            ? "요청을 처리하지 못했습니다. 내용을 확인하고 다시 시도해 주세요."
            : message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
            .body(new ErrorResponse(exception.getReason()));
    }

    public enum BookingType { HOSPITAL, TRANSPORT }

    public record PreviewRequest(
        @NotNull BookingType type,
        @NotBlank @Size(max = 500) String message
    ) {
    }

    public record QuestionRequest(@NotBlank @Size(max = 500) String question) {
    }

    public record ApprovalRequest(@NotNull UUID previewId, boolean approved) {
    }

    public record HospitalPageRequest(@NotNull UUID searchId, @Min(1) int page) {
    }

    public record TransportPrefillRequest(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotNull LocalDate date,
        @NotNull LocalTime preferredArrivalTime
    ) {
    }

    public record PreviewResponse(
        BookingType type,
        List<PreviewOption> options,
        UUID searchId,
        int page,
        boolean hasMore
    ) {
    }

    public record PreviewOption(
        UUID id,
        String title,
        String summary,
        TransportMode transportMode,
        String serviceInfo,
        String origin,
        String destination,
        String departureTime,
        String arrivalTime,
        List<Detail> details,
        int totalPrice,
        String warning
    ) {
    }

    public record Detail(String label, String value) {
    }

    public record ReservationResponse(
        String message,
        List<String> reservationNumbers,
        PreviewResponse retryPreview,
        TransportPrefill transportPrefill
    ) {
    }

    public record TransportPrefill(
        String destination,
        LocalDate date,
        LocalTime preferredArrivalTime
    ) {
    }

    public record ErrorResponse(String message) {
    }

    private record PendingBooking(BookingType type, Object plan, HospitalRequest hospitalRequest) {
    }
}
