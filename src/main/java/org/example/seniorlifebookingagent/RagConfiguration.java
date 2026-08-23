package org.example.seniorlifebookingagent;

import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.lucene.LuceneSearchOperations;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.ModelProvider;
import java.nio.file.Path;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true", matchIfMissing = true)
class RagConfiguration {

    @Bean
    LuceneSearchOperations policySearch(ModelProvider modelProvider) {
        return LuceneSearchOperations.withName("policy-guides")
                                     .withEmbeddingService(modelProvider.getEmbeddingService(
                                         DefaultModelSelectionCriteria.INSTANCE
                                     ))
                                     .withIndexPath(Path.of(".lucene-index"))
                                     .buildAndLoadChunks();
    }

    @Bean
    ApplicationRunner ingestPolicyGuides(LuceneSearchOperations policySearch) {
        return ignored -> {
            var reader = new TikaHierarchicalContentReader();
            var resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:rag/**/*.md");

            for (var resource : resources) {
                var uri = resource.getURI().toString();
                if (!policySearch.existsRootWithUri(uri)) {
                    try (var input = resource.getInputStream()) {
                        policySearch.writeAndChunkDocument(reader.parseContent(input, uri));
                    }
                }
            }
        };
    }
}
