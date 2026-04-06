package server;

import model.AuctionItem;

public class AuctionQueueEntry {
    private final String sellerTokenId;
    private final String sellerUsername;
    private final String sellerIpAddress;
    private final int sellerPort;
    private final AuctionItem item;
    private final String auctionId;

    public AuctionQueueEntry(String auctionId, String sellerTokenId, String sellerUsername,
                             String sellerIpAddress, int sellerPort, AuctionItem item) {
        this.auctionId = auctionId;
        this.sellerTokenId = sellerTokenId;
        this.sellerUsername = sellerUsername;
        this.sellerIpAddress = sellerIpAddress;
        this.sellerPort = sellerPort;
        this.item = item;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getSellerTokenId() {
        return sellerTokenId;
    }

    public String getSellerUsername() {
        return sellerUsername;
    }

    public String getSellerIpAddress() {
        return sellerIpAddress;
    }

    public int getSellerPort() {
        return sellerPort;
    }

    public AuctionItem getItem() {
        return item;
    }

    @Override
    public String toString() {
        return "AuctionQueueEntry{" +
                "sellerTokenId='" + sellerTokenId + '\'' +
                ", sellerUsername='" + sellerUsername + '\'' +
                ", sellerIpAddress='" + sellerIpAddress + '\'' +
                ", sellerPort=" + sellerPort +
                ", item=" + item +
                '}';
    }
}
