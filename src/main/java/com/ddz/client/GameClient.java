package com.ddz.client;

import com.ddz.common.Card;
import com.ddz.common.Message;
import com.ddz.common.MessageType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;

public class GameClient {
    private final String host;
    private final int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // 客户端状态
    private String playerId;
    private String playerName;
    private List<Card> hand;
    private int myCurrentScore = 0; // 新增：用于记录自己的累计分数
    private final UIRenderer uiRenderer = new UIRenderer();

    public GameClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 启动一个线程来监听服务器消息
            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true); // 设置为守护线程，主线程退出时它也退出
            listenerThread.start();

            // 主线程处理用户输入
            handleUserInput();

        } catch (UnknownHostException e) {
            System.err.println("找不到主机: " + host);
        } catch (IOException e) {
            System.err.println("无法连接到服务器: " + e.getMessage());
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void listenToServer() {
        try {
            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                Message msg = Message.fromJson(serverMessage);
                uiRenderer.handleMessage(msg, this);
            }
        } catch (IOException e) {
            System.out.println("\n与服务器的连接已断开。按回车键退出。");
        } catch (Exception e) {
            // 捕获所有运行时异常，防止线程意外终止
            System.err.println("处理服务器消息时发生严重错误，连接可能已中断: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 监听线程结束，意味着游戏结束或连接断开，可以安全退出
            System.exit(0);
        }
    }

    private void handleUserInput() {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        try {
            // 1. 提示输入昵称并注册
            System.out.print("成功连接到服务器! 请输入您的昵称: ");
            this.playerName = consoleReader.readLine();
            if (this.playerName == null || this.playerName.trim().isEmpty()) {
                this.playerName = "玩家" + System.currentTimeMillis() % 1000;
            }
            sendMessage(new Message(MessageType.REGISTER, this.playerName));
            System.out.println("已发送注册请求，等待其他玩家加入...");

            // 2. 循环读取游戏指令
            String userInput;
            while ((userInput = consoleReader.readLine()) != null) {
                processInput(userInput.trim());
            }
        } catch (IOException e) {
            // 当主线程（控制台输入）发生IO异常时，打印堆栈并退出
            e.printStackTrace();
        }
    }

    private void processInput(String input) {
        if (input.isEmpty()) return;

        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "bid":
                try {
                    int score = Integer.parseInt(parts[1]);
                    sendMessage(new Message(MessageType.BID, score));
                } catch (Exception e) {
                    System.out.println("无效的叫分命令. 用法: bid <分数> (例如: bid 1)");
                }
                break;
            case "play":
                if (parts.length < 2) {
                    System.out.println("无效的出牌. 用法: play 34567");
                    return;
                }
                try {
                    List<Card> cards = parseCards(parts[1]);
                    sendMessage(new Message(MessageType.PLAY, cards));
                } catch (Exception e) {
                    System.out.println("无效的牌: " + e.getMessage() + ". 请使用 3456789TJQKASB. 例: play 34567");
                }
                break;
            case "pass":
                sendMessage(new Message(MessageType.PASS, null));
                break;
            case "chat":
                if (parts.length > 1) {
                    sendMessage(new Message(MessageType.CHAT, parts[1]));
                }
                break;
            case "ready":
                sendMessage(new Message(MessageType.READY, null));
                break;
            default:
                // 默认行为是出牌 (允许用户直接输入牌，如 33344)
                try {
                    List<Card> cards = parseCards(input);
                    sendMessage(new Message(MessageType.PLAY, cards));
                } catch (Exception e) {
                    System.out.println("无效命令. 可用: play, pass, bid, chat, ready. 或者直接输入牌 (如 34567).");
                }
                break;
        }
    }

    private List<Card> parseCards(String cardStr) {
        // 将输入的字符串转换为大写，以便不区分大小写
        return cardStr.toUpperCase().chars()
                .mapToObj(c -> Card.fromChar((char) c))
                .collect(Collectors.toList());
    }

    public void sendMessage(Message message) {
        if (out != null) {
            out.println(message.toString());
        }
    }

    // --- Getters and Setters ---
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public List<Card> getHand() {
        return hand;
    }

    public void setHand(List<Card> hand) {
        this.hand = hand;
        if (this.hand != null) {
            this.hand.sort(Card.cardComparator());
        }
    }

    public int getMyCurrentScore() {
        return myCurrentScore;
    }

    public void setMyCurrentScore(int score) {
        this.myCurrentScore = score;
    }
}