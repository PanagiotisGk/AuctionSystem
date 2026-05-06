package server;

import common.Message;
import common.MessageType;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerState serverState;
    private String currentTokenId = null;


    /**
     * Αρχικοποιούμε
     *
     * @param socket
     * @param serverState
     */
    public ClientHandler(Socket socket, ServerState serverState) {
        this.socket = socket;
        this.serverState = serverState;
    }

    @Override
    public void run() {
        try (
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            while (true) {
                Object obj = in.readObject();

                if (!(obj instanceof Message request)) {
                    continue;
                }

                Message response = handleRequest(request);
                out.writeObject(response);
                out.flush();
            }

        } catch (Exception exc) {
            if (currentTokenId != null) {
                System.out.println("[DISCONNECT] Peer disconnected (token: " + currentTokenId + ")");
                serverState.handlePeerDisconnect(currentTokenId);
            } else {
                System.out.println("[INFO] Short-lived connection closed from " + socket.getInetAddress().getHostAddress());
            }
        }
    }


    /**
     * Αντιμετωπίζει όλα τα requests που κάνει ένας client
     *
     * @param request
     * @return
     */
    private Message handleRequest(Message request) {
        if (request.getType() == MessageType.REGISTER) {
            return handleRegister(request);
        } else if (request.getType() == MessageType.LOGIN) {
            return handleLogin(request);
        } else if (request.getType() == MessageType.LOGOUT) {
            return handleLogout(request);
        }else if (request.getType() == MessageType.REQUEST_AUCTION) {
        return handleRequestAuction(request);

        } else if (request.getType() == MessageType.GET_CURRENT_AUCTION) {
            return handleGetCurrentAuction(request);

        } else if (request.getType() == MessageType.GET_AUCTION_DETAILS) {
            return handleGetAuctionDetails(request);


        } else if (request.getType() == MessageType.PLACE_BID) {
            return handlePlaceBid(request);
        }
        // μήνυμα για όταν ένας peer αγοράζει ένα item και αυτό μεταφέρεται στον dir του
        else if (request.getType() == MessageType.ITEM_ACQUIRED) {
            System.out.println("[SERVER] Item " + request.getObjectId()
                    + " is now owned by: " + request.getMessage());
            Message response = new Message(MessageType.ITEM_ACQUIRED);
            response.setSuccess(true);
            response.setMessage("Item acquisition recorded");
            return response;
        }

        else if (request.getType() == MessageType.TRANSACTION_SUCCESS) {
            String buyerUsername = request.getUsername();
            System.out.println("[SERVER] Transaction SUCCESS for buyer: " + buyerUsername);
            serverState.updateReputation(buyerUsername, true);
            Message response = new Message(MessageType.TRANSACTION_SUCCESS);
            response.setSuccess(true);
            response.setMessage("Reputation updated (success)");
            return response;
        }
        else if (request.getType() == MessageType.TRANSACTION_FAILED) {
            String buyerUsername = request.getUsername();
            System.out.println("[SERVER] Transaction FAILED (cancelled) by buyer: " + buyerUsername);
            serverState.updateReputation(buyerUsername, false);
            Message response = new Message(MessageType.TRANSACTION_FAILED);
            response.setSuccess(true);
            response.setMessage("Reputation updated (failure)");
            return response;
        }
        else {
            Message response = new Message(MessageType.ERROR);
            response.setSuccess(false);
            response.setMessage("Unknown request type");
            return response;
        }


    }

    /**
     * Είσοδος ενός user
     *
     *
     * @param request
     * @return
     */
    private Message handleRegister(Message request) {
        String result = serverState.registerUser(request.getUsername(), request.getPassword());

        Message response = new Message(MessageType.REGISTER_RESPONSE);
        if ("SUCCESS".equals(result)) {
            response.setSuccess(true);
            response.setMessage("User registered successfully");
        } else {
            response.setSuccess(false);
            response.setMessage(result);
        }
        return response;
    }


    /**
     * Είσοδος με στοιχεία peer
     *
     * @param request
     * @return
     */
    private Message handleLogin(Message request) {
        String clientIp = socket.getInetAddress().getHostAddress();
        int peerPort = request.getPort() != null ? request.getPort() : -1;

        ServerState.LoginResult result = serverState.loginUser(
                request.getUsername(),
                request.getPassword(),
                clientIp,
                peerPort
        );

        if (result.isSuccess()) {
            currentTokenId = result.getTokenId(); // αποθήκευουμε το token
        }

        Message response = new Message(MessageType.LOGIN_RESPONSE);
        response.setSuccess(result.isSuccess());
        response.setMessage(result.getMessage());
        response.setTokenId(result.getTokenId());
        return response;
    }


    /**
     * Έξοδος peer
     *
     * @param request
     * @return
     */
    private Message handleLogout(Message request) {
        String result = serverState.logoutUser(request.getTokenId());

        Message response = new Message(MessageType.LOGOUT_RESPONSE);
        if ("SUCCESS".equals(result)) {
            response.setSuccess(true);
            response.setMessage("Logout successful");
        } else {
            response.setSuccess(false);
            response.setMessage(result);
        }
        return response;
    }

    /**
     * Καταχώρηση αντικειμένων για δημοπρασία
     *
     * @param request
     * @return
     */
    private Message handleRequestAuction(Message request) {
        String result = serverState.requestAuction(request.getTokenId(), request.getItems());

        Message response = new Message(MessageType.REQUEST_AUCTION_RESPONSE);
        if ("SUCCESS".equals(result)) {
            response.setSuccess(true);
            response.setMessage("Auction items submitted successfully");
            System.out.println("REQUEST_AUCTION received for token=" + request.getTokenId());

            for (AuctionQueueEntry entry : serverState.getAuctionQueueSnapshot()) {
                System.out.println(entry);
            }
        } else {
            response.setSuccess(false);
            response.setMessage(result);
        }

        return response;
    }


    /**
     * Εντολή current από τον peer, επιστρέφει την τρέχουσα δημοπρασία
     *
     * @param request
     * @return
     */
    private Message handleGetCurrentAuction(Message request) {
        Message response = new Message(MessageType.GET_CURRENT_AUCTION_RESPONSE);

        List<AuctionState> auctions = serverState.getCurrentAuctions();

        if (auctions.isEmpty()) {
            response.setSuccess(false);
            response.setMessage("There is no active auction available");
            return response;
        }

        // Στέλνουμε τις πληροφορίες ως formatted string
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < auctions.size(); i++) {
            AuctionState a = auctions.get(i);
            if (i > 0) sb.append(" | ");
            sb.append("[").append(i + 1).append("] ")
                    .append(a.getItem().getObjectId())
                    .append(" - ").append(a.getItem().getDescription())
                    .append(" (ID: ").append(a.getQueueEntry().getAuctionId()).append(")");
        }

        response.setSuccess(true);
        response.setMessage(sb.toString());
        // Για backward compatibility, βάζουμε τα στοιχεία της 1ης δημοπρασίας
        response.setObjectId(auctions.get(0).getItem().getObjectId());
        response.setDescription(auctions.get(0).getItem().getDescription());
        response.setAuctionId(auctions.get(0).getQueueEntry().getAuctionId());

        return response;
    }

    /**
     * Εντολή details, επιστρέφει τις λεπτομέρειες της δημοπρασίας
     *
     * @param request
     * @return
     */
    private Message handleGetAuctionDetails(Message request) {
        Message response = new Message(MessageType.GET_AUCTION_DETAILS_RESPONSE);

        String auctionId = request.getAuctionId();
        AuctionState auction = null;

        if (auctionId != null) {
            auction = serverState.getAuctionById(auctionId);
        } else {
            // Fallback: πρώτη ενεργή δημοπρασία
            List<AuctionState> auctions = serverState.getCurrentAuctions();
            if (!auctions.isEmpty()) {
                auction = auctions.get(0);
            }
        }

        if (auction == null || !auction.isActive()) {
            response.setSuccess(false);
            response.setMessage("There is no active auction available");
            return response;
        }

        response.setSuccess(true);
        response.setMessage("Auction details fetched successfully");
        response.setAuctionId(auction.getQueueEntry().getAuctionId());
        response.setSellerUsername(auction.getQueueEntry().getSellerUsername());
        response.setSellerTokenId(auction.getQueueEntry().getSellerTokenId());
        response.setCurrentHighestBid(auction.getCurrentHighestBid());
        response.setRemainingSeconds(auction.getRemainingSeconds());
        response.setAuctionDuration((long) auction.getItem().getAuctionDuration());

        return response;
    }


    /**
     * Εντολή καταχώρησης bid, επιστρέφει την επιτυχή καταχώρηση bid ή την αποτυχία αυτής.
     *
     * @param request
     * @return
     */
    private Message handlePlaceBid(Message request) {
        Message response = new Message(MessageType.PLACE_BID_RESPONSE);

        if (request.getBidAmount() == null) {
            response.setSuccess(false);
            response.setMessage("Missing bid amount.");
            return response;
        }

        ServerState.BidResult result = serverState.placeBid(
                request.getTokenId(),
                request.getAuctionId(),
                request.getBidAmount()
        );

        response.setSuccess(result.isSuccess());
        response.setMessage(result.getMessage());

        return response;
    }

}
