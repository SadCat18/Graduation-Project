package com.skatehub.board;

public class BoardRequestException extends RuntimeException {
    private final int status;
    public BoardRequestException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
