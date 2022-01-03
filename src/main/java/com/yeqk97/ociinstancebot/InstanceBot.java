package com.yeqk97.ociinstancebot;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.ComputeWaiters;
import com.oracle.bmc.core.model.CreateVnicDetails;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.InstanceAgentPluginConfigDetails;
import com.oracle.bmc.core.model.InstanceOptions;
import com.oracle.bmc.core.model.InstanceSourceDetails;
import com.oracle.bmc.core.model.InstanceSourceViaImageDetails;
import com.oracle.bmc.core.model.LaunchInstanceAgentConfigDetails;
import com.oracle.bmc.core.model.LaunchInstanceAvailabilityConfigDetails;
import com.oracle.bmc.core.model.LaunchInstanceDetails;
import com.oracle.bmc.core.model.LaunchInstanceShapeConfigDetails;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.requests.LaunchInstanceRequest;
import com.oracle.bmc.core.requests.ListInstancesRequest;
import com.oracle.bmc.core.responses.GetInstanceResponse;
import com.oracle.bmc.core.responses.LaunchInstanceResponse;
import com.oracle.bmc.workrequests.WorkRequestClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InstanceBot {

    private final ComputeClient computeClient;

    private final WorkRequestClient workRequestClient;

    private final ComputeWaiters computeWaiters;

    private final BotConfigurationProperties properties;

    private static final Set<String> PLUGIN_CONFIG_NAMES =
            Set.of("Vulnerability Scanning", "Compute Instance Monitoring",
                    "Bastion");

    private boolean instanceCreated;

    public InstanceBot(
            BotConfigurationProperties properties) throws IOException {
        this.properties = properties;
        final ConfigFileReader.ConfigFile configFile =
                ConfigFileReader.parse(properties.getApiConfigPath(),
                        properties.getApiConfigProfile());
        final AuthenticationDetailsProvider provider =
                new ConfigFileAuthenticationDetailsProvider(configFile);
        computeClient = ComputeClient.builder().build(provider);
        computeClient.setRegion(properties.getRegionId());
        workRequestClient =
                WorkRequestClient.builder().build(provider);
        workRequestClient.setRegion(properties.getRegionId());
        computeWaiters = computeClient.newWaiters(workRequestClient);
    }

    @Scheduled(fixedRateString = "${bot.retry-rate-minutes}", timeUnit = TimeUnit.MINUTES)
    private void tryCreateInstance() {
        if (instanceCreated || existInstance()) {
            return;
        }
        createInstance();
    }

    private boolean existInstance() {
        instanceCreated =computeClient.listInstances(
                        ListInstancesRequest.builder().compartmentId(
                                properties.getCompartmentId()).build())
                .getItems()
                .stream()
                .anyMatch(i -> i.getShape()
                        .equals(properties.getInstance().getShape()));

        if (instanceCreated) {
            log.info("There is already an instance with shape {}",
                    properties.getInstance().getShape());
        }

        return instanceCreated;
    }

    private void createInstance() {

        BotConfigurationProperties.Instance ic = properties.getInstance();

        LaunchInstanceShapeConfigDetails shapeConfig =
                LaunchInstanceShapeConfigDetails.builder()
                        .memoryInGBs(
                                (float) ic.getShapeConfig().getMemoryInGbs())
                        .ocpus((float) ic.getShapeConfig().getOcpus())
                        .build();

        InstanceSourceDetails instanceSourceDetails =
                InstanceSourceViaImageDetails.builder()
                        .imageId(ic.getImageId())
                        .bootVolumeSizeInGBs(ic.getBootVolumeSizeInGbs())
                        .build();

        CreateVnicDetails createVnicDetails = CreateVnicDetails.builder()
                .assignPublicIp(ic.isAssignPublicIp())
                .subnetId(ic.getSubnetId())
                .privateIp(ic.getPrivateIp())
                .assignPrivateDnsRecord(true)
                .build();

        List<InstanceAgentPluginConfigDetails>
                pluginsConfigs = PLUGIN_CONFIG_NAMES.stream()
                .map(n -> InstanceAgentPluginConfigDetails.builder()
                        .name(n)
                        .desiredState(
                                InstanceAgentPluginConfigDetails.DesiredState.Disabled)
                        .build())
                .collect(
                        Collectors.toList());

        LaunchInstanceAgentConfigDetails agentConfig =
                LaunchInstanceAgentConfigDetails.builder()
                        .pluginsConfig(pluginsConfigs)
                        .isMonitoringDisabled(false)
                        .isManagementDisabled(false)
                        .build();


        InstanceOptions instanceOptions = InstanceOptions.builder()
                .areLegacyImdsEndpointsDisabled(false)
                .build();

        LaunchInstanceAvailabilityConfigDetails
                availabilityConfig =
                LaunchInstanceAvailabilityConfigDetails.builder()
                        .recoveryAction(
                                LaunchInstanceAvailabilityConfigDetails.RecoveryAction.RestoreInstance)
                        .build();

        LaunchInstanceDetails launchInstanceDetails =
                LaunchInstanceDetails.builder()
                        .metadata(
                                Collections.singletonMap("ssh_authorized_keys",
                                        ic.getSshKey()))
                        .shape(ic.getShape())
                        .compartmentId(properties.getCompartmentId())
                        .displayName(ic.getDisplayName())
                        .availabilityDomain(ic.getAvailabilityDomain())
                        .sourceDetails(instanceSourceDetails)
                        .isPvEncryptionInTransitEnabled(true)
                        .createVnicDetails(createVnicDetails)
                        .agentConfig(agentConfig)
                        .instanceOptions(instanceOptions)
                        .availabilityConfig(availabilityConfig)
                        .shapeConfig(shapeConfig)
                        .build();

        try {
            LaunchInstanceRequest launchInstanceRequest =
                    LaunchInstanceRequest.builder()
                            .launchInstanceDetails(launchInstanceDetails)
                            .build();
            LaunchInstanceResponse launchInstanceResponse =
                    computeWaiters.forLaunchInstance(launchInstanceRequest)
                            .execute();

            // Stop even if get instance fails
            instanceCreated = true;

            GetInstanceRequest getInstanceRequest =
                    GetInstanceRequest.builder()
                            .instanceId(
                                    launchInstanceResponse.getInstance()
                                            .getId())
                            .build();
            GetInstanceResponse getInstanceResponse =
                    computeWaiters
                            .forInstance(getInstanceRequest,
                                    Instance.LifecycleState.Running)
                            .execute();
            Instance instance = getInstanceResponse.getInstance();


            log.info("Launched Instance: {}", instance.getId());
            log.info(String.valueOf(instance));

            closeConnections();
        } catch (Exception e) {
            log.error(String.format("Could not create instance, waiting %s minutes before retry.", properties.getRetryRateMinutes()), e);
        }
    }

    private void closeConnections() {
        computeClient.close();
        workRequestClient.close();
    }
}
