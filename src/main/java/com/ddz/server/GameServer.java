package com.ddz.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameServer {
    private static final int PORT = 12345;
    private static final int MAX_THREADS = 10;

    public void start() {
        ExecutorService threadPool = Executors.newFixedThreadPool(MAX_THREADS);
        GameRoom gameRoom = new GameRoom();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("斗地主服务器已启动，在端口 " + PORT + " 上等待玩家连接...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("新玩家连接: " + clientSocket.getRemoteSocketAddress());
                threadPool.execute(new ClientHandler(clientSocket, gameRoom));
            }
        } catch (IOException e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }
}
