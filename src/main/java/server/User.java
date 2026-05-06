package server;

public class User {
    private final String username;
    private final String password;
    private int numAuctionsSeller;
    private int numAuctionsBidder;
    private double reputationScore;
    private static final double BETA = 0.25;

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
        this.reputationScore = 1.0;
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

    public double getReputationScore() {
        return reputationScore;
    }

    /**
     * Ενημερώνει το reputation_score βάσει του τύπου:
     * New reputation_score = (1 - β) * Old reputation_score + β * {0 ή 1}
     * όπου β = 0.25
     */
    public void updateReputation(boolean success) {
        double oldReputation = this.reputationScore;
        this.reputationScore = (1 - BETA) * this.reputationScore + BETA * (success ? 1.0 : 0.0);
        System.out.println("[REPUTATION] " + username + ": " + String.format("%.4f", oldReputation)
                + " -> " + String.format("%.4f", this.reputationScore)
                + (success ? " (επιτυχία)" : " (ακύρωση)"));
    }
}
