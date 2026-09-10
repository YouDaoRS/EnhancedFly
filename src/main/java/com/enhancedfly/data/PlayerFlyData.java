package com.enhancedfly.data;

import java.util.UUID;

public class PlayerFlyData {
    
    private final UUID uuid;
    private String playerName;
    private boolean permanentFly;
    private long tempFlyTime; // 秒
    private long lastLogin;
    
    // 统计数据
    private long totalFlyTime; // 总飞行时间（秒）
    private double totalDistance; // 总飞行距离（方块）
    private int totalPurchases; // 购买次数
    private long firstFlyDate; // 首次飞行时间戳
    private long lastFlyDate; // 最后飞行时间戳
    private double maxAltitude; // 最高飞行高度
    
    public PlayerFlyData(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.permanentFly = false;
        this.tempFlyTime = 0;
        this.lastLogin = System.currentTimeMillis();
        this.totalFlyTime = 0;
        this.totalDistance = 0;
        this.totalPurchases = 0;
        this.firstFlyDate = 0;
        this.lastFlyDate = 0;
        this.maxAltitude = 0;
    }
    
    public PlayerFlyData(UUID uuid, String playerName, boolean permanentFly, long tempFlyTime, long lastLogin) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.permanentFly = permanentFly;
        this.tempFlyTime = Math.max(0, tempFlyTime);
        this.lastLogin = lastLogin;
        this.totalFlyTime = 0;
        this.totalDistance = 0;
        this.totalPurchases = 0;
        this.firstFlyDate = 0;
        this.lastFlyDate = 0;
        this.maxAltitude = 0;
    }
    
    public void setPlayerName(String name) { this.playerName = name; }

    public PlayerFlyData snapshot() {
        PlayerFlyData copy = new PlayerFlyData(uuid, playerName, permanentFly, tempFlyTime, lastLogin);
        copy.totalFlyTime = totalFlyTime;
        copy.totalDistance = totalDistance;
        copy.totalPurchases = totalPurchases;
        copy.firstFlyDate = firstFlyDate;
        copy.lastFlyDate = lastFlyDate;
        copy.maxAltitude = maxAltitude;
        return copy;
    }

    public UUID getUuid() {
        return uuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public boolean hasPermanentFly() {
        return permanentFly;
    }
    
    public void setHasPermanentFly(boolean permanentFly) {
        this.permanentFly = permanentFly;
    }
    
    public long getTempFlyTime() {
        return tempFlyTime;
    }
    
    public void setTempFlyTime(long tempFlyTime) {
        this.tempFlyTime = Math.max(0, tempFlyTime);
    }
    
    public void addTime(long seconds) {
        if (seconds <= 0 || seconds > Long.MAX_VALUE - tempFlyTime) throw new IllegalArgumentException("飞行时间必须为正数且不能溢出");
        this.tempFlyTime += seconds;
    }
    
    public void consumeTime(long seconds) {
        if (seconds < 0) throw new IllegalArgumentException("消耗时间不能为负数");
        this.tempFlyTime = Math.max(0, this.tempFlyTime - seconds);
    }
    
    public long getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    public boolean hasAnyFly() {
        return permanentFly || tempFlyTime > 0;
    }
    
    // 统计方法
    public long getTotalFlyTime() {
        return totalFlyTime;
    }
    
    public void addTotalFlyTime(long seconds) {
        if (seconds > 0) this.totalFlyTime += Math.min(seconds, Long.MAX_VALUE - this.totalFlyTime);
    }
    
    public void setTotalFlyTime(long totalFlyTime) {
        this.totalFlyTime = Math.max(0, totalFlyTime);
    }
    
    public double getTotalDistance() {
        return totalDistance;
    }
    
    public void addDistance(double distance) {
        if (Double.isFinite(distance) && distance > 0 && Double.isFinite(this.totalDistance + distance)) this.totalDistance += distance;
    }
    
    public void setTotalDistance(double totalDistance) {
        this.totalDistance = Double.isFinite(totalDistance) ? Math.max(0, totalDistance) : 0;
    }
    
    public int getTotalPurchases() {
        return totalPurchases;
    }
    
    public void incrementPurchases() {
        if (this.totalPurchases < Integer.MAX_VALUE) this.totalPurchases++;
    }
    
    public void setTotalPurchases(int totalPurchases) {
        this.totalPurchases = Math.max(0, totalPurchases);
    }
    
    public long getFirstFlyDate() {
        return firstFlyDate;
    }
    
    public void setFirstFlyDate(long firstFlyDate) {
        if (this.firstFlyDate == 0) {
            this.firstFlyDate = firstFlyDate;
        }
    }
    
    public long getLastFlyDate() {
        return lastFlyDate;
    }
    
    public void setLastFlyDate(long lastFlyDate) {
        this.lastFlyDate = lastFlyDate;
    }
    
    public double getMaxAltitude() {
        return maxAltitude;
    }
    
    public void updateMaxAltitude(double altitude) {
        if (altitude > this.maxAltitude) {
            this.maxAltitude = altitude;
        }
    }
    
    public void setMaxAltitude(double maxAltitude) {
        this.maxAltitude = maxAltitude;
    }
}

