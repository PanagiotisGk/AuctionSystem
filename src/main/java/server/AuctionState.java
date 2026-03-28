package server;

import model.AuctionItem;

public class AuctionState {
    private final AuctionQueueEntry queueEntry;
    private double currentHighestBid;
    private String currentHighestBidderToken;
    private final long startTimeMillis;
    private final long endTimeMillis;
    private boolean active;

    public AuctionState(AuctionQueueEntry queueEntry) {
        this.queueEntry = queueEntry;
        this.currentHighestBid = queueEntry.getItem().getStartBid();
        this.currentHighestBidderToken = null;
        this.startTimeMillis = System.currentTimeMillis();
        this.endTimeMillis = startTimeMillis + (queueEntry.getItem().getAuctionDuration() * 1000L);
        this.active = true;
    }

    public AuctionQueueEntry getQueueEntry() {
        return queueEntry;
    }

    public synchronized double getCurrentHighestBid() {
        return currentHighestBid;
    }

    public synchronized void setCurrentHighestBid(double currentHighestBid) {
        this.currentHighestBid = currentHighestBid;
    }

    public synchronized String getCurrentHighestBidderToken() {
        return currentHighestBidderToken;
    }

    public synchronized void setCurrentHighestBidderToken(String currentHighestBidderToken) {
        this.currentHighestBidderToken = currentHighestBidderToken;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized void setActive(boolean active) {
        this.active = active;
    }

    public long getRemainingSeconds() {
        long remaining = (endTimeMillis - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 0);
    }

    public AuctionItem getItem() {
        return queueEntry.getItem();
    }

    @Override
    public String toString() {
        return "AuctionState{" +
                "item=" + queueEntry.getItem() +
                ", sellerUsername='" + queueEntry.getSellerUsername() + '\'' +
                ", currentHighestBid=" + currentHighestBid +
                ", currentHighestBidderToken='" + currentHighestBidderToken + '\'' +
                ", remainingSeconds=" + getRemainingSeconds() +
                ", active=" + active +
                '}';
    }
}
