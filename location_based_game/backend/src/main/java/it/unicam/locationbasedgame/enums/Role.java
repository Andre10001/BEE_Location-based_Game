package it.unicam.locationbasedgame.enums;

/**
 * The kind of account.
 */
public enum Role {

    player("Player"),
    admin("Administrator");

    /** String label for the role. */
    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}