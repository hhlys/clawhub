package com.clawhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clawhub.platform")
public class PlatformLimitsProperties {

    private int instanceLimit = 5;

    public int getInstanceLimit() {
        return instanceLimit;
    }

    public void setInstanceLimit(int instanceLimit) {
        this.instanceLimit = instanceLimit;
    }
}
