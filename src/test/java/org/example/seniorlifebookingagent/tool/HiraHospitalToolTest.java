package org.example.seniorlifebookingagent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Collectors;
import org.example.seniorlifebookingagent.domain.hospital.HospitalRequest;
import org.junit.jupiter.api.Test;

class HiraHospitalToolTest {

    @Test
    void returnsReportedHospitalHours() {
        var client = new PublicDataClient("test-key", uri -> switch (uri.getPath()) {
            case "/B551182/hospInfoServicev2/getHospBasisList" -> xml("""
                <ykiho>Y0</ykiho><yadmNm>분당서울대학교병원</yadmNm><addr>경기 성남시 분당구</addr>
                </item><item>
                <ykiho>Y1</ykiho><yadmNm>서울대학교병원</yadmNm><addr>서울 종로구 대학로 101</addr>
                """);
            case "/B551182/MadmDtlInfoService2.8/getDgsbjtInfo2.8" -> xml(
                "<dgsbjtCdNm>정형외과</dgsbjtCdNm>");
            case "/B551182/MadmDtlInfoService2.8/getDtlInfo2.8" -> xml(
                "<trmtWedStart>0900</trmtWedStart><trmtWedEnd>1730</trmtWedEnd>");
            default -> throw new AssertionError(uri);
        });

        var tool = new HiraHospitalTool(client);
        var appointment = tool.search(new HospitalRequest(
            "서울", "서울대학교병원", "정형외과", LocalDate.of(2026, 8, 26), "아침"
        )).getFirst();
        var eveningAppointment = tool.search(new HospitalRequest(
            "서울", "서울대학교병원", "정형외과", LocalDate.of(2026, 8, 26), "저녁"
        )).getFirst();

        assertEquals("서울 종로구 대학로 101", appointment.address());
        assertEquals("서울대학교병원", appointment.hospitalName());
        assertEquals(LocalTime.of(9, 0), appointment.opensAt());
        assertEquals(LocalTime.of(17, 30), appointment.closesAt());
        assertTrue(!appointment.appointmentTime().toLocalTime().isBefore(LocalTime.of(9, 0))
            && appointment.appointmentTime().toLocalTime().isBefore(LocalTime.NOON));
        assertTrue(!eveningAppointment.appointmentTime().toLocalTime().isBefore(LocalTime.NOON));
        assertEquals(0, appointment.appointmentTime().getMinute() % 10);
    }

    @Test
    void retrievesOnlyTheRequestedHospitalPage() {
        var client = new PublicDataClient("test-key", uri -> switch (uri.getPath()) {
            case "/B551182/hospInfoServicev2/getHospBasisList" -> {
                assertTrue(uri.getQuery().contains("sidoCd=310000"));
                assertTrue(uri.getQuery().contains("dgsbjtCd=05"));
                assertTrue(uri.getQuery().contains("pageNo=2"));
                assertTrue(uri.getQuery().contains("numOfRows=10"));
                yield xml(hospitals(11, 21));
            }
            case "/B551182/MadmDtlInfoService2.8/getDtlInfo2.8" -> xml(
                "<trmtWedStart>0900</trmtWedStart><trmtWedEnd>1730</trmtWedEnd>");
            default -> throw new AssertionError(uri);
        });

        var page = new HiraHospitalTool(client).searchPage(new HospitalRequest(
            "경기", null, "정형외과", LocalDate.of(2026, 8, 26), "오전"
        ), 2, 10);

        assertEquals(10, page.appointments().size());
        assertEquals(10, page.appointments().stream()
            .map(appointment -> appointment.hospitalName())
            .collect(Collectors.toSet()).size());
        assertTrue(page.hasMore());
    }

    @Test
    void rejectsUnsupportedCityBeforeCallingPublicData() {
        var client = new PublicDataClient("test-key", uri -> {
            throw new AssertionError("공공데이터 API를 호출하면 안 됩니다: " + uri);
        });

        assertThrows(IllegalArgumentException.class, () -> new HiraHospitalTool(client).search(
            new HospitalRequest("수원", "수원병원", "정형외과", LocalDate.of(2026, 8, 26), "오전")
        ));
    }

    private String hospitals(int from, int to) {
        return java.util.stream.IntStream.range(from, to)
            .mapToObj(number -> """
                <ykiho>Y%d</ykiho><yadmNm>%d병원</yadmNm><addr>서울</addr><drTotCnt>%d</drTotCnt>
                """.formatted(number, number, number))
            .collect(Collectors.joining("</item><item>"));
    }

    private String xml(String item) {
        return "<response><header><resultCode>00</resultCode><resultMsg>OK</resultMsg></header>"
            + "<body><items><item>" + item + "</item></items></body></response>";
    }
}
