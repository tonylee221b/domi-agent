package org.example.seniorlifebookingagent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PublicDataClientTest {

    @Test
    void acceptsEncodedAndDecodedServiceKeysWithoutDoubleEncoding() {
        assertEquals(queryFor("abc+def/ghi="), queryFor("abc%2Bdef%2Fghi%3D"));
        assertFalse(queryFor("abc%2Bdef%2Fghi%3D").contains("%252B"));
    }

    @Test
    void readsPublicDataErrorReason() {
        assertEquals("등록되지 않은 서비스키", PublicDataClient.errorDetail(
            "<OpenAPI_ServiceResponse><returnAuthMsg>등록되지 않은 서비스키</returnAuthMsg></OpenAPI_ServiceResponse>"));
    }

    @Test
    void doesNotHideGatewayErrorAsEmptyResults() {
        var client = new PublicDataClient("test-key", request -> """
            <OpenAPI_ServiceResponse><cmmMsgHeader>
            <errMsg>SERVICE ERROR</errMsg><returnAuthMsg>SERVICE_ACCESS_DENIED_ERROR</returnAuthMsg>
            </cmmMsgHeader></OpenAPI_ServiceResponse>
            """);

        var error = assertThrows(IllegalStateException.class,
            () -> client.get("https://example.com", "/items", Map.of("pageNo", "1")));

        assertEquals("공공데이터 API 오류: SERVICE_ACCESS_DENIED_ERROR", error.getMessage());
    }

    private String queryFor(String key) {
        var uri = new AtomicReference<URI>();
        var client = new PublicDataClient(key, request -> {
            uri.set(request);
            return "<response><header><resultCode>00</resultCode></header><body><items/></body></response>";
        });
        client.get("https://example.com", "/items", Map.of("pageNo", "1"));
        return uri.get().getRawQuery();
    }
}
