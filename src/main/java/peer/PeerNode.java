package peer;

import common.Message;
import common.MessageType;
import model.AuctionItem;
import java.util.List;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class PeerNode {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        String username = args.length > 0 ? args[0] : "peer1";
        String password = args.length > 1 ? args[1] : "1234";
        int peerPort = args.length > 2 ? Integer.parseInt(args[2]) : 6001;

        System.out.println("Starting peer with username=" + username + ", port=" + peerPort);

        PeerState peerState = new PeerState(username, peerPort);

        // ξεκινά local listener thread
        Thread listenerThread = new Thread(new PeerListener(peerPort));
        listenerThread.start();

        try (
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connected to Auction Server");

            // REGISTER
            Message registerMsg = new Message(MessageType.REGISTER);
            registerMsg.setUsername(username);
            registerMsg.setPassword(password);
            out.writeObject(registerMsg);
            out.flush();

            Message registerResponse = (Message) in.readObject();
            System.out.println("REGISTER -> " + registerResponse.getMessage());

            // LOGIN
            Message loginMsg = new Message(MessageType.LOGIN);
            loginMsg.setUsername(username);
            loginMsg.setPassword(password);
            loginMsg.setPort(peerPort);
            out.writeObject(loginMsg);
            out.flush();

            Message loginResponse = (Message) in.readObject();
            System.out.println("LOGIN -> " + loginResponse.getMessage());

            if (Boolean.TRUE.equals(loginResponse.getSuccess())) {
                peerState.setTokenId(loginResponse.getTokenId());
                System.out.println("TOKEN -> " + peerState.getTokenId());

            } else {
                System.out.println("Login failed. Peer will terminate.");
                return;
            }
            SharedDirectoryManager sharedDirectoryManager = new SharedDirectoryManager("shared_directory");
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