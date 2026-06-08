package com.ddz.server;

import com.alibaba.fastjson.JSON;
import com.ddz.common.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final GameRoom gameRoom;
    private PrintWriter out;
    private BufferedReader in;

    // 使用一个Player对象来聚合玩家所有信息
    private final Player player;

    public ClientHandler(Socket socket, GameRoom gameRoom) {
        this.socket = socket;
        this.gameRoom = gameRoom;
        this.player = new Player();
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 连接建立后，第一条消息必须是注册消息
            String registrationLine = in.readLine();
            if (registrationLine == null) {
                // 客户端在发送任何消息前就断开了
                return;
            }

            Message regMsg = Message.fromJson(registrationLine);
            if (regMsg.getType() == MessageType.REGISTER) {
                String playerName = regMsg.a(String.class);
                this.player.setName(playerName);
                // 将此处理器（代表一个玩家）添加到游戏房间
                // 房间会为其分配ID并通知所有玩家
                gameRoom.addPlayer(this);
            } else {
                // 如果第一条消息不是注册，则认为是非法连接，直接关闭
                System.out.println("收到非法连接（第一条消息不是REGISTER），已关闭。");
                socket.close();
                return;
            }

            // 注册成功后，开始循环监听客户端的其他消息
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = Message.fromJson(line);
                handleMessage(msg);
            }
        } catch (IOException e) {
            // 捕获IO异常，通常意味着客户端断开连接
            String playerName = (player.getName() != null) ? player.getName() : "[未注册]";
            String playerId = (player.getId() != null) ? player.getId() : "[未知ID]";
            System.out.println("玩家 " + playerName + "(" + playerId + ") 连接异常: " + e.getMessage());
        } finally {
            // 确保玩家被从游戏房间移除
            if (player.getId() != null) {
                gameRoom.removePlayer(player.getId());
            }
            // 关闭socket资源
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理从客户端接收到的各种消息
     * @param msg 消息对象
     */
    private void handleMessage(Message msg) {
        // 如果玩家ID还未被分配（即还未成功加入房间），则不处理任何消息
        if (player.getId() == null) {
            return;
        }

        switch (msg.getType()) {
            case BID:
                int bid = msg.a(Integer.class);
                gameRoom.handleBid(player.getId(), bid);
                break;
            case PLAY:
                // 使用 fastjson 将 JSON 字符串反序列化为 Card 列表
                List<Card> cardsToPlay = JSON.parseArray(msg.getContent(), Card.class);
                gameRoom.handlePlay(player.getId(), cardsToPlay);
                break;
            case PASS:
                gameRoom.handlePass(player.getId());
                break;
            case CHAT:
                String chatText = msg.a(String.class);
                gameRoom.handleChat(player.getId(), chatText);
                break;
            case READY:
                gameRoom.handleReady(player.getId());
                break;
            default:
                // 客户端不应该发送其他类型的消息，在此忽略
                System.out.println("收到来自 " + player.getId() + " 的未知类型消息: " + msg.getType());
                break;
        }
    }

    /**
     * 向该处理器对应的客户端发送消息
     * @param message 要发送的消息对象
     */
    public void sendMessage(Message message) {
        // 确保输出流存在且socket未关闭
        if (out != null && !socket.isClosed()) {
            out.println(message.toString());
        }
    }

    // --- Getters and Setters ---

    /**
     * 获取与此处理器关联的Player对象
     * @return Player对象
     */
    public Player getPlayer() {
        return player;
    }

    public List<Card> getHand() {
        return player.getHand();
    }

    public void setHand(List<Card> hand) {
        player.setHand(hand);
    }

    public void setLandlord(boolean isLandlord) {
        player.setLandlord(isLandlord);
    }

    public Socket getSocket() {
        return this.socket;
    }
}