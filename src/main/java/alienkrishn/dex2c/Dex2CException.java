package alienkrishn.dex2c;

final class Dex2CException extends RuntimeException {

    Dex2CException(String message) {
        super(message);
    }

    Dex2CException(String message, Throwable cause) {
        super(message, cause);
    }
}
