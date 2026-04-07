package cn.v7soft.admin.exception;

public class GracefulShutdownPendingException extends RuntimeException {

    public GracefulShutdownPendingException(String message) {
        super(message);
    }
}
