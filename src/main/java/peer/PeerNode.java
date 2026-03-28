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
    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {
        String username = "peer1";
        String password = "1234";
        int peerPort = 6001;

        PeerState peerState = new PeerState(username, peerPort);

        // Ξεκινά local listener thread
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
            System.out.println("Type 'logout' to logout and exit.");

            while (true) {
                String input = scanner.nextLine();

                if ("logout".equalsIgnoreCase(input.trim())) {
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
                } else {
                    System.out.println("Unknown command. Type 'logout' to exit.");
                }
            }

            System.out.println("Peer terminated.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}