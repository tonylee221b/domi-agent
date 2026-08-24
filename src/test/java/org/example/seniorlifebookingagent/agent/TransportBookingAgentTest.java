package org.example.seniorlifebookingagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.example.seniorlifebookingagent.domain.transport.ApprovedTransportPlan;
import org.example.seniorlifebookingagent.domain.transport.TransportLeg;
import org.example.seniorlifebookingagent.domain.transport.TransportMode;
import org.example.seniorlifebookingagent.domain.transport.TransportOptions;
import org.example.seniorlifebookingagent.domain.transport.TransportPlan;
import org.example.seniorlifebookingagent.domain.transport.TransportRequest;
import org.junit.jupiter.api.Test;

public class TransportBookingAgentTest {

    @Test
    void runsTransportAgent() {
        var req = new TransportRequest(
            "대전",
            "서울",
            LocalDate.of(2026, 8, 20),
            null,
            "15:00"
        );

        var tool = (org.example.seniorlifebookingagent.tool.TransportTool) ignored -> List.of(
            new TransportPlan(TransportMode.TRAIN, List.of(new TransportLeg(
                TransportMode.TRAIN, "KTX 123호", "대전역", "서울역",
                req.date().atTime(8, 10), req.date().atTime(9, 21), 47_400
            )))
        );
        var agent = new TransportBookingAgent(tool);
        var result = agent.searchTransport(req).plans().getFirst();

        assertNotNull(result);
        assertEquals(TransportMode.TRAIN, result.primaryMode());
        assertEquals("기차, 대전역 → 서울역, 오전 8시 10분 출발, 오전 9시 21분 도착, 47,400원",
            result.choiceSummary());
        assertTrue(agent.reserve(new ApprovedTransportPlan(result)).reservationNumber()
                        .matches("DEMO-T-\\d{8}-\\d{4}"));
    }

    @Test
    void appliesAiRankingWithoutChangingRealTransportData() {
        var date = LocalDate.of(2026, 8, 20);
        var train = new TransportPlan(TransportMode.TRAIN, List.of(new TransportLeg(
            TransportMode.TRAIN, "KTX 123호", "대전역", "서울역",
            date.atTime(8, 10), date.atTime(9, 21), 47_400)));
        var bus = new TransportPlan(TransportMode.EXPRESS_BUS, List.of(new TransportLeg(
            TransportMode.EXPRESS_BUS, "우등", "대전터미널", "서울터미널",
            date.atTime(8, 0), date.atTime(10, 0), 18_000)));
        var agent = new TransportBookingAgent(ignored -> List.of());

        var result = agent.applyRanking(
            new TransportOptions(List.of(train, bus)),
            new TransportBookingAgent.Ranking(List.of(
                new TransportBookingAgent.RankedCandidate(2, List.of("가장 저렴", "편안한 우등 좌석")),
                new TransportBookingAgent.RankedCandidate(1, List.of("가장 빠른 도착")))));

        assertEquals(bus, result.recommendations().getFirst().plan());
        assertEquals("• 가장 저렴\n• 편안한 우등 좌석", result.recommendations().getFirst().reason());
        assertEquals(train, result.recommendations().getLast().plan());
    }

}
