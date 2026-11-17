package com.ecommerce.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;

public class DemoPublishRequest {

    @NotBlank
    private String exchange = "commands";

    @NotBlank
    private String routingKey;

    private String body = "demo message";

    private boolean fail = false;

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public boolean isFail() {
        return fail;
    }

    public void setFail(boolean fail) {
        this.fail = fail;
    }
}
