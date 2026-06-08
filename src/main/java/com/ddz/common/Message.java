package com.ddz.common;

import com.alibaba.fastjson.JSON;

public class Message {
    private MessageType type;
    private String content; // 存储JSON格式的数据

    public Message() {}

    public Message(MessageType type, Object payload) {
        this.type = type;
        this.content = JSON.toJSONString(payload);
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // 泛型方法，用于将content反序列化为指定类型的对象
    public <T> T a(Class<T> clazz) {
        return JSON.parseObject(this.content, clazz);
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    public static Message fromJson(String json) {
        return JSON.parseObject(json, Message.class);
    }
}
