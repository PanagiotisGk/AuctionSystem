package common;

/**
 * Οι τυποι των μηνυμάτων που εμφανίζονται είτε στον peer είτε
 * στον server
 */
public enum MessageType {
    REGISTER,
    REGISTER_RESPONSE,
    LOGIN,
    LOGIN_RESPONSE,
    LOGOUT,
    LOGOUT_RESPONSE,
    REQUEST_AUCTION,
    REQUEST_AUCTION_RESPONSE,
    GET_CURRENT_AUCTION,
    GET_CURRENT_AUCTION_RESPONSE,
    GET_AUCTION_DETAILS,
    GET_AUCTION_DETAILS_RESPONSE,
    PLACE_BID,
    PLACE_BID_RESPONSE,
    ERROR,
    NOTIFICATION,
    TRANSACTION_REQUEST,
    TRANSACTION_RESPONSE,
    ITEM_ACQUIRED,
}