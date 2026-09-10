package com.racehorse.media.entity;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.persistence.BaseEntity;
import com.racehorse.horse.entity.Horse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "horse_profile_files")
public class HorseProfileFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private UserAccount uploadedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private UserAccount approvedBy;

    @Column(name = "cloudinary_asset_id", nullable = false, length = 100)
    private String cloudinaryAssetId;

    @Column(name = "cloudinary_public_id", nullable = false, length = 300)
    private String cloudinaryPublicId;

    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;

    @Column(name = "file_format", length = 30)
    private String fileFormat;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "secure_url", nullable = false, length = 1000)
    private String secureUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileFileStatus status;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected HorseProfileFile() {
    }

    public HorseProfileFile(
            Horse horse,
            UserAccount uploadedBy,
            String cloudinaryAssetId,
            String cloudinaryPublicId,
            String resourceType,
            String fileFormat,
            String originalFilename,
            String contentType,
            long fileSize,
            String secureUrl,
            ProfileFileStatus status) {
        this.horse = horse;
        this.uploadedBy = uploadedBy;
        this.cloudinaryAssetId = cloudinaryAssetId;
        this.cloudinaryPublicId = cloudinaryPublicId;
        this.resourceType = resourceType;
        this.fileFormat = fileFormat;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.secureUrl = secureUrl;
        this.status = status;
    }

    public void approve(UserAccount approver, Instant now) {
        this.status = ProfileFileStatus.ACTIVE;
        this.approvedBy = approver;
        this.approvedAt = now;
    }

    public void replace() {
        this.status = ProfileFileStatus.REPLACED;
    }

    public void delete() {
        this.status = ProfileFileStatus.DELETED;
    }

    public Horse getHorse() { return horse; }
    public UserAccount getUploadedBy() { return uploadedBy; }
    public UserAccount getApprovedBy() { return approvedBy; }
    public String getCloudinaryAssetId() { return cloudinaryAssetId; }
    public String getCloudinaryPublicId() { return cloudinaryPublicId; }
    public String getResourceType() { return resourceType; }
    public String getFileFormat() { return fileFormat; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getSecureUrl() { return secureUrl; }
    public ProfileFileStatus getStatus() { return status; }
    public Instant getApprovedAt() { return approvedAt; }
}
