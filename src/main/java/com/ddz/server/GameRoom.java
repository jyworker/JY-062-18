package com.ddz.server;

import cn.hutool.core.util.IdUtil;
import com.ddz.common.Card;
import com.ddz.common.Message;
import com.ddz.common.MessageType;
import com.ddz.common.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GameRoom {
    private static final int NUM_PLAYERS = 3;
    private final Map<String, ClientHandler> players = new ConcurrentHashMap<>();
    private final List<String> playerIds = new ArrayList<>();
    private final Map<String, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    private GameState gameState = GameState.WAITING;
    private final Set<String> readyPlayers = new CopyOnWriteArraySet<>();
    private int baseBid = 0;
    private int scoreMultiplier = 1;
    private Map<String, Integer> playCounts = new ConcurrentHashMap<>();

    private List<Card> deck;
    private List<Card> landlordCards;
    private int currentPlayerIndex = -1;
    private String landlordId = null;
    private int biddingTurn = 0;
    private int passCount = 0;
    private String lastPlayerId = null;
    private List<Card> lastPlayedCards = new ArrayList<>();
    private CardLogic.Play lastPlayType = null;

    private enum GameState {
        WAITING, BIDDING, PLAYING, INTERMISSION
    }

    public synchronized void addPlayer(ClientHandler clientHandler) {
        if (players.size() >= NUM_PLAYERS && !playerIds.contains(clientHandler.getPlayer().getId())) {
            clientHandler.sendMessage(new Message(MessageType.PROMPT, "房间已满，无法加入。"));
            try { clientHandler.getSocket().close(); } catch (Exception e) {}
            return;
        }

        String playerId = IdUtil.simpleUUID().substring(0, 8);
        Player player = clientHandler.getPlayer();
        player.setId(playerId);
        players.put(playerId, clientHandler);
        playerLocks.put(playerId, new ReentrantLock());
        if (!playerIds.contains(playerId)) {
            playerIds.add(playerId);
        }

        System.out.println("玩家 " + player.getName() + "(" + playerId + ") 加入房间. 当前人数: " + players.size());

        Map<String, String> playerNames = getPlayerNamesMap();
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", playerId);
        payload.put("playerNames", playerNames);
        broadcastAll(new Message(MessageType.REGISTER_SUCCESS, payload));

        if (players.size() == NUM_PLAYERS) {
            this.gameState = GameState.INTERMISSION;
            broadcastAll(new Message(MessageType.PROMPT, "玩家已满，请输入 'ready' 开始第一局游戏！"));
        }
    }

    public synchronized void handleReady(String playerId) {
        if (gameState != GameState.INTERMISSION) return;

        readyPlayers.add(playerId);
        broadcastAll(new Message(MessageType.PROMPT, "玩家 '" + getPlayerNamesMap().get(playerId) + "' 已准备。(" + readyPlayers.size() + "/" + NUM_PLAYERS + ")"));

        if (readyPlayers.size() == NUM_PLAYERS) {
            broadcastAll(new Message(MessageType.PROMPT, "所有玩家已准备，2秒后开始下一局..."));
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    startGame();
                }
            }, 2000);
        }
    }

    private void resetForNewGame() {
        players.values().forEach(handler -> {
            handler.getPlayer().setLandlord(false);
            handler.getPlayer().getHand().clear();
        });
        this.readyPlayers.clear();
        this.baseBid = 1;
        this.scoreMultiplier = 1;
        this.playCounts.clear();
        this.landlordId = null;
        this.landlordCards = null;
        this.deck = null;
        this.lastPlayedCards = new ArrayList<>();
        this.lastPlayType = null;
        this.lastPlayerId = null;
        this.passCount = 0;
        this.biddingTurn = 0;
        this.currentPlayerIndex = -1;
    }

    private void startGame() {
        resetForNewGame();
        this.gameState = GameState.BIDDING;
        System.out.println("新一局游戏开始！");

        deck = new ArrayList<>();
        for (Card card : Card.values()) {
            if (card == Card.S || card == Card.B) deck.add(card);
            else for (int i = 0; i < 4; i++) deck.add(card);
        }
        Collections.shuffle(deck);

        landlordCards = new ArrayList<>(deck.subList(51, 54));

        Map<String, Object> gameStartPayload = new HashMap<>();
        gameStartPayload.put("playerNames", getPlayerNamesMap());
        broadcastAll(new Message(MessageType.GAME_START, gameStartPayload));

        for (int i = 0; i < NUM_PLAYERS; i++) {
            String playerId = playerIds.get(i);
            playCounts.put(playerId, 0);
            ClientHandler handler = players.get(playerId);
            List<Card> hand = new ArrayList<>(deck.subList(i * 17, (i + 1) * 17));
            hand.sort(Card.cardComparator());
            handler.setHand(hand);
            Map<String, Object> payload = new HashMap<>();
            payload.put("hand", hand);
            handler.sendMessage(new Message(MessageType.DEAL_CARDS, payload));
        }

        currentPlayerIndex = new Random().nextInt(NUM_PLAYERS);
        promptNextBidder();
    }

    private void promptNextBidder() {
        if (biddingTurn >= NUM_PLAYERS) {
            determineLandlord();
            return;
        }
        String bidderId = playerIds.get(currentPlayerIndex);
        Map<String, Object> payload = new HashMap<>();
        payload.put("bidderId", bidderId);
        payload.put("currentBid", baseBid);
        broadcastAll(new Message(MessageType.BID_INFO, payload));
        biddingTurn++;
    }

    public synchronized void handleBid(String playerId, int bid) {
        if (gameState != GameState.BIDDING || !playerId.equals(playerIds.get(currentPlayerIndex))) return;

        String playerName = getPlayerNamesMap().get(playerId);
        if (bid > baseBid && bid <= 3) {
            baseBid = bid;
            landlordId = playerId;
            broadcastAll(new Message(MessageType.PROMPT, "玩家 " + playerName + " 叫 " + bid + " 分!"));
            if (bid == 3) {
                determineLandlord();
                return;
            }
        } else {
            broadcastAll(new Message(MessageType.PROMPT, "玩家 " + playerName + " 不叫."));
        }

        currentPlayerIndex = (currentPlayerIndex + 1) % NUM_PLAYERS;
        promptNextBidder();
    }

    private void determineLandlord() {
        if (landlordId == null) {
            broadcastAll(new Message(MessageType.PROMPT, "无人叫地主，3秒后将重新发牌..."));
            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            startGame();
            return;
        }

        ClientHandler landlordHandler = players.get(landlordId);
        landlordHandler.getPlayer().setLandlord(true);
        landlordHandler.getHand().addAll(landlordCards);
        landlordHandler.getHand().sort(Card.cardComparator());
        currentPlayerIndex = playerIds.indexOf(landlordId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("landlordId", landlordId);
        payload.put("landlordCards", landlordCards);
        broadcastAll(new Message(MessageType.LANDLORD_CONFIRMED, payload));

        Map<String, Object> handUpdatePayload = new HashMap<>();
        handUpdatePayload.put("hand", landlordHandler.getHand());
        landlordHandler.sendMessage(new Message(MessageType.DEAL_CARDS, handUpdatePayload));

        gameState = GameState.PLAYING;
        broadcastGameState();
    }

    public void handlePlay(String playerId, List<Card> cards) {
        ReentrantLock playerLock = playerLocks.get(playerId);
        if (playerLock == null) return;

        playerLock.lock();
        try {
            synchronized (this) {
                if (gameState != GameState.PLAYING || !playerId.equals(playerIds.get(currentPlayerIndex))) {
                    players.get(playerId).sendMessage(new Message(MessageType.PLAY_INVALID, "还没轮到你出牌！"));
                    return;
                }
                if (cards == null || cards.isEmpty()) {
                    players.get(playerId).sendMessage(new Message(MessageType.PLAY_INVALID, "出牌不能为空！"));
                    return;
                }

                ClientHandler playerHandler = players.get(playerId);
                if (!playerHasCards(playerHandler.getHand(), cards)) {
                    players.get(playerId).sendMessage(new Message(MessageType.PLAY_INVALID, "你没有这些牌！"));
                    return;
                }

                CardLogic.Play currentPlay = CardLogic.getPlayType(cards);
                boolean isNewTurn = (lastPlayerId == null || lastPlayerId.equals(playerId));
                CardLogic.Play lastEffectivePlay = isNewTurn ? null : lastPlayType;

                if (currentPlay.getType() == CardLogic.CardType.INVALID) {
                    players.get(playerId).sendMessage(new Message(MessageType.PLAY_INVALID, "出牌牌型不合法！"));
                    return;
                }

                if (lastEffectivePlay != null && lastPlayedCards.size() != cards.size() && !currentPlay.isBombOrRocket()) {
                    players.get(playerId).sendMessage(new Message(MessageType.PLAY_INVALID, "出牌数量与上家不一致！"));
                    return;
                }

                if (!CardLogic.canPlay(lastEffectivePlay, currentPlay)) {
                    players.get(playerId).sendMessage(new Message(MessageType.PLAY_INVALID, "你的牌没有上家的牌大！"));
                    return;
                }

                if (currentPlay.isBombOrRocket()) {
                    scoreMultiplier *= 2;
                    broadcastAll(new Message(MessageType.PROMPT, "💣 炸弹出现！当前倍率 x" + scoreMultiplier));
                }

                playCounts.merge(playerId, 1, Integer::sum);

                for (Card cardToRemove : cards) {
                    playerHandler.getHand().remove(cardToRemove);
                }

                lastPlayedCards = cards;
                lastPlayType = currentPlay;
                lastPlayerId = playerId;
                passCount = 0;
            }
        } finally {
            playerLock.unlock();
        }

        ClientHandler playerHandler = players.get(playerId);
        String playerName = playerHandler.getPlayer().getName();
        broadcastAll(new Message(MessageType.PROMPT, "玩家 " + playerName + " 出了: " + formatCardsForPrompt(cards)));

        Map<String, Object> handUpdatePayload = new HashMap<>();
        handUpdatePayload.put("hand", playerHandler.getHand());
        playerHandler.sendMessage(new Message(MessageType.DEAL_CARDS, handUpdatePayload));

        if (playerHandler.getHand().isEmpty()) {
            endGame(playerId);
            return;
        }

        nextTurn();
        broadcastGameState();
    }

    /**
     * 【新增】安全地检查玩家是否拥有要出的所有牌（检查数量）
     * @param hand 玩家的手牌
     * @param cardsToPlay 玩家要出的牌
     * @return 如果拥有，返回true，否则返回false
     */
    private boolean playerHasCards(List<Card> hand, List<Card> cardsToPlay) {
        if (cardsToPlay.isEmpty()) return true;
        // 统计手牌中每张牌的数量
        Map<Card, Long> handCounts = hand.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        // 统计要出的牌中每张牌的数量
        Map<Card, Long> playCounts = cardsToPlay.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // 检查要出的每一种牌
        for (Map.Entry<Card, Long> entry : playCounts.entrySet()) {
            Card card = entry.getKey();
            Long countNeeded = entry.getValue();
            // 如果手牌中没有这张牌，或者数量不够，则验证失败
            if (!handCounts.containsKey(card) || handCounts.get(card) < countNeeded) {
                return false;
            }
        }
        return true;
    }

    private void endGame(String winnerId) {
        this.gameState = GameState.INTERMISSION;
        this.readyPlayers.clear();

        Player winner = players.get(winnerId).getPlayer();
        boolean isLandlordWinner = winner.isLandlord();

        boolean isSpring = false;
        if (isLandlordWinner) {
            boolean farmersPlayed = playerIds.stream()
                    .filter(pid -> !pid.equals(landlordId))
                    .anyMatch(pid -> playCounts.getOrDefault(pid, 0) > 0);
            if (!farmersPlayed) {
                isSpring = true;
                scoreMultiplier *= 2;
            }
        } else {
            if (playCounts.getOrDefault(landlordId, 0) <= 1 && !landlordId.equals(winnerId)) {
                isSpring = true;
                scoreMultiplier *= 2;
            }
        }

        int finalScore = baseBid * scoreMultiplier;

        for (String pid : playerIds) {
            Player p = players.get(pid).getPlayer();
            if (p.isLandlord()) {
                p.addScore(isLandlordWinner ? finalScore * 2 : -finalScore * 2);
            } else {
                p.addScore(isLandlordWinner ? -finalScore : finalScore);
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("winnerId", winnerId);
        payload.put("isLandlordTeamWin", isLandlordWinner);
        payload.put("baseBid", baseBid);
        payload.put("multiplier", scoreMultiplier);
        payload.put("isSpring", isSpring);
        payload.put("finalScoreChange", finalScore);
        payload.put("allPlayerScores", getPlayerScoresMap());

        broadcastAll(new Message(MessageType.GAME_OVER, payload));
        System.out.println("游戏结束，胜利者: " + winner.getName());
    }

    public synchronized void handlePass(String playerId) {
        if (gameState != GameState.PLAYING || !playerId.equals(playerIds.get(currentPlayerIndex))) {
            players.get(playerId).sendMessage(new Message(MessageType.PLAY_INVALID, "还没轮到你！"));
            return;
        }
        if (lastPlayerId == null || lastPlayerId.equals(playerId)) {
            players.get(playerId).sendMessage(new Message(MessageType.PLAY_INVALID, "你是第一个出牌或刚接了牌，不能PASS。"));
            return;
        }

        passCount++;
        String playerName = getPlayerNamesMap().get(playerId);
        broadcastAll(new Message(MessageType.PROMPT, "玩家 " + playerName + " PASS."));

        if (passCount == NUM_PLAYERS - 1) {
            passCount = 0;
            currentPlayerIndex = playerIds.indexOf(lastPlayerId);
            String nextPlayerName = getPlayerNamesMap().get(playerIds.get(currentPlayerIndex));
            lastPlayerId = null;
            lastPlayedCards.clear();
            lastPlayType = null;
            broadcastAll(new Message(MessageType.PROMPT, "所有人都PASS，现在由玩家 " + nextPlayerName + " 重新出牌."));
        } else {
            nextTurn();
        }

        broadcastGameState();
    }

    private void nextTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % NUM_PLAYERS;
    }

    private void broadcastGameState() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("currentPlayerId", playerIds.get(currentPlayerIndex));
        payload.put("lastPlayerId", lastPlayerId);
        payload.put("lastPlayedCards", lastPlayedCards);

        Map<String, Integer> handsCount = new HashMap<>();
        for (String pid : playerIds) {
            handsCount.put(pid, players.get(pid).getHand().size());
        }
        payload.put("handsCount", handsCount);

        broadcastAll(new Message(MessageType.GAME_UPDATE, payload));
    }

    public void handleChat(String senderId, String text) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderId", senderId);
        payload.put("text", text);
        broadcastAll(new Message(MessageType.CHAT_MESSAGE, payload));
    }

    public void removePlayer(String playerId) {
        ClientHandler handler = players.remove(playerId);
        playerLocks.remove(playerId);
        if (handler != null) {
            playerIds.remove(playerId);
            readyPlayers.remove(playerId);
            String playerName = handler.getPlayer().getName();
            System.out.println("玩家 " + playerName + "(" + playerId + ") 已断开连接.");

            if (gameState != GameState.WAITING && players.size() < NUM_PLAYERS) {
                gameState = GameState.INTERMISSION;
                broadcastAll(new Message(MessageType.PROMPT, "玩家 " + playerName + " 已离开游戏，本局游戏结束。等待新玩家加入..."));
            }
        }
    }

    private Map<String, Integer> getPlayerScoresMap() {
        return players.values().stream()
                .map(ClientHandler::getPlayer)
                .collect(Collectors.toMap(Player::getId, Player::getScore));
    }

    private Map<String, String> getPlayerNamesMap() {
        return players.values().stream()
                .map(ClientHandler::getPlayer)
                .collect(Collectors.toMap(Player::getId, Player::getName));
    }

    private void broadcastAll(Message message) {
        for (ClientHandler handler : players.values()) {
            handler.sendMessage(message);
        }
    }

    private String formatCardsForPrompt(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return "";
        return cards.stream().map(Card::getDisplay).map(String::valueOf).collect(Collectors.joining(" "));
    }
}