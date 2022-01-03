package com.yeqk97.ociinstancebot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(BotConfigurationProperties.class)
@EnableScheduling
public class OciInstanceBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(OciInstanceBotApplication.class, args);
    }

}
