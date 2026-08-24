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
        var answer = context.ai()
                            .withDefaultLlm()
                            .withReference(policyGuides)
                            .createObject(
                          """
                          다음 질문에 검색된 안내만 근거로 답하세요.
                          한국어 질문은 한국어로, 영어 질문은 영어로 답하세요.
                          highlights에는 가장 중요한 요점 1~3개를 각각 짧은 한 문장으로 작성하세요.
                          detail에는 요점을 이해하는 데 필요한 설명을 짧은 문장 3개 이내로 작성하세요.
                          한국어 답변의 모든 문장은 예외 없이 "~합니다.", "~됩니다."처럼 "~니다."로 끝내세요.
                          반말, 명사형 종결, "~해요" 형태는 사용하지 마세요.
                          문서 제목, 기관명, URL 등 출처 정보는 답변에 넣지 마세요.
                          근거에 없는 의료 준비사항이나 정책은 추측하지 말고 확인이 필요하다고 안내하세요.
                          서비스 지원 범위 질문은 도미 교통편 조회 지원 범위를 우선하세요.
                          시내버스나 택시에 관한 질문에는 현재 지원하지 않는다고 명확히 안내하고,
                          노선, 요금, 할인, 호출 방법을 대신 설명하거나 추측하지 마세요.

                          질문: %s
                          """.formatted(userInput),
                          PolicyAnswer.class
                      );
        ensurePoliteKorean(userInput.toString(), answer);
        return answer;
    }

    static void ensurePoliteKorean(String question, PolicyAnswer answer) {
        if (!question.matches(".*[가-힣].*")) {
            return;
        }
        var sentences = String.join("\n", answer.highlights()) + "\n" + answer.detail();
        for (var sentence : sentences.split("[.!?]+")) {
            if (!sentence.isBlank() && !sentence.strip().endsWith("니다")) {
                throw new IllegalArgumentException("한국어 안내는 공손한 '~니다'체로 작성해야 합니다.");
            }
        }
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
