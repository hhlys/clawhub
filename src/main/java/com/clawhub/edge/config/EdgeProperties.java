package com.clawhub.edge.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clawhub.edge")
public class EdgeProperties {

    /**
     * Optional shared token for MVP edge registration. If blank, token checks
     * are skipped so local development can start quickly.
     */
    private String registrationToken = "";

    private Duration offlineAfter = Duration.ofSeconds(90);

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }

    public Duration getOfflineAfter() {
        return offlineAfter;
    }

    public void setOfflineAfter(Duration offlineAfter) {
        this.offlineAfter = offlineAfter;
    }
}
