package org.example.seniorlifebookingagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class RagCorpusTest {

    @Test
    void everyGuideKeepsItsOfficialSourceAndReviewDate() throws Exception {
        var guides = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:rag/**/*.md");

        for (var guide : guides) {
            var text = guide.getContentAsString(StandardCharsets.UTF_8);
            assertThat(text).contains("source_url:", "checked_at:");
            if (!text.contains("category: product-support")) {
                assertThat(text).contains("source_url: https://");
            }
        }

        assertThat(guides).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void transportSupportGuideNamesUnsupportedModes() throws Exception {
        var guide = new PathMatchingResourcePatternResolver()
            .getResource("classpath:rag/transport/supported-modes.md")
            .getContentAsString(StandardCharsets.UTF_8);

        assertThat(guide).contains(
            "시내버스와 택시는 지원하지 않습니다",
            "노선, 요금, 할인, 호출 또는 예약",
            "기차·시외버스·고속버스"
        );
    }

    @Test
    void corpusDoesNotContainConflictingCityBusGuides() throws Exception {
        var guides = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:rag/**/*.md");

        for (var guide : guides) {
            assertThat(guide.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("category: city-bus");
        }
    }
}
