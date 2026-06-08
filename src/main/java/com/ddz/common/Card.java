package com.ddz.common;

import java.util.Comparator;

public enum Card {
    C3('3', 3), C4('4', 4), C5('5', 5), C6('6', 6), C7('7', 7),
    C8('8', 8), C9('9', 9), T('T', 10), J('J', 11), Q('Q', 12),
    K('K', 13), A('A', 14), C2('2', 15),
    S('S', 16), // 小王
    B('B', 17); // 大王

    private final char display;
    private final int value;

    Card(char display, int value) {
        this.display = display;
        this.value = value;
    }

    public char getDisplay() {
        return display;
    }

    public int getValue() {
        return value;
    }

    public static Card fromChar(char c) {
        for (Card card : values()) {
            if (card.getDisplay() == Character.toUpperCase(c)) {
                return card;
            }
        }
        throw new IllegalArgumentException("无效的牌字符: " + c);
    }

    // 提供一个按值降序的比较器
    public static Comparator<Card> cardComparator() {
        return Comparator.comparingInt(Card::getValue).reversed();
    }
}