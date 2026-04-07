package server;

public class User {
    private final String username;
    private final String password;
    private int numAuctionsSeller;
    private int numAuctionsBidder;

    /**
     *
     *
     * @param username
     * @param password
     */
    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.numAuctionsSeller = 0;
        this.numAuctionsBidder = 0;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getNumAuctionsSeller() {
        return numAuctionsSeller;
    }

    public void incrementNumAuctionsSeller() {
        this.numAuctionsSeller++;
    }

    public int getNumAuctionsBidder() {
        return numAuctionsBidder;
    }

    public void incrementNumAuctionsBidder() {
        this.numAuctionsBidder++;
    }
}
