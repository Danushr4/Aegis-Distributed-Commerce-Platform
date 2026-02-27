package com.aegis.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private int postOrdersCapacity = 100;
    private double postOrdersRefillPerSecond = 20;
    private int getOrderCapacity = 200;
    private double getOrderRefillPerSecond = 50;

    public int getPostOrdersCapacity() {
        return postOrdersCapacity;
    }

    public void setPostOrdersCapacity(int postOrdersCapacity) {
        this.postOrdersCapacity = postOrdersCapacity;
    }

    public double getPostOrdersRefillPerSecond() {
        return postOrdersRefillPerSecond;
    }

    public void setPostOrdersRefillPerSecond(double postOrdersRefillPerSecond) {
        this.postOrdersRefillPerSecond = postOrdersRefillPerSecond;
    }

    public int getGetOrderCapacity() {
        return getOrderCapacity;
    }

    public void setGetOrderCapacity(int getOrderCapacity) {
        this.getOrderCapacity = getOrderCapacity;
    }

    public double getGetOrderRefillPerSecond() {
        return getOrderRefillPerSecond;
    }

    public void setGetOrderRefillPerSecond(double getOrderRefillPerSecond) {
        this.getOrderRefillPerSecond = getOrderRefillPerSecond;
    }
}
