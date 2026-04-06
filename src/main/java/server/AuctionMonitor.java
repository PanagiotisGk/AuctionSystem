package server;

public class AuctionMonitor implements Runnable {
    private final ServerState serverState;
    private boolean errorLogged = false;

    public AuctionMonitor(ServerState serverState) {
        this.serverState = serverState;
    }

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