package server;

import common.Message;
import common.MessageType;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerState serverState;
    private String currentTokenId = null;

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
            System.out.println("Client disconnected: " + socket.getInetAddress().getHostAddress());
            serverState.handlePeerDisconnect(currentTokenId);
        }
    }



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

        else if (request.getType() == MessageType.ITEM_ACQUIRED) {
            System.out.println("[SERVER] Item " + request.getObjectId()
                    + " is now owned by: " + request.getMessage());
            Message response = new Message(MessageType.ITEM_ACQUIRED);
            response.setSuccess(true);
            response.setMessage("Item acquisition recorded");
            return response;
        }
        else {
            Message response = new Message(MessageType.ERROR);
            response.setSuccess(false);
            response.setMessage("Unknown request type");
            return response;
        }
    }

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



    private Message handleGetCurrentAuction(Message request) {
        Message response = new Message(MessageType.GET_CURRENT_AUCTION_RESPONSE);

        AuctionState currentAuction = serverState.getCurrentAuction();

        if (currentAuction == null || !currentAuction.isActive()) {
            response.setSuccess(false);
            response.setMessage("There is no active auction available");
            return response;
        }

        response.setSuccess(true);
        response.setMessage("Current auction fetched successfully");
        response.setObjectId(currentAuction.getItem().getObjectId());
        response.setDescription(currentAuction.getItem().getDescription());

        return response;
    }

    private Message handleGetAuctionDetails(Message request) {
        Message response = new Message(MessageType.GET_AUCTION_DETAILS_RESPONSE);

        AuctionState currentAuction = serverState.getCurrentAuction();

        if (currentAuction == null || !currentAuction.isActive()) {
            response.setSuccess(false);
            response.setMessage("There is no active auction available");
            return response;
        }

        response.setSuccess(true);
        response.setMessage("Auction details fetched successfully");
        response.setSellerUsername(currentAuction.getQueueEntry().getSellerUsername());
        response.setSellerTokenId(currentAuction.getQueueEntry().getSellerTokenId());
        response.setCurrentHighestBid(currentAuction.getCurrentHighestBid());
        response.setRemainingSeconds(currentAuction.getRemainingSeconds());

        return response;
    }

    private Message handlePlaceBid(Message request) {
        Message response = new Message(MessageType.PLACE_BID_RESPONSE);

        if (request.getBidAmount() == null) {
            response.setSuccess(false);
            response.setMessage("Missing bid amount.");
            return response;
        }

        ServerState.BidResult result = serverState.placeBid(
                request.getTokenId(),
                request.getBidAmount()
        );

        response.setSuccess(result.isSuccess());
        response.setMessage(result.getMessage());

        return response;
    }

}
