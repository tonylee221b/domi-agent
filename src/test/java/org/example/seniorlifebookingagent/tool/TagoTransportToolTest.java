package org.example.seniorlifebookingagent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.example.seniorlifebookingagent.domain.transport.TransportMode;
import org.example.seniorlifebookingagent.domain.transport.TransportPlan;
import org.example.seniorlifebookingagent.domain.transport.TransportRequest;
import org.example.seniorlifebookingagent.support.KoreanDateTime;
import org.junit.jupiter.api.Test;

class TagoTransportToolTest {

    @Test
    void rejectsMissingPlaceBeforeCallingPublicData() {
        var client = new PublicDataClient("test-key", uri -> {
            throw new AssertionError("공공데이터를 호출하면 안 됩니다: " + uri);
        });

        var error = assertThrows(IllegalArgumentException.class, () ->
            new TagoTransportTool(client).search(new TransportRequest(
                null, "서울", LocalDate.of(2026, 8, 26), "아침", null
            )));

        assertEquals("출발지와 목적지를 모두 말씀해 주세요.", error.getMessage());
    }

    @Test
    void rejectsPastDateBeforeCallingPublicData() {
        var client = new PublicDataClient("test-key", uri -> {
            throw new AssertionError("공공데이터를 호출하면 안 됩니다: " + uri);
        });

        var error = assertThrows(IllegalArgumentException.class, () ->
            new TagoTransportTool(client).search(new TransportRequest(
                "천안", "부산", KoreanDateTime.today().minusDays(1), null, null
            )));

        assertEquals("지난 날짜의 교통편은 조회할 수 없습니다. 오늘 이후 날짜를 말씀해 주세요.", error.getMessage());
    }

    @Test
    void returnsActualTimetableShapeAndFare() {
        var client = new PublicDataClient("test-key", uri -> switch (uri.getPath()) {
            case "/1613000/TrainInfo/GetCtyCodeList" -> xml("""
                <item><cityname>서울특별시</cityname><citycode>11</citycode></item>
                <item><cityname>대전광역시</cityname><citycode>25</citycode></item>
                """);
            case "/1613000/TrainInfo/GetCtyAcctoTrainSttnList" ->
                uri.getQuery().contains("cityCode=25")
                    ? xml("<item><nodename>대전</nodename><nodeid>NAT011668</nodeid></item>")
                    : xml("<item><nodename>서울</nodename><nodeid>NAT010000</nodeid></item>");
            case "/1613000/TrainInfo/GetStrtpntAlocFndTrainInfo" -> xml("""
                <item><depplandtime>20260826081000</depplandtime><arrplandtime>20260826092100</arrplandtime>
                <adultcharge>47400</adultcharge><traingradename>KTX</traingradename><trainno>123</trainno></item><item>
                <depplandtime>20260826170000</depplandtime><arrplandtime>20260826182100</arrplandtime>
                <adultcharge>47400</adultcharge><traingradename>KTX</traingradename><trainno>456</trainno></item>
                """);
            case "/1613000/SuburbsBusInfo/GetSuberbsBusTrminlList" -> xml("""
                <item><terminalNm>대전복합</terminalNm><terminalId>NAEK300</terminalId></item>
                <item><terminalNm>서울남부</terminalNm><terminalId>NAEK020</terminalId></item>
                """);
            case "/1613000/SuburbsBusInfo/GetStrtpntAlocFndSuberbsBusInfo" -> xml("""
                <item><depPlaceNm>대전복합</depPlaceNm><arrPlaceNm>서울남부</arrPlaceNm>
                <depPlandTime>202608260830</depPlandTime><arrPlandTime>202608261030</arrPlandTime>
                <charge>13800</charge></item>
                """);
            case "/1613000/ExpBusInfo/GetExpBusTrminlList" -> xml("""
                <item><terminalNm>대전복합</terminalNm><terminalId>NAEK300</terminalId></item>
                <item><terminalNm>서울경부</terminalNm><terminalId>NAEK010</terminalId></item>
                """);
            case "/1613000/ExpBusInfo/GetStrtpntAlocFndExpbusInfo" -> xml("""
                <item><depPlaceNm>대전복합</depPlaceNm><arrPlaceNm>서울경부</arrPlaceNm>
                <depPlandTime>20260826090000</depPlandTime><arrPlandTime>20260826110000</arrPlandTime>
                <charge>17200</charge></item>
                """);
            default -> throw new AssertionError(uri);
        });

        var plans = new TagoTransportTool(client).search(new TransportRequest(
            "대전", "서울", LocalDate.of(2026, 8, 26), "아침", "오전"
        ));
        var eveningPlan = new TagoTransportTool(client).search(new TransportRequest(
            "대전", "서울", LocalDate.of(2026, 8, 26), "저녁", null
        )).getFirst();
        var plan = plans.getFirst();

        assertEquals(Set.of(TransportMode.TRAIN, TransportMode.INTERCITY_BUS, TransportMode.EXPRESS_BUS),
            plans.stream().map(item -> item.primaryMode()).collect(Collectors.toSet()));
        assertEquals(LocalTime.of(8, 10), plan.departAt().toLocalTime());
        assertEquals(LocalTime.of(9, 21), plan.arriveAt().toLocalTime());
        assertEquals("대전역", plan.legs().getFirst().origin());
        assertEquals("서울역", plan.legs().getLast().destination());
        assertEquals(47_400, plan.totalPrice());
        assertEquals("KTX 123호", plan.serviceInfo());
        assertEquals(LocalTime.of(17, 0), eveningPlan.departAt().toLocalTime());
        assertTrue(plans.stream()
            .filter(candidate -> candidate.primaryMode() == TransportMode.EXPRESS_BUS)
            .allMatch(candidate -> candidate.legs().getFirst().origin().equals("대전복합터미널")
                && candidate.legs().getLast().destination().equals("서울경부터미널")));
    }

    @Test
    void includesAllSupportedLongDistanceModes() {
        var client = new PublicDataClient("test-key", uri -> switch (uri.getPath()) {
            case "/1613000/TrainInfo/GetCtyCodeList" -> xml("""
                <item><cityname>서울특별시</cityname><citycode>11</citycode></item>
                <item><cityname>대전광역시</cityname><citycode>25</citycode></item>
                """);
            case "/1613000/TrainInfo/GetCtyAcctoTrainSttnList" ->
                uri.getQuery().contains("cityCode=25")
                    ? xml("<item><nodename>대전</nodename><nodeid>D</nodeid></item>")
                    : xml("<item><nodename>서울</nodename><nodeid>S</nodeid></item>");
            case "/1613000/TrainInfo/GetStrtpntAlocFndTrainInfo" -> timetable("47400");
            case "/1613000/SuburbsBusInfo/GetSuberbsBusTrminlList",
                 "/1613000/ExpBusInfo/GetExpBusTrminlList" -> xml("""
                     <item><terminalNm>대전</terminalNm><terminalId>D</terminalId></item>
                     <item><terminalNm>서울</terminalNm><terminalId>S</terminalId></item>
                     """);
            case "/1613000/SuburbsBusInfo/GetStrtpntAlocFndSuberbsBusInfo",
                 "/1613000/ExpBusInfo/GetStrtpntAlocFndExpbusInfo" -> timetable("15000");
            default -> throw new AssertionError(uri);
        });

        var plans = new TagoTransportTool(client).search(new TransportRequest(
            "대전", "서울대병원", LocalDate.of(2026, 8, 26), null, null
        ));
        var modes = plans.stream().map(plan -> plan.primaryMode()).collect(Collectors.toSet());

        assertEquals(Set.of(TransportMode.TRAIN, TransportMode.INTERCITY_BUS, TransportMode.EXPRESS_BUS), modes);
        assertTrue(plans.stream().anyMatch(plan -> plan.serviceInfo().equals("우등 노선 B100")));
    }

    @Test
    void findsCityStationsAndMixesTransportModes() {
        var client = new PublicDataClient("test-key", uri -> switch (uri.getPath()) {
            case "/1613000/TrainInfo/GetCtyCodeList" -> xml("""
                <item><cityname>서울특별시</cityname><citycode>11</citycode></item>
                <item><cityname>충청남도</cityname><citycode>34</citycode></item>
                """);
            case "/1613000/TrainInfo/GetCtyAcctoTrainSttnList" ->
                uri.getQuery().contains("cityCode=34")
                    ? xml("""
                        <item><nodename>천안</nodename><nodeid>CHEONAN</nodeid></item>
                        <item><nodename>천안아산</nodename><nodeid>CHEONAN_ASAN</nodeid></item>
                        """)
                    : xml("<item><nodename>서울</nodename><nodeid>SEOUL</nodeid></item>");
            case "/1613000/TrainInfo/GetStrtpntAlocFndTrainInfo" ->
                uri.getQuery().contains("depPlaceId=CHEONAN_ASAN")
                    ? xml("""
                        <item><depplandtime>20260826090000</depplandtime><arrplandtime>20260826103000</arrplandtime>
                        <adultcharge>35000</adultcharge><traingradename>KTX</traingradename><trainno>2</trainno></item>
                        """)
                    : xml("""
                        <item><depplandtime>20260826080000</depplandtime><arrplandtime>20260826100000</arrplandtime>
                        <adultcharge>20000</adultcharge><traingradename>ITX</traingradename><trainno>1</trainno></item>
                        """);
            case "/1613000/SuburbsBusInfo/GetSuberbsBusTrminlList",
                 "/1613000/ExpBusInfo/GetExpBusTrminlList" -> xml("""
                     <item><terminalNm>천안종합</terminalNm><terminalId>CHEONAN_BUS</terminalId></item>
                     <item><terminalNm>서울경부</terminalNm><terminalId>SEOUL_BUS</terminalId></item>
                     """);
            case "/1613000/SuburbsBusInfo/GetStrtpntAlocFndSuberbsBusInfo",
                 "/1613000/ExpBusInfo/GetStrtpntAlocFndExpbusInfo" -> timetable("15000");
            default -> throw new AssertionError(uri);
        });

        var plans = new TagoTransportTool(client).search(new TransportRequest(
            "천안", "서울", LocalDate.of(2026, 8, 26), null, null
        ));

        assertEquals(List.of(TransportMode.TRAIN, TransportMode.INTERCITY_BUS, TransportMode.EXPRESS_BUS),
            plans.stream().limit(3).map(TransportPlan::primaryMode).toList());
        assertEquals(Set.of("천안역", "천안아산역"), plans.stream()
            .filter(plan -> plan.primaryMode() == TransportMode.TRAIN)
            .map(plan -> plan.legs().getFirst().origin())
            .collect(Collectors.toSet()));
        assertEquals(Set.of("ITX 1호", "KTX 2호"), plans.stream()
            .filter(plan -> plan.primaryMode() == TransportMode.TRAIN)
            .map(TransportPlan::serviceInfo)
            .collect(Collectors.toSet()));
    }

    private String timetable(String charge) {
        return xml("<item><depPlandTime>20260826081000</depPlandTime>"
            + "<arrPlandTime>20260826092100</arrPlandTime><charge>" + charge + "</charge>"
            + "<adultcharge>" + charge + "</adultcharge><gradeNm>우등</gradeNm><routeId>B100</routeId>"
            + "<trainGradeName>KTX</trainGradeName><trainNo>123</trainNo></item>");
    }

    private String xml(String items) {
        return "<response><header><resultCode>00</resultCode><resultMsg>OK</resultMsg></header>"
            + "<body><items>" + items + "</items></body></response>";
    }
}
