package com.ddz.common;

import java.util.ArrayList;
import java.util.List;

public class Player {
    private String id;
    private String name;
    private List<Card> hand = new ArrayList<>();
    private boolean isLandlord = false;
    private int score = 0; // 新增：累计分数

    // FastJSON 需要无参构造函数
    public Player() {}

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Card> getHand() { return hand; }
    public void setHand(List<Card> hand) { this.hand = hand; }
    public boolean isLandlord() { return isLandlord; }
    public void setLandlord(boolean landlord) { this.isLandlord = landlord; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public void addScore(int points) { this.score += points; }
}
