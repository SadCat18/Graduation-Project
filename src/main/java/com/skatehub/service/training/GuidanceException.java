package com.skatehub.service.training;

public class GuidanceException extends RuntimeException {
    public final int status;
    public final String errorCode;
    public final java.util.Map<String,Object> details;
    public GuidanceException(int status, String errorCode, String message) {this(status,errorCode,message,java.util.Map.of());}
    public GuidanceException(int status, String errorCode, String message,java.util.Map<String,Object> details) {
        super(message); this.status = status; this.errorCode = errorCode;this.details=java.util.Map.copyOf(details);
    }
}
