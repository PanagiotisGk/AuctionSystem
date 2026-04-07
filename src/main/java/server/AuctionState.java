package server;

import model.AuctionItem;

/**
 * Η κλάση AuctionState αναπαριστά την κατάσταση μιας δημοπρασίας.
 * Διατηρεί πληροφορίες για το αντικείμενο, την τρέχουσα υψηλότερη
 * προσφορά, τον χρόνο έναρξης και λήξης αλλά και την κατάσταση της δημοπρασίας. Οι μέθοδοι είναι συγχρονισμένες(synchronized) για ασφαλή πρόσβαση από πολλά threads.
 */

public class AuctionState {
    private final AuctionQueueEntry queueEntry;
    private double currentHighestBid;
    private String currentHighestBidderToken;
    private final long startTimeMillis;
    private final long endTimeMillis;
    private AuctionStatus status;

    public AuctionState(AuctionQueueEntry queueEntry) {
        this.queueEntry = queueEntry;
        this.currentHighestBid = queueEntry.getItem().getStartBid();
        this.currentHighestBidderToken = null;
        this.startTimeMillis = System.currentTimeMillis();
        this.endTimeMillis = startTimeMillis + (queueEntry.getItem().getAuctionDuration() * 1000L);
        this.status = AuctionStatus.ACTIVE;
    }

    /**
     * Enum με τις δυνατές καταστάσεις μιας δημοπρασίας:
     * ACTIVE    - Σε εξέλιξη
     * SOLD      - Ολοκληρώθηκε με winner
     * NO_BIDS   - Ολοκληρώθηκε χωρίς προσφορές
     * CANCELLED - Ακυρώθηκε λόγω αποσύνδεσης seller
     */
    public enum AuctionStatus {
        ACTIVE,
        SOLD,
        NO_BIDS,
        CANCELLED
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
    public synchronized AuctionStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(AuctionStatus status) {
        this.status = status;
    }

    // ελέγχουμε αν η δημοπρασία είναι ενεργή
    public synchronized boolean isActive() {
        return status == AuctionStatus.ACTIVE;
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
                ", active=" + isActive() +
                '}';
    }

    /**
     * Καταχωρεί μια νέα προσφορά αν η δημοπρασία είναι ενεργή
     * και η νέα προσφορά είναι μεγαλύτερη από την τρέχουσα.
     *
     * @param bidderTokenId το token του peer που κάνει την προσφορά
     * @param newBidAmount το ποσό της νέας προσφοράς
     * @return true αν η προσφορα έγινε αποδεκτή και false αλλιώς
     */
    public synchronized boolean placeBid(String bidderTokenId, double newBidAmount) {
        if (!isActive()) {
            return false;
        }

        if (newBidAmount <= currentHighestBid) {
            return false;
        }

        currentHighestBid = newBidAmount;
        currentHighestBidderToken = bidderTokenId;
        return true;
    }
}
