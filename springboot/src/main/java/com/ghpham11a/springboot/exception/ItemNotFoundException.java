package com.ghpham11a.springboot.exception;

public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(String id) {
        super("Item not found: " + id);
    }
}
