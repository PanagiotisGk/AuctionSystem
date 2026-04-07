package server;



/**
 * Τρέχει σε ξεχωριστό daemon thread και ελέγχει
 * κάθε 1 δευτερόλεπτο αν η τρέχουσα δημοπρασία έχει λήξει.
 * Αν έχει λήξει τότε καλεί τον ServerState για να την οριστικοποιήσει και να ξεκινήσει την επόμενη δημοπρασία από την ουρά.
 *
 */
public class AuctionMonitor implements Runnable {
    private final ServerState serverState;
    private boolean errorLogged = false;


    public AuctionMonitor(ServerState serverState) {
        this.serverState = serverState;
    }

    /**
     * Κύρια μεθοδος του thread, εδώ ελέγχουμε κάθε ένα δευτερόλεπτο αν έχει λήξει
     * η τρέχουσα δημοπρασία.
     * Εκτύπωση μηνύματος σε περίπτωση σφάλματος
     */
    @Override
    public void run() {
        while (true) {
            try {
                serverState.checkAndFinalizeExpiredAuction();
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("AuctionMonitor interrupted.");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!errorLogged)
                    System.out.println("AuctionMonitor error:");
                    e.printStackTrace();
                    errorLogged = true;
            }
        }
    }
}