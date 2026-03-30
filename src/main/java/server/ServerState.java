package server;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import model.AuctionItem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;


public class ServerState {
    private final ConcurrentHashMap<String, User> registeredUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SessionInfo> activeSessionsByToken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> usernameToToken = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<AuctionQueueEntry> auctionQueue = new ConcurrentLinkedQueue<>();
    private volatile AuctionState currentAuction;

    public synchronized String registerUser(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return "Username/password cannot be empty";
        }

        if (registeredUsers.containsKey(username)) {
            return "Username already exists";
        }

        registeredUsers.put(username, new User(username, password));
        return "SUCCESS";
    }

    public synchronized LoginResult loginUser(String username, String password, String ipAddress, int port) {
        User user = registeredUsers.get(username);

        if (user == null) {
            return new LoginResult(false, null, "User does not exist");
        }

        if (!user.getPassword().equals(password)) {
            return new LoginResult(false, null, "Wrong password");
        }

        if (usernameToToken.containsKey(username)) {
            return new LoginResult(false, null, "User already logged in");
        }

        String tokenId = UUID.randomUUID().toString();
        SessionInfo sessionInfo = new SessionInfo(tokenId, username, ipAddress, port);

        activeSessionsByToken.put(tokenId, sessionInfo);
        usernameToToken.put(username, tokenId);

        return new LoginResult(true, tokenId, "SUCCESS");
    }

    public synchronized String logoutUser(String tokenId) {
        SessionInfo sessionInfo = activeSessionsByToken.remove(tokenId);

        if (sessionInfo == null) {
            return "Invalid token";
        }

        usernameToToken.remove(sessionInfo.getUsername());
        return "SUCCESS";
    }

    public static class LoginResult {
        private final boolean success;
        private final String tokenId;
        private final String message;

        public LoginResult(boolean success, String tokenId, String message) {
            this.success = success;
            this.tokenId = tokenId;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getTokenId() {
            return tokenId;
        }

        public String getMessage() {
            return message;
        }
    }

    public synchronized String requestAuction(String tokenId, List<AuctionItem> items) {
        SessionInfo sessionInfo = activeSessionsByToken.get(tokenId);

        if (sessionInfo == null) {
            return "Invalid token";
        }

        if (items == null || items.isEmpty()) {
            return "No items provided";
        }

        for (AuctionItem item : items) {
            AuctionQueueEntry entry = new AuctionQueueEntry(
                    sessionInfo.getTokenId(),
                    sessionInfo.getUsername(),
                    sessionInfo.getIpAddress(),
                    sessionInfo.getPort(),
                    item
            );
            auctionQueue.add(entry);
        }
        startNextAuctionIfNeeded();
        return "SUCCESS";
    }

    public List<AuctionQueueEntry> getAuctionQueueSnapshot() {
        return new ArrayList<>(auctionQueue);
    }

    public synchronized void startNextAuctionIfNeeded() {
        if (currentAuction != null && currentAuction.isActive()) {
            return;
        }

        AuctionQueueEntry nextEntry = auctionQueue.poll();
        if (nextEntry == null) {
            currentAuction = null;
            return;
        }

        currentAuction = new AuctionState(nextEntry);
        System.out.println("NEW CURRENT AUCTION STARTED:");
        System.out.println(currentAuction);
    }

    public synchronized AuctionState getCurrentAuction() {
        if (currentAuction != null && currentAuction.isActive()) {
            if (currentAuction.getRemainingSeconds() <= 0) {
                currentAuction.setActive(false);
                System.out.println("AUCTION FINISHED:");
                System.out.println(currentAuction);
                startNextAuctionIfNeeded();
            }
        }
        return currentAuction;
    }


    public static class BidResult {
        private final boolean success;
        private final String message;

        public BidResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }


    public synchronized BidResult placeBid(String bidderTokenId, double bidAmount) {
        SessionInfo bidderSession = activeSessionsByToken.get(bidderTokenId);

        if (bidderSession == null) {
            return new BidResult(false, "Invalid token");
        }

        AuctionState auction = getCurrentAuction();

        if (auction == null || !auction.isActive()) {
            return new BidResult(false, "No active auction");
        }

        if (auction.getRemainingSeconds() <= 0) {
            auction.setActive(false);
            startNextAuctionIfNeeded();
            return new BidResult(false, "Sorry, auction has already ended");
        }

        String sellerTokenId = auction.getQueueEntry().getSellerTokenId();
        if (sellerTokenId.equals(bidderTokenId)) {
            return new BidResult(false, "Seller cannot bid on his/hers own auction");
        }

        if (bidAmount <= auction.getCurrentHighestBid()) {
            return new BidResult(false, "Bid must be greater than current highest bid");
        }

        boolean updated = auction.placeBid(bidderTokenId, bidAmount);
        if (!updated) {
            return new BidResult(false, "Bid rejected");
        }

        System.out.println("NEW BID ACCEPTED:");
        System.out.println("Item -> " + auction.getItem().getObjectId());
        System.out.println("Bidder Token -> " + bidderTokenId);
        System.out.println("New Highest Bid -> " + auction.getCurrentHighestBid());

        return new BidResult(true, "Bid placed successfully");
    }
}
