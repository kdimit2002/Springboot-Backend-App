package com.example.webapp.BidNow.helpers;

public class GeneralHelper {
    public static String lowerExceptFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() == 1) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
