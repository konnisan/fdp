package com.delivery.fdp.service;

final class ShellCommandSupport {
    private ShellCommandSupport() {
    }

    static boolean windows() {
        return System.getProperty("os.name", "")
                .toLowerCase()
                .contains("win");
    }

    static String quote(String value) {
        String text = value == null ? "" : value;
        if (text.contains("\n") || text.contains("\r") || text.contains("\0")) {
            throw new IllegalArgumentException("Unsafe command argument");
        }
        if (windows()) {
            if (text.contains("\"")) {
                throw new IllegalArgumentException("Windows command argument cannot contain double quotes");
            }
            return "\"" + text + "\"";
        }
        return "'" + text.replace("'", "'\\''") + "'";
    }
}
