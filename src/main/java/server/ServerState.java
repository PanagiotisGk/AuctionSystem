package server;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import common.Message;
import common.MessageType;
import model.AuctionItem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.net.Socket;
import java.io.ObjectOutputStream;


/**
 * Η κλάση ServerState διατηρεί την κατάσταση του Auction Server.
 * Αυτή περιλαμβάνει τις δομές δεδομένων για τους εγγεγραμμένους χρήστες,
 * τις ενεργές συνεδρίες, την ουρά δημοπρασιών και την τρέχουσα δημοπρασία.
 * Όλες οι μέθοδοι που τροποποιούν κοινόχρηστα δεδομένα είναι συγχρονισμένες
 * για αποφυγή race conditions.
 */

public class ServerState {

    //αποθηκεύει όλους τους εγγεγραμμένους χρήστες (username -> User)
    private final ConcurrentHashMap<String, User> registeredUsers = new ConcurrentHashMap<>();

    // αποθηκέυει τις ενεργες συνεδρίες
    private final ConcurrentHashMap<String, SessionInfo> activeSessionsByToken = new ConcurrentHashMap<>();

    // αντιστοιχιζει username -> token_id για γρήγορη αναζήτηση
    private final ConcurrentHashMap<String, String> usernameToToken = new ConcurrentHashMap<>();

    // φτιαχνουμε ουρά first come first served
    private final ConcurrentLinkedQueue<AuctionQueueEntry> auctionQueue = new ConcurrentLinkedQueue<>();

    //η τρέχουσα ενεργή δημοπρασία (την κανουμε volatile για να υπάρχει ορατότητα μεταξύ threads)
    private volatile AuctionState currentAuction;


    /**
     * Στέλνει notification σε έναν συγκεκριμένο peer μέσω socket.
     * Δημιουργεί νέο thread για να μην μπλοκάρει τον κύριο server thread.
     *
     * @param session   Τα στοιχεία επικοινωνίας του peer
     * @param eventType Ο τύπος του event (AUCTION_WON, AUCTION_SOLD, AUCTION_NO_BIDS)
     * @param auction   Η δημοπρασία που ολοκληρώθηκε
     * @param finalBid  Η τελική τιμή της δημοπρασίας
     */
    private void notifyPeer(SessionInfo session, String eventType, AuctionState auction, double finalBid) {
        if (session == null) return;
        new Thread(() -> {
            try (
                    Socket socket = new Socket(session.getIpAddress(), session.getPort());
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
            ) {
                Message msg = new Message(MessageType.NOTIFICATION);
                msg.setMessage(eventType);
                msg.setObjectId(auction.getItem().getObjectId());
                msg.setBidAmount(finalBid);

                //αν είναι ο winner, στέλνουμε και τα στοιχεία του seller για να ξεκινήσουμε το transaction
                if ("AUCTION_WON".equals(eventType)) {
                    SessionInfo sellerSession = activeSessionsByToken.get(
                            auction.getQueueEntry().getSellerTokenId()
                    );
                    System.out.println("sellerSession: " + sellerSession);
                    if (sellerSession != null) {
                        System.out.println("sellerIp: " + sellerSession.getIpAddress());
                        System.out.println("sellerPort: " + sellerSession.getPort());
                        msg.setSellerIp(sellerSession.getIpAddress());
                        msg.setSellerPort(sellerSession.getPort());
                    }
                }

                out.writeObject(msg);
                out.flush();
            } catch (Exception e) {
                System.out.println("Could not notify peer: " + session.getUsername());
            }
        }).start();
    }



    /**
     * Εγγράφει έναν νέο χρήστη στο σύστημα.
     * Ελέγχει αν το username χρησιμοποιείται ήδη.
     * Οι παραμετροι είναι ξεκάθαροι.
     */
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


    /**
     * Πραγματοποιεί login ενός χρήστη.
     * δημιουργεί μοναδικό toke_id για το session
     *
     * @param username
     * @param password
     * @param ipAddress
     * @param port
     * @return
     */
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


    /**
     * Ελέγχει αν η τρέχουσα δημοπρασία έχει λήξει και την οριστικοποιεί.
     * Καλείται περιοδικά από τον AuctionMonitor κάθε 1 δευτερόλεπτο.
     * Ενημερώνει τον winner και τον seller για το αποτέλεσμα.
     */
    public synchronized void checkAndFinalizeExpiredAuction() {



        try {
            // Αν δεν υπάρχει ενεργή δημοπρασία, δεν κάνουμε τίποτα
            if (currentAuction == null || !currentAuction.isActive()) {
                return;
            }

            // Αν δεν έχει λήξει ο χρόνος, δεν κάνουμε τίποτα
            if (currentAuction.getRemainingSeconds() > 0) {
                return;
            }

            // Αποθηκεύουμε την τελευταία κατάσταση πριν το reset
            AuctionState finishedAuction = currentAuction;
            double finalBid = finishedAuction.getCurrentHighestBid();
            String winnerToken = finishedAuction.getCurrentHighestBidderToken();


            // Βρίσκουμε τις sessions του seller και winner
            SessionInfo sellerSession = activeSessionsByToken.get(finishedAuction.getQueueEntry().getSellerTokenId());
            SessionInfo winnerSession = (winnerToken != null)  ? activeSessionsByToken.get(winnerToken) : null; // null αν δεν υπάρχει winner

            if (winnerToken == null) {
                finishedAuction.setStatus(AuctionState.AuctionStatus.NO_BIDS);
            } else {
                finishedAuction.setStatus(AuctionState.AuctionStatus.SOLD);
            }


            System.out.println("AUCTION FINISHED:");
            System.out.println("Item -> " + currentAuction.getItem().getObjectId());
            System.out.println("Seller -> " + currentAuction.getQueueEntry().getSellerUsername());
            System.out.println("Final Highest Bid -> " + currentAuction.getCurrentHighestBid());

            if (winnerToken == null) {
                // Δεν υπήρξε καμία προσφορά
                finishedAuction.setStatus(AuctionState.AuctionStatus.NO_BIDS);
                System.out.println("Result -> No bids were placed. No winner.");
                if (sellerSession != null) notifyPeer(sellerSession, "AUCTION_NO_BIDS", currentAuction, finalBid);
            } else {
                // Βρέθηκε winner
                String winnerUsername = (winnerSession != null) ? winnerSession.getUsername() : "UNKNOWN";
                System.out.println("Result -> Winner found");
                System.out.println("Winner Token -> " + winnerToken);
                System.out.println("Winner Username -> " + winnerUsername);

                //Αφού βρουμε winner και seller:
                if (winnerSession != null) notifyPeer(winnerSession, "AUCTION_WON", currentAuction, finalBid);
                if (sellerSession != null) notifyPeer(sellerSession, "AUCTION_SOLD", currentAuction, finalBid);

                // Αυξάνουμε τους μετρητές δημοπρασιών
                User sellerUser = registeredUsers.get(currentAuction.getQueueEntry().getSellerUsername());
                if (sellerUser != null) {
                    sellerUser.incrementNumAuctionsSeller();
                }

                if (winnerSession != null) {

                    User winnerUser = registeredUsers.get(winnerSession.getUsername());
                    if (winnerUser != null) {
                        winnerUser.incrementNumAuctionsBidder();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR inside checkAndFinalizeExpiredAuction:");
            e.printStackTrace();
            currentAuction = null; // reset για να μην ξαναμπεί
        }
        //Ξεκινάει η επόμενη
        startNextAuctionIfNeeded();
    }


    /**
     * Κλάση για την αποσύνδεση ενός peer.
     *Αφαιρεί τη session και ακυρώνει token_id
     * @param tokenId
     * @return
     */
    public synchronized String logoutUser(String tokenId) {
        SessionInfo sessionInfo = activeSessionsByToken.remove(tokenId);

        if (sessionInfo == null) {
            return "Invalid token";
        }

        // eκτύπωση στατιστικών peer κατά το logout
        User user = registeredUsers.get(sessionInfo.getUsername());
        if (user != null) {
            System.out.println("---PEER STATS ON LOGOUT---");
            System.out.println("Username -> " + user.getUsername());
            System.out.println("Auctions as Seller -> " + user.getNumAuctionsSeller());
            System.out.println("Auctions won as Bidder -> " + user.getNumAuctionsBidder());
            System.out.println("--------------------------");
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

    /**
     * Λαμβάνει τα αντικείμενα ενός peer προς δημοπράτηση και τα προσθέτει
     * στην ουρά FCFS. Ξεκινά αμέσως δημοπρασία αν δεν τρέχει ήδη κάποια.
     *
     * @param tokenId Το token του peer-πωλητή
     * @param items Λίστα αντικειμένων προς δημοπράτηση
     * @return
     */
    public synchronized String requestAuction(String tokenId, List<AuctionItem> items) {
        SessionInfo sessionInfo = activeSessionsByToken.get(tokenId);

        if (sessionInfo == null) {
            return "Invalid token";
        }

        if (items == null || items.isEmpty()) {
            return "No items provided";
        }

        // Προσθέτουμε κάθε αντικείμενο στην ουρά με μοναδικό auction_id
        for (AuctionItem item : items) {
            String auctionId = UUID.randomUUID().toString();

            AuctionQueueEntry entry = new AuctionQueueEntry(
                    auctionId,
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
    //Επιστρέφει στιγμιοτυπο της ουράς δημοπρασιων ώστε να υπάρχει ενημέρωση για το ποιές τρέχουν
    public List<AuctionQueueEntry> getAuctionQueueSnapshot() {
        return new ArrayList<>(auctionQueue);
    }

    /**
     * Ξεκινά την επόμενη δημοπρασία από την ουρά αν δεν τρέχει ήδη κάποια.
     * Αν η ουρά είναι κενή, περιμένει την άφιξη νέου αντικειμένου.
     */
    public synchronized void startNextAuctionIfNeeded() {
        if (currentAuction != null && currentAuction.isActive()) {
            return;
        }

        AuctionQueueEntry nextEntry = auctionQueue.poll();
        if (nextEntry == null) {
            currentAuction = null;
            System.out.println("No auction available in queue.");
            return;
        }

        // Δημιουργούμε νέα δημοπρασία
        currentAuction = new AuctionState(nextEntry);

        System.out.println("---NEW CURRENT AUCTION STARTED---");
        System.out.println("Auction ID -> " + currentAuction.getQueueEntry().getAuctionId());
        System.out.println("Item -> " + currentAuction.getItem().getObjectId());
        System.out.println("Description -> " + currentAuction.getItem().getDescription());
        System.out.println("Seller -> " + currentAuction.getQueueEntry().getSellerUsername());
        System.out.println("Start Bid -> " + currentAuction.getCurrentHighestBid());
        System.out.println("Duration -> " + currentAuction.getItem().getAuctionDuration() + " seconds");
    }

    /**
     *Επιστρέφει την τρέχουσα ενεργή δημοπρασία
     *
     * @return
     */
    public synchronized AuctionState getCurrentAuction() {
        if (currentAuction == null) {
            return null;
        }

        if (!currentAuction.isActive()) {
            return null;
        }

        return currentAuction;
    }

    // Υπεύθυνη για τα αποτελέσματα μιας προσφοράς
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


    /**
     * ενημερώνουμε όλους τους peers ότι αποχώρησε κάποιος από την δημοπρασία
     *
     * @param eventType
     * @param message
     */
    private void notifyAllPeers(String eventType, String message) {
        for (SessionInfo session : activeSessionsByToken.values()) {
            new Thread(() -> {
                try (
                        Socket socket = new Socket(session.getIpAddress(), session.getPort());
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
                ) {
                    Message msg = new Message(MessageType.NOTIFICATION);
                    msg.setMessage(eventType);
                    msg.setObjectId(message);
                    out.writeObject(msg);
                    out.flush();
                } catch (Exception e) {
                    System.out.println("Could not notify peer: " + session.getUsername());
                }
            }).start();
        }
    }

    /**
     * Αντιμετώπιση αποχώρισης ενός peer του οποίου
     * τα αντικείμενα βρίσκονταν σε ενεργή δημοπρασία
     *
     * @param tokenId
     */
    public synchronized void handlePeerDisconnect(String tokenId) {
        if (tokenId == null) return;

        //αφαιρούμε από την ουρά όλα τα αντικείμενα του peer
        auctionQueue.removeIf(entry -> {
            if (entry.getSellerTokenId().equals(tokenId)) {
                System.out.println("[INFO] Removing queued item: " + entry.getItem().getObjectId());
                return true;
            }
            return false;
        });

        //ακυρώνουμε την τρέχουσα δημοπρασία αν ο seller είναι αυτός που έπεσε
        if (currentAuction != null && currentAuction.isActive()) {
            String sellerTokenId = currentAuction.getQueueEntry().getSellerTokenId();
            if (sellerTokenId.equals(tokenId)) {
                System.out.println("[INFO] Removing active auction item: "
                        + currentAuction.getItem().getObjectId());
                System.out.println("Seller disconnected, maybe he didn't find a warm crowd... Cancelling auction: "
                        + currentAuction.getItem().getDescription());
                currentAuction.setStatus(AuctionState.AuctionStatus.CANCELLED);
                currentAuction = null;
            }
        }

        SessionInfo disconnectedSession = activeSessionsByToken.get(tokenId);
        String disconnectedUsername = (disconnectedSession != null) ? disconnectedSession.getUsername() : "Unknown";

        // Ενημέρωσε όλους τους peers
        notifyAllPeers("PEER_DISCONNECTED", disconnectedUsername + " has disconnected.");

        // επόμενο item
        startNextAuctionIfNeeded();
        logoutUser(tokenId);
    }


    /**
     * Για να χειριζόμαστε τα bids που πέφτουν σε αντικείμενα.
     *
     * @param bidderTokenId
     * @param bidAmount
     * @return
     */
    public synchronized BidResult placeBid(String bidderTokenId, double bidAmount) {
        SessionInfo bidderSession = activeSessionsByToken.get(bidderTokenId);

        if (bidderSession == null) {
            return new BidResult(false, "Invalid token");
        }

        AuctionState auction = getCurrentAuction();

        if (auction == null) { //το getCurrentAuction θα μου επιστρέψει ούτως ή αλλως κενό
            return new BidResult(false, "No active auction");
        }

        if (auction.getRemainingSeconds() <= 0) {
            checkAndFinalizeExpiredAuction();
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
        System.out.println("Bidder Id -> " + bidderSession.getUsername());
        System.out.println("New Highest Bid -> " + auction.getCurrentHighestBid());

        return new BidResult(true, "Bid placed successfully");
    }
}
