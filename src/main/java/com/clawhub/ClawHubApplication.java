package com.clawhub;

import com.clawhub.config.DockerProperties;
import com.clawhub.config.BootstrapAdminProperties;
import com.clawhub.config.PlatformLimitsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        DockerProperties.class,
        BootstrapAdminProperties.class,
        PlatformLimitsProperties.class
})
public class ClawHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClawHubApplication.class, args);
    }
}
