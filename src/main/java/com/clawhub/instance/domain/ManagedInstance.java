package com.clawhub.instance.domain;

import com.clawhub.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "managed_instances")
public class ManagedInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, unique = true)
    private AppUser ownerUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductType productType;

    @Column(nullable = false, length = 128, unique = true)
    private String instanceName;

    @Column(nullable = false, length = 128, unique = true)
    private String containerName;

    @Column(nullable = false, length = 255)
    private String dockerImage;

    @Column(nullable = false, length = 128)
    private String host = "127.0.0.1";

    @Column(nullable = false, unique = true)
    private Integer hostPort;

    @Column(nullable = false)
    private Integer containerPort;

    @Column(nullable = false, length = 512)
    private String dataVolumeHostPath;

    @Column(nullable = false, length = 512)
    private String dataVolumeContainerPath;

    @Column(length = 512)
    private String publicBaseUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InstanceStatus status = InstanceStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwnerUser() {
        return ownerUser;
    }

    public void setOwnerUser(AppUser ownerUser) {
        this.ownerUser = ownerUser;
    }

    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getHostPort() {
        return hostPort;
    }

    public void setHostPort(Integer hostPort) {
        this.hostPort = hostPort;
    }

    public Integer getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(Integer containerPort) {
        this.containerPort = containerPort;
    }

    public String getDataVolumeHostPath() {
        return dataVolumeHostPath;
    }

    public void setDataVolumeHostPath(String dataVolumeHostPath) {
        this.dataVolumeHostPath = dataVolumeHostPath;
    }

    public String getDataVolumeContainerPath() {
        return dataVolumeContainerPath;
    }

    public void setDataVolumeContainerPath(String dataVolumeContainerPath) {
        this.dataVolumeContainerPath = dataVolumeContainerPath;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
