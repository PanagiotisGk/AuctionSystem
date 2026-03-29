package server;

import java.net.ServerSocket;
import java.net.Socket;

public class AuctionServer {
    public static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        ServerState serverState = new ServerState();

        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            System.out.println("Auction Server started on port " + SERVER_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New peer connected from " + clientSocket.getInetAddress().getHostAddress());

                ClientHandler handler = new ClientHandler(clientSocket, serverState);
                Thread thread = new Thread(handler);
                thread.start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
