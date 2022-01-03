package com.yeqk97.ociinstancebot;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot")
@Data
public class BotConfigurationProperties {
    private String apiConfigPath;
    private String apiConfigProfile;
    private String regionId;
    private String compartmentId;
    private String retryRateMinutes;
    private Instance instance;

    @Data
    public static class Instance {
        private String displayName;
        private String availabilityDomain;
        private String imageId;
        private String shape;
        private ShapeConfig shapeConfig;
        private boolean assignPublicIp;
        private String subnetId;
        private String privateIp;
        private String sshKey;
        private long bootVolumeSizeInGbs;
    }

    @Data
    public static class ShapeConfig {
        private long memoryInGbs;
        private long ocpus;
    }
}
