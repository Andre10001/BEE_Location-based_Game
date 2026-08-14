package it.unicam.locationbasedgame.enums;

/**
 * Ownership status of an Outpost: unclaimed (neutral)
 * or belonging to one of the two teams.
 */
public enum OutpostState {

    neutral("Neutral"),
    team1("Team 1"),
    team2("Team 2");

    /** String label for this ownership status. */
    private final String label;

    OutpostState(String label) {
        this.label = label;
    }

    /**
     * Returns a string label for this ownership status.
     *
     * @return a string label of the state
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the Team currently owning this status, or null if the state
     * is neutral.
     *
     * @return the owner team, or null when neutral
     */
    public Team getOwnerTeam() {
        return switch (this) {
            case neutral -> null;
            case team1 -> Team.team1;
            case team2 -> Team.team2;
        };
    }
}
