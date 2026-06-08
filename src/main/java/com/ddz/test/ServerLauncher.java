package com.ddz.test;

import com.ddz.server.GameServer;

public class ServerLauncher {
    public static void main(String[] args) {
        GameServer server = new GameServer();
        server.start();
    }
}
