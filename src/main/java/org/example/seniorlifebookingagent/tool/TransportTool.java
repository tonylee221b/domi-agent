package org.example.seniorlifebookingagent.tool;

import java.util.List;
import org.example.seniorlifebookingagent.domain.transport.TransportPlan;
import org.example.seniorlifebookingagent.domain.transport.TransportRequest;

public interface TransportTool {
    List<TransportPlan> search(TransportRequest request);
}
