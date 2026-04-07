package server;

public class SessionInfo {
    private final String tokenId;
    private final String username;
    private final String ipAddress;
    private final int port;

    /**
     * Πληροφορίες για την session, αρχικοποίηση με τα αντίστοιχα στοιχεία.
     *
     *
     * @param tokenId
     * @param username
     * @param ipAddress
     * @param port
     */
    public SessionInfo(String tokenId, String username, String ipAddress, int port) {
        this.tokenId = tokenId;
        this.username = username;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }
}
