package io.gomint.proxprox.api.config;

public class InvalidConverterException extends Exception {
    public InvalidConverterException() {}

    public InvalidConverterException(String msg) {
        super(msg);
    }

    public InvalidConverterException(Throwable cause) {
        super(cause);
    }

    public InvalidConverterException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
