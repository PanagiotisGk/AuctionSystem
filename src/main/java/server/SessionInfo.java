package server;

public class SessionInfo {
    private final String tokenId;
    private final String username;
    private final String ipAddress;
    private final int port;

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
