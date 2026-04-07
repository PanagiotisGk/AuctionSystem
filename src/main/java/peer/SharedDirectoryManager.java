package peer;

import model.AuctionItem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;


/**
 * Η κλάση SharedDirectoryManager διαχειρίζεται τον τοπικό φάκελο
 * του peer όπου αποθηκεύονται τα αντικείμενα προς δημοπράτηση. Κάθε αντικείμενο αντιστοιχεί σε ένα αρχείο .txt με συγκεκριμένα
 * metadata (object_id, description, start_bid, auction_duration).
 */

public class SharedDirectoryManager {
    private final String sharedDirectoryPath;



    // Κατασκευαστης
    public SharedDirectoryManager(String sharedDirectoryPath) {
        this.sharedDirectoryPath = sharedDirectoryPath;
    }


    /**
     * Φορτώνει όλα τα αντικείμενα από τον shared directory.
     * Διαβάζει όλα τα .txt αρχεία και τα μετατρέπει σε AuctionItem. Αν ένα αρχείο δεν έχει σωστό format, το παραλείπει.
     * @return
     */
    public List<AuctionItem> loadItems() {
        List<AuctionItem> items = new ArrayList<>();

        File dir = new File(sharedDirectoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("Shared directory does not exist: " + sharedDirectoryPath);
            return items;
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null) {
            return items;
        }

        for (File file : files) {
            try {
                AuctionItem item = parseItemFile(file);
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                System.out.println("Failed to parse file: " + file.getName());
                e.printStackTrace();
            }
        }

        return items;
    }

    /**
     * Το format του αρχείου είναι key=value ανά γραμμή:
     * object_id=Object_01
     * description=Samsung Galaxy S21
     * start_bid=150.0
     * auction_duration=30
     *
     *
     * @param file
     * @return
     * @throws IllegalArgumentException
     */
    private AuctionItem parseItemFile(File file) throws Exception {
        String objectId = null;
        String description = null;
        Double startBid = null;
        Integer auctionDuration = null;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = br.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }

                String key = parts[0].trim();
                String value = parts[1].trim();

                switch (key) {
                    case "object_id" -> objectId = value;
                    case "description" -> description = value;
                    case "start_bid" -> startBid = Double.parseDouble(value);
                    case "auction_duration" -> auctionDuration = Integer.parseInt(value);
                }
            }
        }

        if (objectId == null || description == null || startBid == null || auctionDuration == null) {
            throw new IllegalArgumentException("Missing required fields in file: " + file.getName());
        }

        return new AuctionItem(objectId, description, startBid, auctionDuration);
    }
}
