package com.clawhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clawhub.provisioning")
public class ClawProvisioningProperties {

    private String defaultHost = "101.251.162.105";
    private int hostPortStart = 18088;
    private int containerPort = 8088;
    private String dataVolumeHostRootPath = "/srv/clawhub/instances";
    private String dataVolumeContainerPath = "/root/.qwenpaw";
    private String qwenpawImage = "qwenpaw:local";
    private String defaultProductVersion = "1.0";

    public String getDefaultHost() {
        return defaultHost;
    }

    public void setDefaultHost(String defaultHost) {
        this.defaultHost = defaultHost;
    }

    public int getHostPortStart() {
        return hostPortStart;
    }

    public void setHostPortStart(int hostPortStart) {
        this.hostPortStart = hostPortStart;
    }

    public int getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(int containerPort) {
        this.containerPort = containerPort;
    }

    public String getDataVolumeHostRootPath() {
        return dataVolumeHostRootPath;
    }

    public void setDataVolumeHostRootPath(String dataVolumeHostRootPath) {
        this.dataVolumeHostRootPath = dataVolumeHostRootPath;
    }

    public String getDataVolumeContainerPath() {
        return dataVolumeContainerPath;
    }

    public void setDataVolumeContainerPath(String dataVolumeContainerPath) {
        this.dataVolumeContainerPath = dataVolumeContainerPath;
    }

    public String getQwenpawImage() {
        return qwenpawImage;
    }

    public void setQwenpawImage(String qwenpawImage) {
        this.qwenpawImage = qwenpawImage;
    }

    public String getDefaultProductVersion() {
        return defaultProductVersion;
    }

    public void setDefaultProductVersion(String defaultProductVersion) {
        this.defaultProductVersion = defaultProductVersion;
    }
}
