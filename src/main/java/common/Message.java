package common;


import java.io.Serializable;
import model.AuctionItem;
import java.util.List;

/**
 * Η κλάση Message αναπαριστά ένα μήνυμα που ανταλλάσσεται μεταξύ
 * peers και server μέσω sockets. Υλοποιεί το Serializable interface
 * ώστε να μπορεί να μεταδίδεται μέσω ObjectOutputStream/ObjectInputStream.
 * Κάθε μήνυμα έχει έναν τύπο (MessageType) και τα κατάλληλα πεδία
 * ανάλογα με τη λειτουργία που εκτελεί.
 */
public class Message implements Serializable {
    private MessageType type;
    private String username;
    private String password;
    private String tokenId;
    private Integer port;
    private Boolean success;
    private String message;
    private List<AuctionItem> items;
    private String objectId;
    private String description;
    private String sellerTokenId;
    private Double currentHighestBid;
    private Long remainingSeconds;
    private Double bidAmount;
    private String sellerIp;
    private Integer sellerPort;
    private String fileName;
    private byte[] fileContent;
    private String sellerUsername;



    public Message() {
    }

    public void setSellerTokenId(String sellerTokenId) {
        this.sellerTokenId = sellerTokenId;
    }

    public String getSellerTokenId() {
        return sellerTokenId;
    }

    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }

    public String getSellerIp() { return sellerIp; }
    public void setSellerIp(String sellerIp) { this.sellerIp = sellerIp; }

    public Integer getSellerPort() { return sellerPort; }
    public void setSellerPort(Integer sellerPort) { this.sellerPort = sellerPort; }

    public Message(MessageType type) {
        this.type = type;
    }

    public MessageType getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<AuctionItem> getItems() {
        return items;
    }

    public void setItems(List<AuctionItem> items) {
        this.items = items;
    }


    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public Double getCurrentHighestBid() {
        return currentHighestBid;
    }

    public void setCurrentHighestBid(Double currentHighestBid) {
        this.currentHighestBid = currentHighestBid;
    }

    public Long getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(Long remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public Double getBidAmount() {
        return bidAmount;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public byte[] getFileContent() { return fileContent; }
    public void setFileContent(byte[] fileContent) { this.fileContent = fileContent; }

    public void setBidAmount(Double bidAmount) {
        this.bidAmount = bidAmount;
    }
}