package peer;

import common.Message;
import common.MessageType;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Random;


/**
 * Η κλάση PeerListener τρέχει σε ξεχωριστό thread και ακούει για
 * εισερχόμενες συνδέσεις στη θύρα του peer. Χειρίζεται δύο τύπους
 * εισερχόμενων μηνυμάτων:
 * 1. NOTIFICATION από τον server (AUCTION_WON, AUCTION_SOLD, PEER_DISCONNECTED κλπ.)
 * 2. TRANSACTION_REQUEST από άλλον peer (winner που θέλει να αγοράσει αντικείμενο)
 * Η μεταφορά αρχείων (transaction) γίνεται μέσω UDP με πρωτόκολλο Go-Back-N,
 * ενώ ο συντονισμός (handshake) παραμένει TCP.
 */
public class PeerListener implements Runnable {
    private final int port;
    private final String sharedDirectoryPath;
    private final String username;
    private ObjectOutputStream serverOut;

    public PeerListener(int port, String sharedDirectoryPath, String username, ObjectOutputStream serverOut) {
        this.port = port;
        this.sharedDirectoryPath = sharedDirectoryPath;
        this.username = username;
        this.serverOut = serverOut;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Peer listener started on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                // κάθε σύνδεση εξυπηρετείται σε ξεχωριστό thread
                new Thread(() -> handleIncoming(socket)).start();
            }

        } catch (Exception e) {
            System.out.println("Peer listener error on port " + port);
            e.printStackTrace();
        }
    }


    /**
     * Χειρίζεται μια εισερχόμενη σύνδεση.
     * Διαβάζει το μήνυμα και το προωθεί στον κατάλληλο handler
     * ανάλογα με τον τύπο του.
     *
     * @param socket Το socket της εισερχόμενης σύνδεσης
     */
    private void handleIncoming(Socket socket) {
        try (
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
        ) {
            Message msg = (Message) in.readObject();

            if (msg.getType() == MessageType.NOTIFICATION) {
                System.out.println("\n[NOTIFICATION] " + msg.getMessage());

                if ("AUCTION_WON".equals(msg.getMessage())) {
                    System.out.println("sellerIp: " + msg.getSellerIp());
                    System.out.println("sellerPort: " + msg.getSellerPort());
                    handleWinnerTransaction(msg);
                } else if ("PEER_DISCONNECTED".equals(msg.getMessage())) {
                    System.out.println("[INFO] " + msg.getObjectId());
                } else {
                    System.out.println("Item: " + msg.getObjectId()
                            + " | Final Bid: " + msg.getBidAmount());
                }

            } else if (msg.getType() == MessageType.TRANSACTION_REQUEST) {
                handleTransactionRequest(msg, out);
            }

        } catch (Exception e) {
            System.out.println("Error handling incoming connection: " + e.getMessage());
        }
    }


    /**
     * Χειρίζεται το transaction ως αγοραστής (winner).
     * Ροή:
     * 1. Αποφασίζει αν θα προχωρήσει (70%) ή θα ακυρώσει (30%)
     * 2. Αν ακυρώσει → ενημερώνει τον server (TRANSACTION_FAILED, reputation μειώνεται)
     * 3. Αν προχωρήσει → συνδέεται TCP στον seller, στέλνει TRANSACTION_REQUEST
     * 4. Ο seller απαντάει με UDP port
     * 5. Ο buyer τρέχει GBNReceiver για να λάβει το αρχείο μέσω UDP Go-Back-N
     * 6. Αποθηκεύει το αρχείο και ενημερώνει τον server
     *
     * @param notification Το notification AUCTION_WON με τα στοιχεία του seller
     */
    private void handleWinnerTransaction(Message notification) {
        new Thread(() -> {
            Random rand = new Random();

            // === Απόφαση αγοραστή: 70% προχωράει, 30% ακυρώνει ===
            double decision = rand.nextDouble();
            if (decision > 0.70) {
                System.out.println("[TRANSACTION] Buyer decided to CANCEL the purchase (probability: 30%)");
                // Ενημέρωση server ότι ακυρώθηκε — reputation μειώνεται
                notifyServerTransactionResult(false);
                return;
            }

            System.out.println("[TRANSACTION] Buyer decided to PROCEED with purchase (probability: 70%)");
            System.out.println("[TRANSACTION] Attempting to connect to seller...");

            try (
                    Socket sellerSocket = new Socket(notification.getSellerIp(), notification.getSellerPort());
                    ObjectOutputStream sellerOut = new ObjectOutputStream(sellerSocket.getOutputStream());
                    ObjectInputStream sellerIn = new ObjectInputStream(sellerSocket.getInputStream())
            ) {
                // Στέλνουμε TRANSACTION_REQUEST στον seller μέσω TCP
                Message request = new Message(MessageType.TRANSACTION_REQUEST);
                request.setObjectId(notification.getObjectId());
                sellerOut.writeObject(request);
                sellerOut.flush();

                // Ο seller απαντάει με την UDP θύρα που θα στείλει τα δεδομένα
                Message response = (Message) sellerIn.readObject();

                if (!Boolean.TRUE.equals(response.getSuccess())) {
                    System.out.println("[TRANSACTION] Seller rejected: " + response.getMessage());
                    notifyServerTransactionResult(false);
                    return;
                }

                // Παίρνουμε την UDP θύρα και το όνομα αρχείου
                int udpPort = response.getPort();
                String fileName = response.getFileName();

                System.out.println("[TRANSACTION] Seller ready. Receiving file via UDP Go-Back-N on port " + udpPort);

                //Λήψη αρχείου μέσω Go-Back-N UDP
                peer.GobackNreceiver receiver = new peer.GobackNreceiver(udpPort);
                byte[] fileData = receiver.receive();

                if (fileData == null) {
                    System.out.println("[TRANSACTION] Failed to receive file via Go-Back-N.");
                    notifyServerTransactionResult(false);
                    return;
                }

                // Αποθήκευση αρχείου στο shared directory
                File dir = new File(sharedDirectoryPath);
                if (!dir.exists()) dir.mkdirs();

                File newFile = new File(dir, fileName);
                Files.write(newFile.toPath(), fileData);
                System.out.println("[TRANSACTION] File received and saved: " + fileName
                        + " (" + fileData.length + " bytes)");

                //ενημέρωση server ότι ο αγοραστής έχει πλέον το αντικείμενο
                //μέσω ξεχωριστού socket
                try (
                        Socket serverSocket = new Socket("127.0.0.1", 8080);
                        ObjectOutputStream sOut = new ObjectOutputStream(serverSocket.getOutputStream());
                        ObjectInputStream sIn = new ObjectInputStream(serverSocket.getInputStream())
                ) {
                    Message updateMsg = new Message(MessageType.ITEM_ACQUIRED);
                    updateMsg.setObjectId(notification.getObjectId());
                    updateMsg.setMessage(username);
                    sOut.writeObject(updateMsg);
                    sOut.flush();
                    Message ack = (Message) sIn.readObject();
                    System.out.println("[TRANSACTION] Server: " + ack.getMessage());
                } catch (Exception ex) {
                    System.out.println("[TRANSACTION] Could not notify server of acquisition: " + ex.getMessage());
                }

                // Ενημέρωση server: επιτυχές transaction → reputation αυξάνεται
                notifyServerTransactionResult(true);

            } catch (Exception e) {
                System.out.println("[TRANSACTION] Error connecting to seller: " + e.getMessage());
                notifyServerTransactionResult(false);
            }
        }).start();
    }


    /**
     * Χειρίζεται το transaction ως πωλητής (seller).
     * Ροή:
     * 1. Βρίσκει το αρχείο στο shared directory
     * 2. Απαντάει στον buyer μέσω TCP με την UDP θύρα
     * 3. Ξεκινά GBNSender και στέλνει το αρχείο μέσω UDP Go-Back-N
     * 4. Αν επιτυχές, διαγράφει το αρχείο από τον δικό του shared directory
     *
     * @param request Το TRANSACTION_REQUEST με το object_id
     * @param out     Η TCP έξοδος προς τον αγοραστή (για handshake)
     */
    private void handleTransactionRequest(Message request, ObjectOutputStream out) {
        try {
            String objectId = request.getObjectId();
            File dir = new File(sharedDirectoryPath);
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));

            File targetFile = null;
            if (files != null) {
                for (File f : files) {
                    if (f.getName().toLowerCase().contains(objectId.toLowerCase())) {
                        targetFile = f;
                        break;
                    }
                }
            }

            Message response = new Message(MessageType.TRANSACTION_RESPONSE);

            if (targetFile == null) {
                response.setSuccess(false);
                response.setMessage("File not found for object: " + objectId);
                out.writeObject(response);
                out.flush();
                return;
            }

            // Διαβάζουμε το αρχείο
            byte[] fileContent = Files.readAllBytes(targetFile.toPath());
            String fileName = targetFile.getName();

            // Επιλέγουμε τυχαία UDP θύρα για Go-Back-N
            int udpPort = 10000 + new Random().nextInt(50000);

            // Απαντάμε στον buyer μέσω TCP: "ετοιμάσου, θα στείλω στη θύρα udpPort"
            response.setSuccess(true);
            response.setMessage("READY_FOR_GBN");
            response.setPort(udpPort);
            response.setFileName(fileName);
            out.writeObject(response);
            out.flush();

            System.out.println("[TRANSACTION] Sending file via UDP Go-Back-N on port " + udpPort);
            System.out.println("[TRANSACTION] File: " + fileName + " (" + fileContent.length + " bytes)");

            // Μικρή αναμονή ώστε ο buyer να προλάβει να ανοίξει τον GBNReceiver
            Thread.sleep(500);

            // === Αποστολή αρχείου μέσω Go-Back-N ===
            InetAddress buyerAddress = InetAddress.getByName(
                    request.getSellerIp() != null ? request.getSellerIp() : "127.0.0.1"
            );

            // Η IP του buyer είναι η IP από την οποία ήρθε το TCP request
            // (δεν μπορούμε να την πάρουμε εδώ, χρησιμοποιούμε localhost)
            // Σε πραγματικό δίκτυο θα έπαιρνε socket.getInetAddress()

            peer.GobackNsender sender = new peer.GobackNsender(fileContent, buyerAddress, udpPort);
            boolean success = sender.send();

            if (success) {
                // Διαγράφουμε το αρχείο από τον seller
                if (targetFile.delete()) {
                    System.out.println("[TRANSACTION] File sent and deleted: " + fileName);
                }
            } else {
                System.out.println("[TRANSACTION] Go-Back-N transfer failed for: " + fileName);
            }

        } catch (Exception e) {
            System.out.println("Error during transaction: " + e.getMessage());
        }
    }


    /**
     * Ενημερώνει τον server για το αποτέλεσμα του transaction
     * (επιτυχία ή ακύρωση), ώστε να ενημερωθεί το reputation_score.
     * Χρησιμοποιεί ξεχωριστό socket για να μην μπλοκάρει τον κύριο.
     *
     * @param success true αν το transaction ολοκληρώθηκε, false αν ακυρώθηκε
     */
    private void notifyServerTransactionResult(boolean success) {
        try (
                Socket socket = new Socket("127.0.0.1", 8080);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            MessageType type = success ? MessageType.TRANSACTION_SUCCESS : MessageType.TRANSACTION_FAILED;
            Message msg = new Message(type);
            msg.setUsername(username);
            out.writeObject(msg);
            out.flush();

            Message response = (Message) in.readObject();
            System.out.println("[TRANSACTION] Server reputation update: " + response.getMessage());

        } catch (Exception e) {
            System.out.println("[TRANSACTION] Could not notify server: " + e.getMessage());
        }
    }
}
