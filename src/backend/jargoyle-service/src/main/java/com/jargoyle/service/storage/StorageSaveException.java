package com.jargoyle.service.storage;

public class StorageSaveException extends Exception {
    public StorageSaveException(String message) {
        super(message);
    }

    public StorageSaveException(String string, Throwable cause) {
        super(string, cause);
    }
}
