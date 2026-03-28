package peer;

public class PeerState {
    private final String username;
    private final int peerPort;
    private String tokenId;
    private boolean loggedIn;

    public PeerState(String username, int peerPort) {
        this.username = username;
        this.peerPort = peerPort;
        this.loggedIn = false;
    }

    public String getUsername() {
        return username;
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

    public synchronized boolean isLoggedIn() {
        return loggedIn;
    }

    public synchronized void clearSession() {
        this.tokenId = null;
        this.loggedIn = false;
    }
}
