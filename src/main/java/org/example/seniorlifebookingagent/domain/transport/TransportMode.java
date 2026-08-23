package org.example.seniorlifebookingagent.domain.transport;

public enum TransportMode {
    TRAIN("기차"),
    INTERCITY_BUS("시외버스"),
    EXPRESS_BUS("고속버스");

    private final String displayName;

    TransportMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
