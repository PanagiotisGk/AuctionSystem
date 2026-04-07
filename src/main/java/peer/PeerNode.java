package peer;

import common.Message;
import common.MessageType;
import model.AuctionItem;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;


/**
 * Η κύρια κλάση του Peer/Bidder. Κάθε peer συνδέεται με τον Auction Server,
 * εγγράφεται, κάνει login, και μπορεί να δημοπρατεί αντικείμενα ή να κάνει bid σε δημοπρασίες.
 * Παράλληλα τρέχουν δύο background threads:
 * ---- ItemGenerator: παράγει αντικείμενα σε τυχαία χρονικά διαστήματα
 * ---- AutoBidder: ελέγχει και bidάρει αυτόματα σε δημοπρασίες
 */

public class PeerNode {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 8080;


    /**
     * Παράγει αντικείμενα σε τυχαία χρονικά διαστήματα και τα στέλνει
     * στον server για δημοπράτηση. Κάθε αντικείμενο αποθηκεύεται ως
     * .txt αρχείο στο shared directory του peer.
     * Ο χρόνος αναμονής είναι RAND * 120 seconds (30-120 sec).
     * Χρησιμοποιεί ξεχωριστό socket για κάθε αίτηση ώστε να αποφεύγονται
     * race conditions με το κύριο socket του peer.
     *
     * @param sharedDir  Το shared directory του peer
     * @param tokenId    Το token του peer για ταυτοποίηση
     * @param serverHost Η IP του server
     * @param serverPort Η θύρα του server
     * @param username   Το username του peer
     */
    private static void startItemGenerator(String sharedDir, String tokenId,
                                           String serverHost, int serverPort, String username) {
        new Thread(() -> {
            Random rand = new Random();
            int itemCounter = 1;

            while (true) {
                try {
                    // τυχαία αναμονή: RAND * 120 seconds (30-120 sec)
                    long waitSeconds = 30 + (long) (rand.nextDouble() * 90);
                    System.out.println("[GENERATOR] Next item in " + waitSeconds + " seconds...");
                    Thread.sleep(waitSeconds * 1000);

                    // Δημιουργία νέου αντικειμένου με τυχαία στοιχεία, συμφωνεί στο πρότυπο
                    String objectId = "Object_" + String.format("%02d", itemCounter++);
                    String description = "Auto-generated item " + objectId;
                    double startBid = 10 + rand.nextInt(190); // τυχαία τιμή 10-200
                    int duration = 30 + rand.nextInt(60); // τυχαία διάρκεια 30-90 sec

                    // γράψιμο αρχείου
                    File dir = new File(sharedDir);
                    if (!dir.exists()) dir.mkdirs();

                    File itemFile = new File(dir, objectId.toLowerCase() + ".txt");
                    try (PrintWriter writer = new PrintWriter(itemFile)) {
                        writer.println("object_id=" + objectId);
                        writer.println("description=" + description);
                        writer.println("start_bid=" + startBid);
                        writer.println("auction_duration=" + duration);
                    }
                    System.out.println("[GENERATOR] Created item: " + objectId);

                    // Νέο socket για αποστολή αντικειμένου στον server
                    try (
                            Socket socket = new Socket(serverHost, serverPort);
                            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
                    ) {
                        AuctionItem newItem = new AuctionItem(objectId, description, startBid, duration);
                        Message requestMsg = new Message(MessageType.REQUEST_AUCTION);
                        requestMsg.setTokenId(tokenId);
                        requestMsg.setItems(List.of(newItem));
                        out.writeObject(requestMsg);
                        out.flush();

                        Message response = (Message) in.readObject();
                        System.out.println("[GENERATOR] Server response: " + response.getMessage());
                    }

                } catch (Exception e) {
                    System.out.println("[GENERATOR] Error: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Ελέγχει αυτόματα ανά 1 λεπτό αν υπάρχει ενεργή δημοπρασία και αποφασίζει αν θα κάνει bid με πιθανότητα 60% (coin flip).
     * Αν αποφασίσει να bidάρει, υπολογίζει την προσφορά με τον τύπο:
     * NewBid = HighestBid * (1 + RAND/10) — αύξηση έως 10%.
     * Χρησιμοποιεί ξεχωριστό socket για κάθε κύκλο ώστε να αποφεύγονται race conditions με το κύριο socket του peer.
     * @param tokenId    Το token του peer για ταυτοποίηση
     * @param serverHost Η IP του server
     * @param serverPort Η θύρα του server
     */
    private static void startAutoBidder(String tokenId, String serverHost, int serverPort) {
        new Thread(() -> {
            Random rand = new Random();

            while (true) {
                try {
                    Thread.sleep(60 * 1000);

                    //νέα σύνδεση για κάθε auto-bid cycle γιατί χαλάει τα messages των λειτουργιών current και details
                    try (
                            Socket socket = new Socket(serverHost, serverPort);
                            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
                    ) {
                        //στοιχεία για την δημοπρασία, ώστε να ξεκινήσει το auto-bid
                        Message currentMsg = new Message(MessageType.GET_CURRENT_AUCTION);
                        currentMsg.setTokenId(tokenId);
                        out.writeObject(currentMsg);
                        out.flush();
                        Message currentResponse = (Message) in.readObject();

                        if (!Boolean.TRUE.equals(currentResponse.getSuccess())) {
                            System.out.println("[AUTO-BID] No active auction.");
                            continue;
                        }

                        System.out.println("[AUTO-BID] Current auction: " + currentResponse.getObjectId());

                        // Coin flip: 60% πιθανότητα ενδιαφέροντος
                        if (rand.nextDouble() > 0.6) {
                            System.out.println("[AUTO-BID] Not interested this time.");
                            continue;
                        }

                        // παίρνουμε λεπτομέρειες
                        Message detailsMsg = new Message(MessageType.GET_AUCTION_DETAILS);
                        detailsMsg.setTokenId(tokenId);
                        out.writeObject(detailsMsg);
                        out.flush();
                        Message detailsResponse = (Message) in.readObject();

                        if (!Boolean.TRUE.equals(detailsResponse.getSuccess())) {
                            System.out.println("[AUTO-BID] Could not get details.");
                            continue;
                        }

                        Double highestBid = detailsResponse.getCurrentHighestBid();
                        if (highestBid == null) {
                            System.out.println("[AUTO-BID] Could not get highest bid.");
                            continue;
                        }

                        // υπολογισμός νέας προσφοράς: NewBid = HighestBid * (1 + RAND/10)
                        double newBid = highestBid * (1 + rand.nextDouble() / 10);
                        newBid = Math.round(newBid * 100.0) / 100.0;
                        System.out.println("[AUTO-BID] Placing bid: " + newBid + " (was " + highestBid + ")");

                        // Καταχώρηση bid
                        Message bidMsg = new Message(MessageType.PLACE_BID);
                        bidMsg.setTokenId(tokenId);
                        bidMsg.setBidAmount(newBid);
                        out.writeObject(bidMsg);
                        out.flush();
                        Message bidResponse = (Message) in.readObject();
                        System.out.println("[AUTO-BID] " + bidResponse.getMessage());
                    }

                } catch (Exception e) {
                    System.out.println("[AUTO-BID] Error: " + e.getMessage());
                }
            }
        }).start();
    }


    /**
     * Κύρια μέθοδος εκτέλεσης του peer. Συνδέεται με τον server, κάνει register/login, φορτώνει τα
     * αντικείμενα του shared directory, και ξεκινά τους background threads.
     * Στη συνέχεια αναμένει εντολές από τον χρήστη.
     *
     * @param args [username, password, port]
     */
    public static void main(String[] args) {
        String username = args.length > 0 ? args[0] : "peer1";
        String password = args.length > 1 ? args[1] : "1234";
        int peerPort = args.length > 2 ? Integer.parseInt(args[2]) : 6001;

        System.out.println("Starting peer with username=" + username + ", port=" + peerPort);

        //αρχικοποίηση της κατάστασης του peer
        PeerState peerState = new PeerState(username, peerPort);


        try (
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connected to Auction Server");

            // Εκκίνηση του PeerListener για εισερχόμενες συνδέσεις, όπως
            // notifications από server, transaction requests από άλλους peers
            String sharedDir = "shared_directory_" + username;
            Thread listenerThread = new Thread(new PeerListener(peerPort, sharedDir, username, out));
            listenerThread.start();

            // εγγραφη στον server
            Message registerMsg = new Message(MessageType.REGISTER);
            registerMsg.setUsername(username);
            registerMsg.setPassword(password);
            out.writeObject(registerMsg);
            out.flush();

            Message registerResponse = (Message) in.readObject();
            System.out.println("REGISTER -> " + registerResponse.getMessage());

            // συνδεση και λήψη token
            Message loginMsg = new Message(MessageType.LOGIN);
            loginMsg.setUsername(username);
            loginMsg.setPassword(password);
            loginMsg.setPort(peerPort);
            out.writeObject(loginMsg);
            out.flush();
            //
            Message loginResponse = (Message) in.readObject();
            System.out.println("LOGIN -> " + loginResponse.getMessage());

            if (Boolean.TRUE.equals(loginResponse.getSuccess())) {
                peerState.setTokenId(loginResponse.getTokenId());
                System.out.println("TOKEN -> " + peerState.getTokenId());

            } else {
                System.out.println("Login failed. Peer will terminate.");
                return;
            }

            SharedDirectoryManager sharedDirectoryManager = new SharedDirectoryManager(sharedDir); // φορτωνει τα objects
            List<AuctionItem> items = sharedDirectoryManager.loadItems();

            System.out.println("Loaded items from shared_directory:");
            for (AuctionItem item : items) {
                System.out.println(item);
            }
            Message requestAuctionMsg = new Message(MessageType.REQUEST_AUCTION);
            requestAuctionMsg.setTokenId(peerState.getTokenId());
            requestAuctionMsg.setPort(peerState.getPeerPort());
            requestAuctionMsg.setItems(items);

            out.writeObject(requestAuctionMsg);
            out.flush();

            Message requestAuctionResponse = (Message) in.readObject();
            System.out.println("REQUEST_AUCTION -> " + requestAuctionResponse.getMessage());
            System.out.println("Peer is now active.");
            System.out.println("Type 'current' to get 'current' auction, 'details' for auction details\n" +
                    " , 'bid <amount> to bid in auction or  'logout' to terminate session.");

            //εδώ καλούμε να ξεκινήσει το random generator , αφού καλέσαμε τα request actions
            startItemGenerator(sharedDir, peerState.getTokenId(), SERVER_HOST, SERVER_PORT, username);
            startAutoBidder(peerState.getTokenId(), SERVER_HOST, SERVER_PORT);


            // κύριος βρόχος αναμονής εντολών από τον χρήστη
            while (true) {
                String input = scanner.nextLine();
                String command = input.trim();

                if ("logout".equalsIgnoreCase(command)) {
                    Message logoutMsg = new Message(MessageType.LOGOUT);
                    logoutMsg.setTokenId(peerState.getTokenId());
                    out.writeObject(logoutMsg);
                    out.flush();

                    Message logoutResponse = (Message) in.readObject();
                    System.out.println("LOGOUT -> " + logoutResponse.getMessage());

                    if (Boolean.TRUE.equals(logoutResponse.getSuccess())) {
                        peerState.clearSession();
                    }

                    break;

                } else if ("current".equalsIgnoreCase(command)) {
                    Message currentAuctionMsg = new Message(MessageType.GET_CURRENT_AUCTION);
                    currentAuctionMsg.setTokenId(peerState.getTokenId());

                    out.writeObject(currentAuctionMsg);
                    out.flush();

                    Message currentAuctionResponse = (Message) in.readObject();

                    if (Boolean.TRUE.equals(currentAuctionResponse.getSuccess())) {
                        System.out.println("CURRENT AUCTION:");
                        System.out.println("Object ID -> " + currentAuctionResponse.getObjectId());
                        System.out.println("Description -> " + currentAuctionResponse.getDescription());
                    } else {
                        System.out.println("GET_CURRENT_AUCTION -> " + currentAuctionResponse.getMessage());
                    }

                } else if ("details".equalsIgnoreCase(command)) {
                    Message detailsMsg = new Message(MessageType.GET_AUCTION_DETAILS);
                    detailsMsg.setTokenId(peerState.getTokenId());

                    out.writeObject(detailsMsg);
                    out.flush();

                    Message detailsResponse = (Message) in.readObject();

                    if (Boolean.TRUE.equals(detailsResponse.getSuccess())) {
                        System.out.println("AUCTION DETAILS:");
                        System.out.println("Seller Token -> " + detailsResponse.getSellerTokenId());
                        System.out.println("Seller Username -> " + detailsResponse.getSellerUsername());
                        System.out.println("Current Highest Bid -> " + detailsResponse.getCurrentHighestBid());
                        System.out.println("Remaining Seconds -> " + detailsResponse.getRemainingSeconds());
                    } else {
                        System.out.println("GET_AUCTION_DETAILS -> " + detailsResponse.getMessage());
                    }

                } else if (command.toLowerCase().startsWith("bid ")) {
                    String[] parts = command.split("\\s+", 2);

                    if (parts.length != 2) {
                        System.out.println("Usage: bid <amount>");
                        continue;
                    }

                    try {
                        double bidAmount = Double.parseDouble(parts[1]);

                        Message bidMsg = new Message(MessageType.PLACE_BID);
                        bidMsg.setTokenId(peerState.getTokenId());
                        bidMsg.setBidAmount(bidAmount);

                        out.writeObject(bidMsg);
                        out.flush();

                        Message bidResponse = (Message) in.readObject();
                        System.out.println("PLACE_BID -> " + bidResponse.getMessage());

                    } catch (NumberFormatException e) {
                        System.out.println("Invalid bid amount. Usage: bid <amount>");
                    }



                } else {
                    System.out.println("Unknown command. Eligible commands are: 'current', 'details', 'bid <amount>' or 'logout'.");
                }
            }

            System.out.println("Peer terminated.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}