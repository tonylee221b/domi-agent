package org.example.seniorlifebookingagent.tool;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.example.seniorlifebookingagent.domain.hospital.HospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalRequest;
import org.springframework.stereotype.Component;

@Component
public class HiraHospitalTool implements HospitalTool {

    private static final String HOSPITAL_API = "https://apis.data.go.kr/B551182/hospInfoServicev2";
    private static final String DETAIL_API = "https://apis.data.go.kr/B551182/MadmDtlInfoService2.8";
    private static final int PAGE_SIZE = 10;
    private static final Map<String, String> REGION_CODES = Map.ofEntries(
        Map.entry("서울", "110000"), Map.entry("부산", "210000"), Map.entry("인천", "220000"),
        Map.entry("대구", "230000"), Map.entry("광주", "240000"), Map.entry("대전", "250000"),
        Map.entry("울산", "260000"), Map.entry("경기", "310000"), Map.entry("강원", "320000"),
        Map.entry("충북", "330000"), Map.entry("충청북도", "330000"),
        Map.entry("충남", "340000"), Map.entry("충청남도", "340000"),
        Map.entry("전북", "350000"), Map.entry("전북특별자치도", "350000"),
        Map.entry("전남", "360000"), Map.entry("전라남도", "360000"),
        Map.entry("경북", "370000"), Map.entry("경상북도", "370000"),
        Map.entry("경남", "380000"), Map.entry("경상남도", "380000"),
        Map.entry("제주", "390000"), Map.entry("제주특별자치도", "390000"),
        Map.entry("세종", "410000")
    );
    private static final Map<String, String> DEPARTMENT_CODES = Map.ofEntries(
        Map.entry("내과", "01"), Map.entry("신경과", "02"),
        Map.entry("정신건강의학과", "03"), Map.entry("정신과", "03"), Map.entry("외과", "04"),
        Map.entry("정형외과", "05"), Map.entry("신경외과", "06"),
        Map.entry("심장혈관흉부외과", "07"), Map.entry("흉부외과", "07"), Map.entry("성형외과", "08"),
        Map.entry("마취통증의학과", "09"), Map.entry("산부인과", "10"), Map.entry("소아청소년과", "11"),
        Map.entry("안과", "12"), Map.entry("이비인후과", "13"), Map.entry("피부과", "14"),
        Map.entry("비뇨의학과", "15"), Map.entry("비뇨기과", "15"), Map.entry("영상의학과", "16"),
        Map.entry("방사선종양학과", "17"), Map.entry("병리과", "18"), Map.entry("진단검사의학과", "19"),
        Map.entry("결핵과", "20"), Map.entry("재활의학과", "21"), Map.entry("핵의학과", "22"),
        Map.entry("가정의학과", "23"), Map.entry("응급의학과", "24"), Map.entry("직업환경의학과", "25"),
        Map.entry("예방의학과", "26"), Map.entry("치과", "49")
    );
    private final PublicDataClient client;

    HiraHospitalTool(PublicDataClient client) {
        this.client = client;
    }

    @Override
    public List<HospitalAppointment> search(HospitalRequest request) {
        return searchPage(request, 1, PAGE_SIZE).appointments();
    }

    @Override
    public SearchPage searchPage(HospitalRequest request, int page, int size) {
        if (request.hospitalName() == null || request.hospitalName().isBlank()) {
            return recommendHospitals(request, page, size);
        }

        var hospitals = findHospitals(request.hospitalName(), request.region());
        if (hospitals.isEmpty() && request.hospitalName().endsWith("병원")) {
            hospitals = findHospitals(
                request.hospitalName().substring(0, request.hospitalName().length() - 2), request.region());
        }
        if (hospitals.isEmpty()) {
            throw new IllegalStateException("해당 병원을 공공데이터에서 찾지 못했습니다.");
        }

        var hospital = hospitals.stream()
                                .min(Comparator.comparingInt(item -> normalize(item.get("yadmNm"))
                                    .equals(normalize(request.hospitalName())) ? 0 : 1))
                                .orElseThrow();
        var ykiho = hospital.get("ykiho");
        verifyDepartment(ykiho, request.department());
        var hours = operatingHours(ykiho, request.date().getDayOfWeek());
        var appointments = appointments(hospital, request, hours);
        if (appointments.isEmpty()) {
            throw new IllegalStateException("요청한 날짜와 시간대에 가능한 병원 예약 시간이 없습니다.");
        }
        return new SearchPage(appointments, false);
    }

    private SearchPage recommendHospitals(HospitalRequest request, int page, int size) {
        var regionCode = codeFor(REGION_CODES, request.region(), "지원하는 지역을 말씀해 주세요.");
        var departmentCode = codeFor(DEPARTMENT_CODES, request.department(), "지원하는 진료과를 말씀해 주세요.");
        var hospitals = findHospitals(page, size, regionCode, departmentCode);

        var recommendations = hospitals.stream()
            .sorted(Comparator.comparingInt(this::doctorCount).reversed())
            .map(hospital -> firstAppointment(hospital, request))
            .flatMap(Optional::stream)
            .toList();
        if (recommendations.isEmpty()) {
            throw new IllegalStateException("해당 지역에서 요청한 진료과의 예약 가능한 병원을 찾지 못했습니다.");
        }
        return new SearchPage(recommendations, hospitals.size() == size);
    }

    private List<Map<String, String>> findHospitals(int page, int size, String regionCode, String departmentCode) {
        return client.get(HOSPITAL_API, "/getHospBasisList", Map.of(
            "pageNo", Integer.toString(page),
            "numOfRows", Integer.toString(size),
            "sidoCd", regionCode,
            "dgsbjtCd", departmentCode
        ), "ServiceKey");
    }

    private Optional<HospitalAppointment> firstAppointment(Map<String, String> hospital, HospitalRequest request) {
        var hours = operatingHours(hospital.get("ykiho"), request.date().getDayOfWeek());
        return appointments(hospital, request, hours).stream().findFirst();
    }

    private List<HospitalAppointment> appointments(
        Map<String, String> hospital,
        HospitalRequest request,
        OperatingHours hours
    ) {
        return appointmentTimes(hours, request.preferredTime()).stream()
            .map(time -> new HospitalAppointment(
                hospital.get("yadmNm"), request.department(), request.date().atTime(time), hospital.get("addr"),
                30_000, hours.opensAt(), hours.closesAt()
            ))
            .toList();
    }

    private List<LocalTime> appointmentTimes(OperatingHours hours, String preferredTime) {
        if (hours.opensAt() == null || hours.closesAt() == null) {
            return List.of();
        }
        var preferred = preferredWindow(preferredTime);
        var start = hours.opensAt().isAfter(preferred.from()) ? hours.opensAt() : preferred.from();
        var end = hours.closesAt().isBefore(preferred.to()) ? hours.closesAt() : preferred.to();
        var firstMinute = ((start.getHour() * 60 + start.getMinute() + 9) / 10) * 10;
        var lastMinute = (end.getHour() * 60 + end.getMinute()) / 10 * 10;
        var slotCount = (lastMinute - firstMinute) / 10 + 1;
        if (slotCount <= 0) {
            return List.of();
        }
        return IntStream.generate(() -> ThreadLocalRandom.current().nextInt(slotCount))
                        .distinct()
                        .limit(Math.min(3, slotCount))
                        .mapToObj(slot -> LocalTime.ofSecondOfDay((firstMinute + slot * 10L) * 60))
                        .sorted()
                        .toList();
    }

    private TimeWindow preferredWindow(String value) {
        if (value == null || value.isBlank()) {
            return new TimeWindow(LocalTime.MIN, LocalTime.MAX);
        }
        return switch (value.trim()) {
            case "새벽" -> new TimeWindow(LocalTime.MIN, LocalTime.of(5, 59));
            case "아침", "오전" -> new TimeWindow(LocalTime.MIN, LocalTime.NOON.minusNanos(1));
            case "낮" -> new TimeWindow(LocalTime.of(11, 0), LocalTime.of(13, 59));
            case "저녁", "오후" -> new TimeWindow(LocalTime.NOON, LocalTime.MAX);
            case "밤" -> new TimeWindow(LocalTime.of(21, 0), LocalTime.MAX);
            default -> {
                var exact = LocalTime.parse(value.trim());
                yield new TimeWindow(exact, exact);
            }
        };
    }

    private List<Map<String, String>> findHospitals(String name, String region) {
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("pageNo", "1");
        parameters.put("numOfRows", "10");
        parameters.put("yadmNm", name);
        parameters.put("sidoCd", codeFor(REGION_CODES, region, "지원하는 지역을 말씀해 주세요."));
        return client.get(HOSPITAL_API, "/getHospBasisList", parameters, "ServiceKey");
    }

    private String codeFor(Map<String, String> codes, String value, String message) {
        return findCode(codes, value).orElseThrow(() -> new IllegalArgumentException(message));
    }

    private Optional<String> findCode(Map<String, String> codes, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        var normalized = value.replaceAll("\\s", "");
        return codes.entrySet().stream()
            .filter(entry -> normalized.startsWith(entry.getKey()))
            .max(Comparator.comparingInt(entry -> entry.getKey().length()))
            .map(Map.Entry::getValue);
    }

    private int doctorCount(Map<String, String> hospital) {
        try {
            return Integer.parseInt(hospital.getOrDefault("drTotCnt", "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void verifyDepartment(String ykiho, String department) {
        var departments = client.get(DETAIL_API, "/getDgsbjtInfo2.8", Map.of(
            "pageNo", "1",
            "numOfRows", "100",
            "_type", "xml",
            "ykiho", ykiho
        ));
        if (departments.stream().noneMatch(item -> normalize(item.get("dgsbjtCdNm"))
            .contains(normalize(department)))) {
            throw new IllegalStateException("해당 병원에서 요청한 진료과를 찾지 못했습니다.");
        }
    }

    private OperatingHours operatingHours(String ykiho, DayOfWeek day) {
        var details = client.get(DETAIL_API, "/getDtlInfo2.8", Map.of(
            "pageNo", "1",
            "numOfRows", "1",
            "_type", "xml",
            "ykiho", ykiho
        ));
        if (details.isEmpty()) {
            return new OperatingHours(null, null);
        }

        var prefix = new LinkedHashMap<DayOfWeek, String>();
        prefix.put(DayOfWeek.MONDAY, "trmtMon");
        prefix.put(DayOfWeek.TUESDAY, "trmtTue");
        prefix.put(DayOfWeek.WEDNESDAY, "trmtWed");
        prefix.put(DayOfWeek.THURSDAY, "trmtThu");
        prefix.put(DayOfWeek.FRIDAY, "trmtFri");
        prefix.put(DayOfWeek.SATURDAY, "trmtSat");
        prefix.put(DayOfWeek.SUNDAY, "trmtSun");
        var fields = details.getFirst();
        return new OperatingHours(
            parseTime(fields.get(prefix.get(day) + "Start")),
            parseTime(fields.get(prefix.get(day) + "End"))
        );
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var digits = value.replace(":", "");
        return LocalTime.of(Integer.parseInt(digits.substring(0, 2)), Integer.parseInt(digits.substring(2, 4)));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("대학교", "대").replaceAll("[\\s()]", "");
    }

    private record OperatingHours(LocalTime opensAt, LocalTime closesAt) {
    }

    private record TimeWindow(LocalTime from, LocalTime to) {
    }
}
