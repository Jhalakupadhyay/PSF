package com.grobird.psf.video.service;

import com.grobird.psf.config.video.VideoS3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Service
public class S3PresignedService {

    private static final Logger log = LoggerFactory.getLogger(S3PresignedService.class);
    private static final String STUB_BASE_URL = "https://stub.local/psf-videos";

    private final VideoS3Properties properties;
    private final S3Presigner presigner;
    private final S3Client s3Client;

    public S3PresignedService(VideoS3Properties properties) {
        this.properties = properties;
        this.presigner = properties.isStub() ? null : buildPresigner(properties);
        this.s3Client = properties.isStub() ? null : buildS3Client(properties);
    }

    private static S3Presigner buildPresigner(VideoS3Properties props) {
        var builder = S3Presigner.builder().region(Region.of(props.getRegion()));
        if (props.getEndpointOverride() != null && !props.getEndpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(props.getEndpointOverride().trim()));
        }
        return builder.build();
    }

    private static S3Client buildS3Client(VideoS3Properties props) {
        var builder = S3Client.builder().region(Region.of(props.getRegion()));
        if (props.getEndpointOverride() != null && !props.getEndpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(props.getEndpointOverride().trim()));
        }
        return builder.build();
    }

    /**
     * Generate a unique S3 key and presigned PUT URL for uploading the golden pitch deck video.
     */
    public UploadResult generateUploadUrlGoldenPitchDeck(Long tenantId) {
        String key = buildKey(properties.getKeyPrefix(), tenantId, "golden-pitch", null);
        if (properties.isStub()) {
            return new UploadResult(stubUploadUrl(key), key);
        }
        String uploadUrl = presignPut(key, properties.getUploadUrlExpiryMinutes());
        return new UploadResult(uploadUrl, key);
    }

    /**
     * Generate a unique S3 key and presigned PUT URL for uploading a skillset video.
     */
    public UploadResult generateUploadUrlSkillset(Long tenantId, Long skillsetId) {
        String key = buildKey(properties.getKeyPrefix(), tenantId, "skillsets", skillsetId);
        if (properties.isStub()) {
            return new UploadResult(stubUploadUrl(key), key);
        }
        String uploadUrl = presignPut(key, properties.getUploadUrlExpiryMinutes());
        return new UploadResult(uploadUrl, key);
    }

    /**
     * Generate presigned PUT URL for sales golden pitch upload (per opportunity).
     */
    public UploadResult generateUploadUrlSalesGoldenPitch(Long tenantId, Long opportunityId) {
        String key = buildKeySales(properties.getKeyPrefix(), tenantId, opportunityId, "golden-pitch", null);
        if (properties.isStub()) {
            return new UploadResult(stubUploadUrl(key), key);
        }
        String uploadUrl = presignPut(key, properties.getUploadUrlExpiryMinutes());
        return new UploadResult(uploadUrl, key);
    }

    /**
     * Generate presigned PUT URL for sales skillset upload (per opportunity and reference).
     */
    public UploadResult generateUploadUrlSalesSkillset(Long tenantId, Long opportunityId, Long referenceVideoId) {
        String key = buildKeySales(properties.getKeyPrefix(), tenantId, opportunityId, "skillsets", referenceVideoId);
        if (properties.isStub()) {
            return new UploadResult(stubUploadUrl(key), key);
        }
        String uploadUrl = presignPut(key, properties.getUploadUrlExpiryMinutes());
        return new UploadResult(uploadUrl, key);
    }

    /**
     * Generate a presigned GET URL for playback (download) of a video stored at the given S3 key.
     */
    public String generatePlaybackUrl(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return null;
        }
        if (properties.isStub()) {
            return stubPlaybackUrl(s3Key);
        }
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(s3Key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(properties.getPlaybackUrlExpiryMinutes()))
                .getObjectRequest(getRequest)
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Delete an object from S3 by its key. No-op in stub mode.
     */
    public void deleteObject(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return;
        }
        if (properties.isStub()) {
            log.info("Stub mode: skipping S3 delete for key {}", s3Key);
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(s3Key)
                    .build());
            log.info("Deleted S3 object: {}", s3Key);
        } catch (Exception e) {
            log.error("Failed to delete S3 object {}: {}", s3Key, e.getMessage(), e);
        }
    }

    private String stubUploadUrl(String key) {
        return STUB_BASE_URL + "/upload?key=" + key.replace("/", "%2F");
    }

    private String stubPlaybackUrl(String key) {
        return STUB_BASE_URL + "/playback?key=" + key.replace("/", "%2F");
    }

    private String buildKey(String prefix, Long tenantId, String type, Long skillsetId) {
        String base = prefix + tenantId + "/" + type + "/";
        if (skillsetId != null) {
            base += skillsetId + "/";
        }
        return base + UUID.randomUUID() + ".mp4";
    }

    private String buildKeySales(String prefix, Long tenantId, Long opportunityId, String subType, Long referenceVideoId) {
        String base = prefix + tenantId + "/sales/" + opportunityId + "/" + subType + "/";
        if (referenceVideoId != null) {
            base += referenceVideoId + "/";
        }
        return base + UUID.randomUUID() + ".mp4";
    }

    private String presignPut(String key, int expiryMinutes) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryMinutes))
                .putObjectRequest(putRequest)
                .build();
        return presigner.presignPutObject(presignRequest).url().toString();
    }

    public record UploadResult(String uploadUrl, String key) {}
}
