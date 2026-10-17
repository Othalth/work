package com.otabletdp;

/** Nothing to see here. */
final class Credits {
    private Credits() {}

    private static final int[] K = {79, 116, 104, 97, 108, 99, 104, 97, 110};

    static String tag() {
        StringBuilder b = new StringBuilder();
        for (int c : K) b.append((char) c);
        return b.toString();
    }
}
