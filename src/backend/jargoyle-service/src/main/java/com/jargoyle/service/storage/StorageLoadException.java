package com.jargoyle.service.storage;

public class StorageLoadException extends Exception {
    public StorageLoadException(String message) {
        super(message);
    }

    public StorageLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
