package peer;

import common.Message;
import common.MessageType;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;

public class PeerListener implements Runnable {
    private final int port;
    private final String sharedDirectoryPath;
    private final String username;
    private ObjectOutputStream serverOut; // σύνδεση προς server

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
                new Thread(() -> handleIncoming(socket)).start();
            }

        } catch (Exception e) {
            System.out.println("Peer listener error on port " + port);
            e.printStackTrace();
        }
    }



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

    private void handleWinnerTransaction(Message notification) {
        new Thread(() -> {
            System.out.println("Attempting to connect to seller...");
            try (
                    Socket sellerSocket = new Socket(notification.getSellerIp(), notification.getSellerPort());
                    ObjectOutputStream sellerOut = new ObjectOutputStream(sellerSocket.getOutputStream());
                    ObjectInputStream sellerIn = new ObjectInputStream(sellerSocket.getInputStream())
            ) {
                // Στέλνουμε TRANSACTION_REQUEST στον seller
                Message request = new Message(MessageType.TRANSACTION_REQUEST);
                request.setObjectId(notification.getObjectId());
                sellerOut.writeObject(request);
                sellerOut.flush();

                // Παραλαμβάνουμε το αρχείο
                Message response = (Message) sellerIn.readObject();

                if (Boolean.TRUE.equals(response.getSuccess())) {
                    // Αποθηκεύουμε το αρχείο στο shared directory μας
                    File dir = new File(sharedDirectoryPath);
                    if (!dir.exists()) dir.mkdirs();

                    File newFile = new File(dir, response.getFileName());
                    Files.write(newFile.toPath(), response.getFileContent());
                    System.out.println("[TRANSACTION] File received and saved: " + response.getFileName());

                    // Ενημερώνουμε τον server
                    synchronized (serverOut) {
                        Message updateMsg = new Message(MessageType.ITEM_ACQUIRED);
                        updateMsg.setObjectId(notification.getObjectId());
                        updateMsg.setMessage(username);
                        serverOut.writeObject(updateMsg);
                        serverOut.flush();
                    }
                } else {
                    System.out.println("[TRANSACTION] Failed: " + response.getMessage());
                }

            } catch (Exception e) {
                System.out.println("[TRANSACTION] Error connecting to seller: " + e.getMessage());
            }
        }).start();
    }

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

            // Διαβάζουμε το περιεχόμενο του αρχείου
            byte[] fileContent = Files.readAllBytes(targetFile.toPath());
            String fileName = targetFile.getName();

            response.setSuccess(true);
            response.setMessage("TRANSACTION_OK");
            response.setFileName(fileName);
            response.setFileContent(fileContent);
            out.writeObject(response);
            out.flush();

            // Διαγράφουμε το αρχείο από το shared directory του seller
            if (targetFile.delete()) {
                System.out.println("[TRANSACTION] File sent and deleted: " + fileName);
            }

        } catch (Exception e) {
            System.out.println("Error during transaction: " + e.getMessage());
        }
    }
}