package com.jargoyle.security;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String provider, String subject) {
        super("No local user for " + provider + "/ " + subject);
    }
}
