package com.ddz.server;

import com.ddz.common.Card;

import java.util.*;
import java.util.stream.Collectors;

// 此类仅包含静态方法，作为工具类
public class CardLogic {

    public enum CardType {
        INVALID, SINGLE, PAIR, TRIO,
        TRIO_WITH_SINGLE,   // 三带一
        TRIO_WITH_PAIR,     // 三带二
        STRAIGHT,           // 顺子
        PAIR_STRAIGHT,      // 连对
        AIRPLANE,           // 飞机（不带翅膀）
        AIRPLANE_WITH_SINGLE_WINGS, // 飞机带单
        AIRPLANE_WITH_PAIR_WINGS,   // 飞机带对
        FOUR_WITH_TWO_SINGLES, // 四带二（散）
        FOUR_WITH_TWO_PAIRS,   // 四带二（对）
        BOMB,
        ROCKET
    }

    public static class Play {
        private final CardType type;
        private final int mainValue;
        private final int length;

        public Play(CardType type, int mainValue, int length) {
            this.type = type;
            this.mainValue = mainValue;
            this.length = length;
        }

        public CardType getType() { return type; }
        public int getMainValue() { return mainValue; }
        public int getLength() { return length; }
        public boolean isBombOrRocket() {
            return type == CardType.BOMB || type == CardType.ROCKET;
        }
    }

    public static Play getPlayType(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return new Play(CardType.INVALID, 0, 0);
        }
        cards.sort(Comparator.comparingInt(Card::getValue));
        int size = cards.size();

        // 1. 基础牌型判断
        switch (size) {
            case 1:
                return new Play(CardType.SINGLE, cards.get(0).getValue(), 1);
            case 2:
                if (cards.get(0).getValue() == cards.get(1).getValue()) {
                    return new Play(CardType.PAIR, cards.get(0).getValue(), 1);
                }
                if (cards.contains(Card.S) && cards.contains(Card.B)) {
                    return new Play(CardType.ROCKET, 0, 1);
                }
                break;
            case 3:
                if (cards.get(0).getValue() == cards.get(2).getValue()) {
                    return new Play(CardType.TRIO, cards.get(0).getValue(), 1);
                }
                break;
        }

        // 2. 使用Map进行频率分析，这是判断复杂牌型的关键
        Map<Integer, Long> counts = cards.stream().collect(Collectors.groupingBy(Card::getValue, Collectors.counting()));
        int mapSize = counts.size();

        // 3. 炸弹 (特殊处理)
        if (size == 4 && mapSize == 1) {
            return new Play(CardType.BOMB, cards.get(0).getValue(), 1);
        }

        // 4. 三带X 和 四带X 判断
        if (counts.containsValue(3L)) {
            // 提取三条的主值
            int mainValue = counts.entrySet().stream().filter(e -> e.getValue() == 3).map(Map.Entry::getKey).findFirst().get();
            if (size == 4 && mapSize == 2) return new Play(CardType.TRIO_WITH_SINGLE, mainValue, 1);
            if (size == 5 && mapSize == 2) return new Play(CardType.TRIO_WITH_PAIR, mainValue, 1);
        }
        if (counts.containsValue(4L)) {
            int mainValue = counts.entrySet().stream().filter(e -> e.getValue() == 4).map(Map.Entry::getKey).findFirst().get();
            if (size == 6 && mapSize == 3) return new Play(CardType.FOUR_WITH_TWO_SINGLES, mainValue, 1);
            if (size == 8 && mapSize == 3) return new Play(CardType.FOUR_WITH_TWO_PAIRS, mainValue, 1);
        }

        // 5. 顺子类型判断
        // 顺子: 牌数=Map大小，且连续
        if (size >= 5 && mapSize == size && isConsecutive(cards, 1)) {
            return new Play(CardType.STRAIGHT, cards.get(size - 1).getValue(), size);
        }
        // 连对: 牌数/2=Map大小，且连续
        if (size >= 6 && size % 2 == 0 && mapSize == size / 2 && isConsecutive(cards, 2)) {
            return new Play(CardType.PAIR_STRAIGHT, cards.get(size - 1).getValue(), size / 2);
        }

        // 6. 飞机判断 (最复杂的逻辑)
        List<Integer> trios = counts.entrySet().stream()
                .filter(e -> e.getValue() == 3L)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        if (!trios.isEmpty() && isListConsecutive(trios)) {
            int airplaneLength = trios.size();
            int mainValue = trios.get(airplaneLength - 1);

            if (size == airplaneLength * 3) {
                // 纯飞机
                return new Play(CardType.AIRPLANE, mainValue, airplaneLength);
            }
            if (size == airplaneLength * 4 && mapSize == airplaneLength * 2) {
                // 飞机带单翅膀, Map大小必须是飞机长度的两倍
                return new Play(CardType.AIRPLANE_WITH_SINGLE_WINGS, mainValue, airplaneLength);
            }
            if (size == airplaneLength * 5 && mapSize == airplaneLength * 2) {
                // 飞机带对翅膀，确保翅膀都是对子
                boolean wingsArePairs = counts.entrySet().stream()
                        .filter(e -> !trios.contains(e.getKey()))
                        .allMatch(e -> e.getValue() == 2L);
                if (wingsArePairs) {
                    return new Play(CardType.AIRPLANE_WITH_PAIR_WINGS, mainValue, airplaneLength);
                }
            }
        }

        // 所有规则都不匹配，是无效牌
        return new Play(CardType.INVALID, 0, 0);
    }

    private static boolean isConsecutive(List<Card> cards, int groupSize) {
        if (cards.get(cards.size() - 1).getValue() > Card.A.getValue()) return false;
        for (int i = 0; i < cards.size() / groupSize - 1; i++) {
            if (cards.get(i * groupSize).getValue() + 1 != cards.get((i + 1) * groupSize).getValue()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isListConsecutive(List<Integer> values) {
        if (values.isEmpty() || values.get(values.size() - 1) > Card.A.getValue()) return false;
        for (int i = 0; i < values.size() - 1; i++) {
            if (values.get(i) + 1 != values.get(i + 1)) return false;
        }
        return true;
    }

    public static boolean canPlay(Play lastPlay, Play currentPlay) {
        if (currentPlay.type == CardType.INVALID) return false;
        if (lastPlay == null) return true;

        if (currentPlay.type == CardType.ROCKET) return true;
        if (lastPlay.type == CardType.ROCKET) return false;

        if (currentPlay.type == CardType.BOMB && lastPlay.type != CardType.BOMB) return true;

        if (currentPlay.type != lastPlay.type || currentPlay.length != lastPlay.length) {
            return currentPlay.type == CardType.BOMB;
        }

        return currentPlay.mainValue > lastPlay.mainValue;
    }
}
