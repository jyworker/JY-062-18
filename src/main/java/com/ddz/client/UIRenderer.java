package com.ddz.client;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.ddz.common.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UIRenderer {
    private String lastPlayerId = "";
    private List<Card> lastPlayedCards;
    private String currentPlayerId = "";
    private Map<String, Integer> handsCount;
    private String landlordId;
    private Map<String, String> playerNames = new HashMap<>();

    public synchronized void handleMessage(Message msg, GameClient client) {
        switch (msg.getType()) {
            case PROMPT:
                String promptText = msg.a(String.class);
                for (Map.Entry<String, String> entry : playerNames.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        promptText = promptText.replace(entry.getKey(), getPlayerDisplayName(entry.getKey()));
                    }
                }
                System.out.println("\n[提示] " + promptText);
                break;

            case PLAY_INVALID:
                System.out.println("!! 无效操作: " + msg.a(String.class));
                break;

            default:
                String contentStr = msg.getContent();
                if (contentStr == null || contentStr.equals("null") || contentStr.isEmpty()) {
                    if (msg.getType() == MessageType.GAME_OVER) {
                        System.out.println("\n================ GAME OVER ================");
                        System.out.print("游戏结束，按回车键退出程序。");
                    }
                    return;
                }
                JSONObject content = JSONObject.parseObject(contentStr);

                switch (msg.getType()) {
                    case REGISTER_SUCCESS:
                        if (content.containsKey("playerId") && client.getPlayerId() == null) {
                            client.setPlayerId(content.getString("playerId"));
                        }
                        updatePlayerNames(content.getObject("playerNames", new TypeReference<Map<String, String>>() {}));
                        System.out.println("注册/更新成功！你的身份是: " + getPlayerDisplayName(client.getPlayerId()) + "。");
                        break;

                    case GAME_START:
                        updatePlayerNames(content.getObject("playerNames", new TypeReference<Map<String, String>>() {}));
                        System.out.println("\n所有玩家已准备就绪，游戏开始！");
                        System.out.println("本局玩家: " + playerNames.values().stream().collect(Collectors.joining(", ")));
                        break;

                    case DEAL_CARDS:
                        List<Card> hand = content.getJSONArray("hand").toJavaList(Card.class);
                        client.setHand(hand);
                        System.out.println("\n手牌已更新！");
                        render(client);
                        break;

                    case BID_INFO:
                        String bidderId = content.getString("bidderId");
                        int currentBid = content.getIntValue("currentBid");
                        if (bidderId.equals(client.getPlayerId())) {
                            System.out.println("\n>>> 轮到你叫地主了！当前最高叫分: " + currentBid + "。输入 'bid <分数>' 或 'bid 0' 不叫。");
                            render(client);
                        } else {
                            System.out.println("\n>>> 等待玩家 " + getPlayerDisplayName(bidderId) + " 叫地主...");
                        }
                        break;

                    case LANDLORD_CONFIRMED:
                        landlordId = content.getString("landlordId");
                        List<Card> landlordCards = content.getJSONArray("landlordCards").toJavaList(Card.class);
                        System.out.println("\n地主是: " + getPlayerDisplayName(landlordId));
                        System.out.println("底牌是: " + formatCards(landlordCards));
                        break;

                    case GAME_UPDATE:
                        currentPlayerId = content.getString("currentPlayerId");
                        lastPlayerId = content.getString("lastPlayerId");
                        lastPlayedCards = content.getJSONArray("lastPlayedCards").toJavaList(Card.class);
                        handsCount = content.getObject("handsCount", new TypeReference<Map<String, Integer>>() {});

                        if (currentPlayerId.equals(client.getPlayerId())) {
                            System.out.println("\n>>> 轮到你出牌了。输入 'play <牌>' 或 'pass'。");
                            render(client);
                        } else {
                            System.out.println("\n>>> 等待玩家 " + getPlayerDisplayName(currentPlayerId) + " 出牌...");
                        }
                        break;

                    case GAME_OVER:
                        JSONObject summary = content;
                        String winnerId = summary.getString("winnerId");
                        boolean isLandlordTeamWin = summary.getBooleanValue("isLandlordTeamWin");
                        int finalScoreChange = summary.getIntValue("finalScoreChange");
                        Map<String, Integer> allScores = summary.getObject("allPlayerScores", new TypeReference<Map<String, Integer>>(){});

                        if (allScores != null && allScores.containsKey(client.getPlayerId())) {
                            client.setMyCurrentScore(allScores.get(client.getPlayerId()));
                        }

                        System.out.println("\n==================== 局末结算 ====================");
                        System.out.println("胜利方: " + (isLandlordTeamWin ? "地主" : "农民") + "队");
                        if (summary.getBooleanValue("isSpring")) {
                            System.out.println("触发特殊事件: 春天! 倍率 x2");
                        }
                        System.out.println("底分: " + summary.getIntValue("baseBid"));
                        System.out.println("总倍率: " + summary.getIntValue("multiplier"));

                        System.out.println("\n--- 得分详情 ---");
                        if (allScores != null) {
                            allScores.forEach((pid, score) -> {
                                boolean isLandlord = pid.equals(landlordId);
                                String scoreChangeStr;
                                if (isLandlord) {
                                    scoreChangeStr = (isLandlordTeamWin ? "+" : "-") + (finalScoreChange * 2);
                                } else {
                                    scoreChangeStr = (isLandlordTeamWin ? "-" : "+") + finalScoreChange;
                                }
                                System.out.println(getPlayerDisplayName(pid) + ": " + score + " (本局 " + scoreChangeStr + ")");
                            });
                        }

                        System.out.println("================================================");
                        System.out.println("\n本局游戏结束。输入 'ready' 开始下一局，或 'chat <内容>' 聊天。");
                        break;

                    case CHAT_MESSAGE:
                        System.out.println("\n[聊天] " + getPlayerDisplayName(content.getString("senderId")) + ": " + content.getString("text"));
                        break;
                }
                break;
        }
    }

    private void render(GameClient client) {
        System.out.println("\n-------------------- 游戏桌面 --------------------");
        String role = "";
        if (landlordId != null) {
            role = client.getPlayerId() != null && client.getPlayerId().equals(landlordId) ? " (地主)" : " (农民)";
        }
        System.out.println("你的身份: " + getPlayerDisplayName(client.getPlayerId()) + role + " | 当前总分: " + client.getMyCurrentScore());

        if (landlordId != null) {
            System.out.println("地主: " + getPlayerDisplayName(landlordId));
        }

        if (handsCount != null) {
            playerNames.keySet().stream()
                    .filter(pid -> client.getPlayerId() != null && !pid.equals(client.getPlayerId()))
                    .forEach(pid -> {
                        System.out.println("玩家 " + getPlayerDisplayName(pid) + " 剩余牌数: " + handsCount.getOrDefault(pid, 0));
                    });
        }

        if (lastPlayedCards != null && !lastPlayedCards.isEmpty()) {
            System.out.println("上家 (" + getPlayerDisplayName(lastPlayerId) + ") 出的牌: " + formatCards(lastPlayedCards));
        } else {
            System.out.println("当前牌面: 无");
        }

        System.out.println("\n你的手牌 (" + (client.getHand() != null ? client.getHand().size() : 0) + "张):");
        System.out.println(formatCards(client.getHand()));
        System.out.println("-------------------------------------------------");
    }

    private String formatCards(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return "[]";
        }
        return cards.stream().map(Card::getDisplay).map(String::valueOf).collect(Collectors.joining(" "));
    }

    private void updatePlayerNames(Map<String, String> names) {
        if (names != null) {
            this.playerNames.putAll(names);
        }
    }

    private String getPlayerDisplayName(String playerId) {
        if (playerId == null) return "未知玩家";
        String name = playerNames.getOrDefault(playerId, "玩家");
        String idSuffix = playerId.length() > 4 ? playerId.substring(playerId.length() - 4) : playerId;
        return name + "(" + idSuffix + ")";
    }
}