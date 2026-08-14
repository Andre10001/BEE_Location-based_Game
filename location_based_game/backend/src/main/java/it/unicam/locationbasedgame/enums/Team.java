package it.unicam.locationbasedgame.enums;

/**
 * The teams competing in the game.
 */
public enum Team {

    team1("Team 1"),
    team2("Team 2");

    /** String label for this team. */
    private final String label;

    Team(String label) {
        this.label = label;
    }

    /**
     * Returns a string label for this team, useful when displaying
     * the team name in a user interface.
     *
     * @return a string label of the team
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the team opposing this one. Used to identify the rival
     * team when a conquest attempt targets an owned outpost.
     *
     * @return the opposing team
     */
    public Team getOpponent() {
        return this == team1 ? team2 : team1;
    }
}
