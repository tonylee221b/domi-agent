package org.example.seniorlifebookingagent.tool;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.example.seniorlifebookingagent.domain.transport.TransportLeg;
import org.example.seniorlifebookingagent.domain.transport.TransportMode;
import org.example.seniorlifebookingagent.domain.transport.TransportPlan;
import org.example.seniorlifebookingagent.domain.transport.TransportRequest;
import org.example.seniorlifebookingagent.support.KoreanDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TagoTransportTool implements TransportTool {

    private static final Logger log = LoggerFactory.getLogger(TagoTransportTool.class);
    private static final String TRAIN_API = "https://apis.data.go.kr/1613000/TrainInfo";
    private static final String INTERCITY_BUS_API = "https://apis.data.go.kr/1613000/SuburbsBusInfo";
    private static final String EXPRESS_BUS_API = "https://apis.data.go.kr/1613000/ExpBusInfo";
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter API_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PublicDataClient client;

    TagoTransportTool(PublicDataClient client) {
        this.client = client;
    }

    @Override
    public List<TransportPlan> search(TransportRequest request) {
        if (request.origin() == null || request.origin().isBlank()
            || request.destination() == null || request.destination().isBlank()) {
            throw new IllegalArgumentException("출발지와 목적지를 모두 말씀해 주세요.");
        }
        if (request.date().isBefore(KoreanDateTime.today())) {
            throw new IllegalArgumentException("지난 날짜의 교통편은 조회할 수 없습니다. 오늘 이후 날짜를 말씀해 주세요.");
        }

        var departureTime = preferredTime(request.preferredDepartureTime(), false);
        var arrivalTime = preferredTime(request.preferredArrivalTime(), true);
        log.info("교통 조회 시간 조건: 출발 {}, 도착 {}", departureTime, arrivalTime);
        var plans = new ArrayList<TransportPlan>();
        plans.addAll(trains(request, departureTime, arrivalTime));
        plans.addAll(buses(request, departureTime, arrivalTime, INTERCITY_BUS_API,
            "/GetSuberbsBusTrminlList", "/GetStrtpntAlocFndSuberbsBusInfo", TransportMode.INTERCITY_BUS));
        plans.addAll(buses(request, departureTime, arrivalTime, EXPRESS_BUS_API,
            "/GetExpBusTrminlList", "/GetStrtpntAlocFndExpbusInfo", TransportMode.EXPRESS_BUS));
        var byMode = new EnumMap<TransportMode, ArrayDeque<TransportPlan>>(TransportMode.class);
        plans.stream().sorted(Comparator.comparing(TransportPlan::departAt))
             .forEach(plan -> byMode.computeIfAbsent(plan.primaryMode(), ignored -> new ArrayDeque<>()).add(plan));
        var mixed = new ArrayList<TransportPlan>();
        while (mixed.size() < plans.size()) {
            for (var mode : TransportMode.values()) {
                var candidates = byMode.get(mode);
                if (candidates != null && !candidates.isEmpty()) {
                    mixed.add(candidates.remove());
                }
            }
        }
        return List.copyOf(mixed);
    }

    private List<TransportPlan> trains(TransportRequest request, TimeRange departureTime, TimeRange arrivalTime) {
        var cities = client.get(TRAIN_API, "/GetCtyCodeList", Map.of("_type", "xml"));
        var departures = stations(cities, request.origin());
        var arrivals = stations(cities, request.destination());
        if (departures.isEmpty() || arrivals.isEmpty()) {
            return List.of();
        }
        var found = new ArrayList<TransportPlan>();
        for (var departure : departures) {
            for (var arrival : arrivals) {
                var items = client.get(TRAIN_API, "/GetStrtpntAlocFndTrainInfo", Map.of(
                    "pageNo", "1",
                    "numOfRows", "100",
                    "_type", "xml",
                    "depPlaceId", departure.id(),
                    "arrPlaceId", arrival.id(),
                    "depPlandTime", request.date().format(API_DATE)
                ));
                found.addAll(plans(items, TransportMode.TRAIN, departure.name(), arrival.name(),
                    "adultcharge", departureTime, arrivalTime));
            }
        }
        return List.copyOf(found);
    }

    private List<TransportPlan> buses(
        TransportRequest request,
        TimeRange departureTime,
        TimeRange arrivalTime,
        String api,
        String terminalPath,
        String timetablePath,
        TransportMode mode
    ) {
        var terminals = client.get(api, terminalPath, Map.of(
            "pageNo", "1",
            "numOfRows", "1000",
            "_type", "xml"
        ));
        var departures = terminals(terminals, request.origin());
        var arrivals = terminals(terminals, request.destination());
        log.info("{} 터미널 조회: 전체 {}개, 출발 후보 {}개, 도착 후보 {}개",
            mode.displayName(), terminals.size(), departures.size(), arrivals.size());
        if (departures.isEmpty() || arrivals.isEmpty()) {
            return List.of();
        }
        for (var departure : departures) {
            for (var arrival : arrivals) {
                var items = client.get(api, timetablePath, Map.of(
                    "pageNo", "1",
                    "numOfRows", "100",
                    "_type", "xml",
                    "depTerminalId", departure.id(),
                    "arrTerminalId", arrival.id(),
                    "depPlandTime", request.date().format(API_DATE)
                ));
                var found = plans(items, mode, departure.name(), arrival.name(), "charge", departureTime, arrivalTime);
                log.info("{} {}→{} 운행편: API {}개, 시간 조건 통과 {}개",
                    mode.displayName(), departure.name(), arrival.name(), items.size(), found.size());
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return List.of();
    }

    private List<TransportPlan> plans(
        List<Map<String, String>> items,
        TransportMode mode,
        String departure,
        String arrival,
        String chargeField,
        TimeRange departureTime,
        TimeRange arrivalTime
    ) {
        return items.stream()
                    .map(item -> plan(item, mode, departure, arrival, chargeField))
                    .filter(plan -> departureTime == null || departureTime.includes(plan.departAt().toLocalTime()))
                    .filter(plan -> arrivalTime == null || arrivalTime.includes(plan.arriveAt().toLocalTime()))
                    .limit(3)
                    .toList();
    }

    private List<Station> stations(List<Map<String, String>> cities, String place) {
        var matchingCities = cities.stream().filter(item -> matchesCity(place, item.get("cityname"))).toList();
        // ponytail: 지역 API가 없어 시·도명이 아닌 입력은 역 목록을 순회한다. 호출량이 문제되면 역 목록을 캐시한다.
        var searchCities = matchingCities.isEmpty() ? cities : matchingCities;
        var matches = searchCities.stream()
            .flatMap(city -> client.get(TRAIN_API, "/GetCtyAcctoTrainSttnList", Map.of(
                "pageNo", "1", "numOfRows", "100", "_type", "xml", "cityCode", city.get("citycode")
            )).stream())
            .filter(item -> place.contains(value(item, "nodename"))
                || value(item, "nodename").contains(place.replace("역", "")))
            .limit(5)
            .map(item -> new Station(value(item, "nodeid"), value(item, "nodename")))
            .toList();
        if (!matches.isEmpty() || matchingCities.isEmpty()) {
            return matches;
        }
        return client.get(TRAIN_API, "/GetCtyAcctoTrainSttnList", Map.of(
            "pageNo", "1", "numOfRows", "100", "_type", "xml",
            "cityCode", matchingCities.getFirst().get("citycode")
        )).stream().limit(1)
          .map(item -> new Station(value(item, "nodeid"), value(item, "nodename")))
          .toList();
    }

    private List<Station> terminals(List<Map<String, String>> terminals, String place) {
        var areas = Arrays.stream(place.split("[\\s,]+"))
                          .map(this::cityCore)
                          .filter(area -> area.length() >= 2)
                          .toList();
        // ponytail: 지도 없이 주소의 지역명과 맞는 터미널 중 실제 운행편이 있는 첫 조합을 쓴다.
        return terminals.stream()
                        .filter(item -> areas.stream().anyMatch(area ->
                            value(item, "terminalNm").contains(area)
                                || value(item, "cityName", "").contains(area)
                                || value(item, "terminalNm").startsWith(area.substring(0, 2))
                                || value(item, "cityName", "").startsWith(area.substring(0, 2))))
                        .limit(5)
                        .map(item -> new Station(value(item, "terminalId"), value(item, "terminalNm")))
                        .toList();
    }

    private TransportPlan plan(
        Map<String, String> item,
        TransportMode mode,
        String departure,
        String arrival,
        String chargeField
    ) {
        return new TransportPlan(mode, List.of(new TransportLeg(
            mode,
            serviceInfo(item, mode),
            stopName(value(item, "depPlaceNm", departure), mode),
            stopName(value(item, "arrPlaceNm", arrival), mode),
            parseDateTime(value(item, "depPlandTime")),
            parseDateTime(value(item, "arrPlandTime")),
            Integer.parseInt(value(item, chargeField, "0"))
        )));
    }

    private String stopName(String name, TransportMode mode) {
        var suffix = switch (mode) {
            case TRAIN -> "역";
            case INTERCITY_BUS, EXPRESS_BUS -> "터미널";
            default -> "";
        };
        return suffix.isEmpty() || name.endsWith(suffix) ? name : name + suffix;
    }

    private String serviceInfo(Map<String, String> item, TransportMode mode) {
        var grade = value(item, mode == TransportMode.TRAIN ? "trainGradeName" : "gradeNm", "");
        var number = value(item, mode == TransportMode.TRAIN ? "trainNo" : "routeId", "");
        var numberLabel = mode == TransportMode.TRAIN ? number + "호" : "노선 " + number;
        return (grade + (number.isBlank() ? "" : " " + numberLabel)).strip();
    }

    private String value(Map<String, String> item, String key) {
        return value(item, key, null);
    }

    private String value(Map<String, String> item, String key, String fallback) {
        return item.getOrDefault(key, item.getOrDefault(key.toLowerCase(), fallback));
    }

    private LocalDateTime parseDateTime(String value) {
        return LocalDateTime.parse(value.length() == 12 ? value + "00" : value, API_DATE_TIME);
    }

    private boolean matchesCity(String place, String cityName) {
        var core = cityCore(cityName);
        return place.contains(cityName) || place.contains(core) || core.contains(place);
    }

    private String cityCore(String cityName) {
        return cityName.replaceFirst("(특별자치도|특별자치시|광역시|특별시|도)$", "");
    }

    private TimeRange preferredTime(String value, boolean exactIsUpperBound) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim()) {
            case "새벽" -> new TimeRange(LocalTime.MIN, LocalTime.of(5, 59));
            case "아침", "오전" -> new TimeRange(LocalTime.MIN, LocalTime.NOON.minusNanos(1));
            case "낮" -> new TimeRange(LocalTime.of(11, 0), LocalTime.of(13, 59));
            case "저녁", "오후" -> new TimeRange(LocalTime.NOON, LocalTime.MAX);
            case "밤" -> new TimeRange(LocalTime.of(21, 0), LocalTime.MAX);
            default -> {
                var exact = LocalTime.parse(value.trim());
                yield exactIsUpperBound
                    ? new TimeRange(LocalTime.MIN, exact)
                    : new TimeRange(exact, LocalTime.MAX);
            }
        };
    }

    private record TimeRange(LocalTime from, LocalTime to) {
        boolean includes(LocalTime time) {
            return !time.isBefore(from) && !time.isAfter(to);
        }
    }

    private record Station(String id, String name) {
    }
}
