package server;

import java.net.ServerSocket;
import java.net.Socket;
/**
 * Η κύρια κλάση του Auction Server.
 * Ξεκινά τον server, αρχικοποιεί τον AuctionMonitor και αναμένει
 * συνδέσεις από peers. Για κάθε νέο peer δημιουργεί ξεχωριστό thread μέσω του ClientHandler ώστε να εξυπηρετούνται ταυτόχρονα.
 *
 */
public class AuctionServer {
    public static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        ServerState serverState = new ServerState();

        Thread auctionMonitorThread = new Thread(new AuctionMonitor(serverState));
        auctionMonitorThread.setDaemon(true);
        auctionMonitorThread.start();

        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            System.out.println("Auction Server started on port " + SERVER_PORT);

            while (true) {   // για να βρουμε νέα συνδεση απο peer
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from " + clientSocket.getInetAddress().getHostAddress());

                ClientHandler handler = new ClientHandler(clientSocket, serverState);
                Thread thread = new Thread(handler);
                thread.start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
