package org.example.seniorlifebookingagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.example.seniorlifebookingagent.agent.PolicyGuideAgent.PolicyAnswer;
import org.junit.jupiter.api.Test;

class PolicyAnswerTest {

    @Test
    void keepsEmptyModelFieldsSafeForTheWeb() {
        var answer = new PolicyAnswer(null, null);

        assertEquals(0, answer.highlights().size());
        assertEquals("", answer.detail());
    }

    @Test
    void requiresFormalPoliteKoreanInEverySentence() {
        assertDoesNotThrow(() -> PolicyGuideAgent.ensurePoliteKorean(
            "택시는 지원하나요?",
            new PolicyAnswer(List.of("현재 택시는 지원하지 않습니다."), "기차를 이용할 수 있습니다.")));

        assertThrows(IllegalArgumentException.class, () -> PolicyGuideAgent.ensurePoliteKorean(
            "택시는 지원하나요?",
            new PolicyAnswer(List.of("현재 택시는 지원하지 않아."), "기차를 이용할 수 있습니다.")));
    }
}
