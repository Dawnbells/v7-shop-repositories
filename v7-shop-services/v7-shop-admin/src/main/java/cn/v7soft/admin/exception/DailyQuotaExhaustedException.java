package cn.v7soft.admin.exception;

public class DailyQuotaExhaustedException extends RuntimeException {

    public DailyQuotaExhaustedException(String message) {
        super(message);
    }

    public DailyQuotaExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
