package peer;

import java.net.ServerSocket;
import java.net.Socket;

public class PeerListener implements Runnable {
    private final int port;

    public PeerListener(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Peer listener started on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Incoming connection from: " + socket.getInetAddress().getHostAddress());

                // Προς το παρόν απλά δεχόμαστε και κλείνουμε
                socket.close();
            }

        } catch (Exception e) {
            System.out.println("Peer listener error on port " + port);
            e.printStackTrace();
        }
    }
}
