package peer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Random;

/**
 * Υλοποιεί τον παραλήπτη Go-Back-N μέσω UDP.
 * Ο buyer χρησιμοποιεί αυτή την κλάση για να λάβει το αρχείο metadata
 * από τον seller.
 *
 * Προσομοίωση αναξιόπιστου δικτύου:
 *   - 20% πιθανότητα απόρριψης εισερχόμενου πακέτου
 *   - 20% πιθανότητα μη αποστολής ACK
 *
 * Ο receiver δέχεται μόνο in-order πακέτα (απορρίπτει out-of-order)
 * και στέλνει cumulative ACKs.
 */
public class GobackNreceiver {

    private static final int HEADER_SIZE = 12;
    private static final int MAX_PACKET_SIZE = HEADER_SIZE + 64;
    // timeout αναμονής για τον receiver — αρκετά μεγάλο ώστε ο sender
    // να προλάβει να κάνει retransmit
    private static final int RECEIVE_TIMEOUT_MS = 15000;

    private final int port;
    private final Random rand = new Random();

    /**
     * @param port Η UDP θύρα στην οποία ακούει ο receiver
     */
    public GobackNreceiver(int port) {
        this.port = port;
    }

    /**
     * Ξεκινά τη λήψη πακέτων Go-Back-N.
     * Ανασυνθέτει το αρχείο από τα πακέτα που λαμβάνει.
     *
     * @return Τα bytes του αρχείου, ή null αν αποτύχει
     */
    public byte[] receive() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setSoTimeout(RECEIVE_TIMEOUT_MS);

            System.out.println("[GBN-RECEIVER] Listening on UDP port " + port);

            int expectedSeqNum = 0;
            int totalPackets = -1;
            byte[][] receivedData = null;
            int[] payloadLengths = null;

            while (true) {
                byte[] buf = new byte[MAX_PACKET_SIZE];
                DatagramPacket dp = new DatagramPacket(buf, buf.length);

                try {
                    socket.receive(dp);
                } catch (java.net.SocketTimeoutException e) {
                    System.out.println("[GBN-RECEIVER] Timeout waiting for packets. Aborting.");
                    return null;
                }

                // Προσομοίωση απώλειας: 20% πιθανότητα απόρριψης εισερχόμενου πακέτου
                if (rand.nextDouble() < 0.20) {
                    int droppedSeq = bytesToInt(buf, 0);
                    System.out.println("[GBN-RECEIVER] Simulated DROP of packet #" + droppedSeq);
                    continue;
                }

                // Ανάγνωση header
                int seqNum = bytesToInt(buf, 0);
                int totalPkts = bytesToInt(buf, 4);
                int payloadLength = bytesToInt(buf, 8);

                // Αρχικοποίηση buffers στο πρώτο πακέτο
                if (receivedData == null) {
                    totalPackets = totalPkts;
                    receivedData = new byte[totalPackets][];
                    payloadLengths = new int[totalPackets];
                    System.out.println("[GBN-RECEIVER] Expecting " + totalPackets + " packets");
                }

                // Ελέγχουμε αν είναι in-order
                if (seqNum == expectedSeqNum) {
                    // Αποθηκεύουμε το payload
                    receivedData[seqNum] = new byte[payloadLength];
                    System.arraycopy(buf, HEADER_SIZE, receivedData[seqNum], 0, payloadLength);
                    payloadLengths[seqNum] = payloadLength;

                    System.out.println("[GBN-RECEIVER] Accepted packet #" + seqNum
                            + " (" + payloadLength + " bytes)");

                    expectedSeqNum++;

                    // Στέλνουμε cumulative ACK (αλλά 20% χάνεται)
                    if (rand.nextDouble() < 0.20) {
                        System.out.println("[GBN-RECEIVER] Simulated ACK DROP for ACK #" + (expectedSeqNum - 1));
                    } else {
                        byte[] ackBytes = intToBytes(expectedSeqNum - 1);
                        DatagramPacket ackPacket = new DatagramPacket(
                                ackBytes, ackBytes.length,
                                dp.getAddress(), dp.getPort()
                        );
                        socket.send(ackPacket);
                        System.out.println("[GBN-RECEIVER] Sent ACK #" + (expectedSeqNum - 1));
                    }

                    // Τελειώσαμε;
                    if (expectedSeqNum == totalPackets) {
                        System.out.println("[GBN-RECEIVER] All packets received! Reassembling file...");
                        return reassemble(receivedData, payloadLengths, totalPackets);
                    }

                } else {
                    // Out-of-order → απορρίπτουμε, ξαναστέλνουμε ACK για το τελευταίο in-order
                    System.out.println("[GBN-RECEIVER] Out-of-order packet #" + seqNum
                            + " (expected #" + expectedSeqNum + ") -> Discarded");

                    if (expectedSeqNum > 0) {
                        // Ξαναστέλνουμε ACK για το τελευταίο σωστό (αν δεν χαθεί)
                        if (rand.nextDouble() >= 0.20) {
                            byte[] ackBytes = intToBytes(expectedSeqNum - 1);
                            DatagramPacket ackPacket = new DatagramPacket(
                                    ackBytes, ackBytes.length,
                                    dp.getAddress(), dp.getPort()
                            );
                            socket.send(ackPacket);
                            System.out.println("[GBN-RECEIVER] Re-sent ACK #" + (expectedSeqNum - 1));
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("[GBN-RECEIVER] Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Ανασυνθέτει τα data πακέτα στο αρχικό αρχείο.
     */
    private byte[] reassemble(byte[][] data, int[] lengths, int totalPackets) {
        int totalSize = 0;
        for (int i = 0; i < totalPackets; i++) {
            totalSize += lengths[i];
        }

        byte[] result = new byte[totalSize];
        int offset = 0;
        for (int i = 0; i < totalPackets; i++) {
            System.arraycopy(data[i], 0, result, offset, lengths[i]);
            offset += lengths[i];
        }
        return result;
    }

    /**
     * Μετατρέπει int σε 4 bytes (big-endian).
     */
    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    /**
     * Μετατρέπει 4 bytes (big-endian) σε int, ξεκινώντας από offset.
     */
    private int bytesToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}
