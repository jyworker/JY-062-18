package com.ddz.common;

public enum MessageType {
    // 客户端 -> 服务器
    REGISTER,       // 注册 (携带玩家昵称)
    CHAT,           // 聊天
    BID,            // 叫/抢地主 (内容为分数)
    PLAY,           // 出牌
    PASS,           // 不出
    READY,          // 新增: 玩家准备好开始下一局

    // 服务器 -> 客户端
    REGISTER_SUCCESS, // 注册成功，返回玩家ID和所有玩家信息
    GAME_START,       // 游戏开始，通知所有玩家
    DEAL_CARDS,       // 发牌信息
    BID_INFO,         // 轮到某人叫地主
    LANDLORD_CONFIRMED, // 地主确认，公布底牌
    GAME_UPDATE,      // 游戏状态更新 (轮到谁，上家出的牌等)
    PLAY_INVALID,     // 出牌无效
    GAME_OVER,        // 游戏结束 (将携带更丰富的结算信息)
    CHAT_MESSAGE,     // 聊天消息广播
    PROMPT            // 通用提示信息
}
