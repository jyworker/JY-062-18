package com.ddz.test;

import com.ddz.client.GameClient;

public class ClientLauncher4 {
    public static void main(String[] args) {
        // 在实际局域网环境中，将 "localhost" 替换为服务器的IP地址
        GameClient client = new GameClient("localhost", 12345);
        client.start();
    }
}
