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

        assertThat(guides).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    void transportSupportGuideNamesUnsupportedModes() throws Exception {
        var guide = new PathMatchingResourcePatternResolver()
            .getResource("classpath:rag/transport/supported-modes.md")
            .getContentAsString(StandardCharsets.UTF_8);

        assertThat(guide).contains("시내버스와 택시", "현재 지원하지 않는다");
    }
}
