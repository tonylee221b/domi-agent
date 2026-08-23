package org.example.seniorlifebookingagent.tool;

import java.util.List;
import org.example.seniorlifebookingagent.domain.hospital.HospitalAppointment;
import org.example.seniorlifebookingagent.domain.hospital.HospitalRequest;

public interface HospitalTool {
    List<HospitalAppointment> search(HospitalRequest request);

    default SearchPage searchPage(HospitalRequest request, int page, int size) {
        return new SearchPage(page == 1 ? search(request) : List.of(), false);
    }

    record SearchPage(List<HospitalAppointment> appointments, boolean hasMore) {
        public SearchPage {
            appointments = List.copyOf(appointments);
        }
    }
}
