package org.example.seniorlifebookingagent.tool;

import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

@Component
class PublicDataClient {

    private final String serviceKey;
    private final Function<URI, String> fetch;

    @Autowired
    PublicDataClient(@Value("${public-data.service-key:}") String serviceKey) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.serviceKey = normalizeServiceKey(serviceKey);
        this.fetch = uri -> {
            try {
                var response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("공공데이터 API 오류 (%d): %s".formatted(
                        response.statusCode(), errorDetail(response.body())));
                }
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("공공데이터 조회가 중단되었습니다.", e);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("공공데이터를 조회하지 못했습니다.", e);
            }
        };
    }

    PublicDataClient(String serviceKey, Function<URI, String> fetch) {
        this.serviceKey = normalizeServiceKey(serviceKey);
        this.fetch = fetch;
    }

    List<Map<String, String>> get(String baseUrl, String path, Map<String, String> parameters) {
        return get(baseUrl, path, parameters, "serviceKey");
    }

    List<Map<String, String>> get(
        String baseUrl,
        String path,
        Map<String, String> parameters,
        String serviceKeyParameter
    ) {
        if (serviceKey.isBlank()) {
            throw new IllegalStateException("PUBLIC_DATA_SERVICE_KEY 환경변수를 설정해 주세요.");
        }

        var query = new LinkedHashMap<String, String>();
        query.put(serviceKeyParameter, serviceKey);
        query.putAll(parameters);
        var encoded = query.entrySet().stream()
                           .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                           .reduce((left, right) -> left + "&" + right)
                           .orElseThrow();
        return items(fetch.apply(URI.create(baseUrl + path + "?" + encoded)));
    }

    private List<Map<String, String>> items(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            for (var tag : List.of("returnAuthMsg", "errMsg")) {
                var errors = document.getElementsByTagName(tag);
                if (errors.getLength() > 0 && !errors.item(0).getTextContent().isBlank()) {
                    throw new IllegalStateException("공공데이터 API 오류: " + errors.item(0).getTextContent().strip());
                }
            }
            var resultCode = document.getElementsByTagName("resultCode");
            if (resultCode.getLength() > 0 && !resultCode.item(0).getTextContent().matches("0+")) {
                throw new IllegalStateException("공공데이터 API 오류: "
                    + document.getElementsByTagName("resultMsg").item(0).getTextContent());
            }

            var result = new ArrayList<Map<String, String>>();
            var itemNodes = document.getElementsByTagName("item");
            for (int i = 0; i < itemNodes.getLength(); i++) {
                var item = (Element) itemNodes.item(i);
                var values = new LinkedHashMap<String, String>();
                var children = item.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    var child = children.item(j);
                    if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        values.put(child.getNodeName(), child.getTextContent().trim());
                    }
                }
                result.add(values);
            }
            return List.copyOf(result);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("공공데이터 XML 응답을 읽지 못했습니다.", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String normalizeServiceKey(String value) {
        var key = value.trim();
        return key.contains("%") ? URLDecoder.decode(key, StandardCharsets.UTF_8) : key;
    }

    static String errorDetail(String body) {
        for (var tag : List.of("returnAuthMsg", "resultMsg", "errMsg")) {
            var start = body.indexOf("<" + tag + ">");
            var end = body.indexOf("</" + tag + ">");
            if (start >= 0 && end > start) {
                return body.substring(start + tag.length() + 2, end).strip();
            }
        }
        return "응답 내용을 확인할 수 없습니다.";
    }
}
