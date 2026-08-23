package org.example.seniorlifebookingagent.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.HasContent;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Agent(description = "Answer hospital, transport policy, and product support questions from guides")
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true", matchIfMissing = true)
public class PolicyGuideAgent {

    private final ToolishRag policyGuides;

    PolicyGuideAgent(SearchOperations policySearch) {
        this.policyGuides = new ToolishRag(
            "policyGuides",
            "병원 이용 안내, 교통 정책, 도미 서비스 지원 범위",
            policySearch
        );
    }

    @Action
    @AchievesGoal(description = "Hospital or train policy question answered with official sources")
    public PolicyAnswer answer(UserInput userInput, OperationContext context) {
        return context.ai()
                      .withDefaultLlm()
                      .withReference(policyGuides)
                      .createObject(
                          """
                          다음 질문에 검색된 안내만 근거로 답하세요.
                          한국어 질문은 한국어로, 영어 질문은 영어로 답하세요.
                          highlights에는 가장 중요한 요점 1~3개를 각각 짧은 한 문장으로 작성하세요.
                          detail에는 요점을 이해하는 데 필요한 설명을 짧은 문장 3개 이내로 작성하세요.
                          한국어로 답할 때는 "~합니다", "~됩니다" 형태의 공손한 존댓말을 사용하세요.
                          문서 제목, 기관명, URL 등 출처 정보는 답변에 넣지 마세요.
                          근거에 없는 의료 준비사항이나 정책은 추측하지 말고 확인이 필요하다고 안내하세요.

                          질문: %s
                          """.formatted(userInput),
                          PolicyAnswer.class
                      );
    }

    public record PolicyAnswer(List<String> highlights, String detail) implements HasContent {
        public PolicyAnswer {
            highlights = highlights == null ? List.of() : List.copyOf(highlights);
            detail = detail == null ? "" : detail;
        }

        @Override
        public String getContent() {
            return String.join("\n", highlights) + "\n" + detail;
        }
    }
}
