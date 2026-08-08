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
     * Called exactly once when the sequence stops, no matter how it ended.
     *
     * @param joined true when the player actually got moved to another world.
     */
    public void onSkyPvPRejoinFinished(boolean joined);
}
