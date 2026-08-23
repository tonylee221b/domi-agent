package org.example.seniorlifebookingagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.example.seniorlifebookingagent.agent.PolicyGuideAgent.PolicyAnswer;
import org.junit.jupiter.api.Test;

class PolicyAnswerTest {

    @Test
    void keepsEmptyModelFieldsSafeForTheWeb() {
        var answer = new PolicyAnswer(null, null);

        assertEquals(0, answer.highlights().size());
        assertEquals("", answer.detail());
    }
}
