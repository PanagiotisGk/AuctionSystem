package model;

import java.io.Serializable;

public class AuctionItem implements Serializable {
    private final String objectId;
    private final String description;
    private final double startBid;
    private final int auctionDuration;

    public AuctionItem(String objectId, String description, double startBid, int auctionDuration) {
        this.objectId = objectId;
        this.description = description;
        this.startBid = startBid;
        this.auctionDuration = auctionDuration;
    }

    public String getObjectId() {
        return objectId;
    }

    public String getDescription() {
        return description;
    }

    public double getStartBid() {
        return startBid;
    }

    public int getAuctionDuration() {
        return auctionDuration;
    }

    @Override
    public String toString() {
        return "AuctionItem{" +
                "objectId='" + objectId + '\'' +
                ", description='" + description + '\'' +
                ", startingBid=" + startBid +
                ", auctionDuration=" + auctionDuration +
                '}';
    }
}
