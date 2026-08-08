package Magic.mod;

/**
 * Implemented by modules that want {@link SkyPvPController} to run the SkyPvP rejoin
 * sequence (compass -> menu -> bow) for them.
 */
public interface SkyPvPParticipant {

    /**
     * Polled every tick while a sequence is running. Returning false aborts it, so a module
     * can pull out when the user turns the setting off or toggles the module back on.
     */
    public boolean isSkyPvPRejoinActive();

    /**
     * True when the player is in a state the sequence can work with: in a world, alive and
     * healthy again. Used both before the compass is opened (waits out the death and the
     * respawn in the lobby) and after the join (waits out the world loading).
     */
    public boolean isSkyPvPRejoinReady();

    /**
     * Called exactly once when the sequence stops, no matter how it ended.
     *
     * @param joined true when the player actually got moved somewhere else.
     */
    public void onSkyPvPRejoinFinished(boolean joined);
}
