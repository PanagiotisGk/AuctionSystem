package peer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

/**
 * Υλοποιεί τον αποστολέα Go-Back-N μέσω UDP.
 * Ο seller χρησιμοποιεί αυτή την κλάση για να στείλει το αρχείο metadata
 * στον buyer, σπασμένο σε πακέτα των 64 bytes.
 *
 * Παράμετροι πρωτοκόλλου:
 *   - DATA_SIZE = 64 bytes (ωφέλιμο φορτίο ανά πακέτο)
 *   - WINDOW_SIZE = 3 (μέγιστα μη-επιβεβαιωμένα πακέτα)
 *   - TIMEOUT = 2000 ms (χρόνος αναμονής πριν επαναμετάδοση)
 *
 * Μορφή πακέτου δεδομένων (DATA):
 *   [0-3]   : sequence number (4 bytes, big-endian)
 *   [4-7]   : συνολικός αριθμός πακέτων (4 bytes, big-endian)
 *   [8-11]  : μέγεθος payload σε αυτό το πακέτο (4 bytes, big-endian)
 *   [12-75] : payload (έως 64 bytes δεδομένα)
 *
 * Μορφή πακέτου ACK:
 *   [0-3]   : ack number (4 bytes, big-endian) — cumulative ACK
 */
public class GobackNsender {

    private static final int DATA_SIZE = 64;
    private static final int WINDOW_SIZE = 3;
    private static final int TIMEOUT_MS = 2000;
    // header: seqNum(4) + totalPackets(4) + payloadLength(4) = 12 bytes
    private static final int HEADER_SIZE = 12;

    private final byte[] fileData;
    private final InetAddress receiverAddress;
    private final int receiverPort;

    /**
     * @param fileData        Τα bytes του αρχείου metadata προς αποστολή
     * @param receiverAddress Η IP του buyer
     * @param receiverPort    Η UDP θύρα του buyer
     */
    public GobackNsender(byte[] fileData, InetAddress receiverAddress, int receiverPort) {
        this.fileData = fileData;
        this.receiverAddress = receiverAddress;
        this.receiverPort = receiverPort;
    }

    /**
     * Εκτελεί την αποστολή Go-Back-N.
     * Σπάει το αρχείο σε πακέτα 64 bytes, τα στέλνει με sliding window,
     * και περιμένει cumulative ACKs. Αν δεν λάβει ACK εντός timeout,
     * επαναμεταδίδει από το παλαιότερο μη-επιβεβαιωμένο πακέτο.
     *
     * @return true αν η μεταφορά ολοκληρώθηκε επιτυχώς
     */
    public boolean send() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);

            // Σπάμε το αρχείο σε πακέτα των 64 bytes
            int totalPackets = (int) Math.ceil((double) fileData.length / DATA_SIZE);
            if (totalPackets == 0) totalPackets = 1;

            System.out.println("[GBN-SENDER] File size: " + fileData.length
                    + " bytes -> " + totalPackets + " packets");

            int base = 0;        // παλαιότερο μη-επιβεβαιωμένο πακέτο
            int nextSeqNum = 0;  // επόμενο πακέτο προς αποστολή

            while (base < totalPackets) {
                // Στέλνουμε πακέτα μέσα στο window
                while (nextSeqNum < base + WINDOW_SIZE && nextSeqNum < totalPackets) {
                    byte[] packet = buildDataPacket(nextSeqNum, totalPackets);
                    DatagramPacket dp = new DatagramPacket(
                            packet, packet.length, receiverAddress, receiverPort
                    );
                    socket.send(dp);
                    System.out.println("[GBN-SENDER] Sent packet #" + nextSeqNum);
                    nextSeqNum++;
                }

                // Περιμένουμε ACK
                try {
                    byte[] ackBuf = new byte[4];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                    socket.receive(ackPacket);

                    int ackNum = bytesToInt(ackBuf);
                    System.out.println("[GBN-SENDER] Received ACK #" + ackNum);

                    // Cumulative ACK: ο receiver επιβεβαιώνει ότι έλαβε
                    // όλα τα πακέτα μέχρι και ackNum
                    if (ackNum >= base) {
                        base = ackNum + 1;
                    }

                } catch (java.net.SocketTimeoutException e) {
                    // Timeout — επαναμετάδοση από base
                    System.out.println("[GBN-SENDER] Timeout! Resending from packet #" + base);
                    nextSeqNum = base;
                }
            }

            System.out.println("[GBN-SENDER] Transfer complete! All " + totalPackets + " packets acknowledged.");
            return true;

        } catch (Exception e) {
            System.out.println("[GBN-SENDER] Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Κατασκευάζει ένα data packet με header + payload.
     *
     * @param seqNum       Ο αριθμός ακολουθίας του πακέτου
     * @param totalPackets Ο συνολικός αριθμός πακέτων
     * @return Το πλήρες πακέτο (header + payload)
     */
    private byte[] buildDataPacket(int seqNum, int totalPackets) {
        int offset = seqNum * DATA_SIZE;
        int payloadLength = Math.min(DATA_SIZE, fileData.length - offset);
        byte[] payload = Arrays.copyOfRange(fileData, offset, offset + payloadLength);

        byte[] packet = new byte[HEADER_SIZE + payloadLength];
        // Header
        intToBytes(seqNum, packet, 0);
        intToBytes(totalPackets, packet, 4);
        intToBytes(payloadLength, packet, 8);
        // Payload
        System.arraycopy(payload, 0, packet, HEADER_SIZE, payloadLength);

        return packet;
    }

    /**
     * Μετατρέπει int σε 4 bytes (big-endian) μέσα σε array.
     */
    private void intToBytes(int value, byte[] dest, int offset) {
        dest[offset]     = (byte) ((value >> 24) & 0xFF);
        dest[offset + 1] = (byte) ((value >> 16) & 0xFF);
        dest[offset + 2] = (byte) ((value >> 8)  & 0xFF);
        dest[offset + 3] = (byte) (value & 0xFF);
    }

    /**
     * Μετατρέπει 4 bytes (big-endian) σε int.
     */
    private int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }
}
