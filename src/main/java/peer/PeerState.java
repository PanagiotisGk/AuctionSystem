package peer;

public class PeerState {
    private final String username;
    private final int peerPort;
    private String tokenId;
    private boolean loggedIn;

    /**
     * Εδώ κρατάμε την κατάσταση του peer
     *
     * @param username
     * @param peerPort
     */
    public PeerState(String username, int peerPort) {
        this.username = username;
        this.peerPort = peerPort;
        this.loggedIn = false;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public synchronized String getTokenId() {
        return tokenId;
    }

    public synchronized void setTokenId(String tokenId) {
        this.tokenId = tokenId;
        this.loggedIn = (tokenId != null);
    }

    //Για μελλοντική χρήση αν χρειαστει να ελεχθει ότι ένας peer έιναι συνδεδεμένος
    public synchronized boolean isLoggedIn() {
        return loggedIn;
    }

    public synchronized void clearSession() {
        this.tokenId = null;
        this.loggedIn = false;
    }
}
