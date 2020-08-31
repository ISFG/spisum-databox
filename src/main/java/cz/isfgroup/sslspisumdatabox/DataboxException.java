package cz.isfgroup.sslspisumdatabox;

public class DataboxException extends RuntimeException {

    public DataboxException(String message) {
        super(message);
    }

    public DataboxException(Throwable cause) {
        super(cause);
    }

    public DataboxException(String message, Throwable cause) {
        super(message, cause);
    }

}
