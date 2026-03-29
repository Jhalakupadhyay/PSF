package com.grobird.psf.video.service;

import com.grobird.psf.config.video.VideoS3Properties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.net.URI;

/**
 * Downloads video bytes from S3 for forwarding to the pitch-analyzer (multipart upload).
 * Caller must close the Resource's InputStream when done.
 */
@Service
public class S3VideoDownloadService {

    private final VideoS3Properties properties;
    private final S3Client s3Client;

    public S3VideoDownloadService(VideoS3Properties properties) {
        this.properties = properties;
        this.s3Client = properties.isStub() ? null : buildS3Client(properties);
    }

    private static S3Client buildS3Client(VideoS3Properties props) {
        var builder = S3Client.builder().region(Region.of(props.getRegion()));
        if (props.getEndpointOverride() != null && !props.getEndpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(props.getEndpointOverride().trim()));
        }
        return builder.build();
    }

    public Resource getResource(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            throw new IllegalArgumentException("S3 key must not be blank");
        }
        if (properties.isStub()) {
            throw new IllegalStateException("S3 stub mode: cannot download object for pitch-analyzer. Set app.videos.s3.stub=false and configure real S3.");
        }
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(s3Key)
                .build();
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            // Buffer the entire object into memory so RestTemplate can read it safely for multipart upload.
            byte[] bytes = response.readAllBytes();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    int lastSlash = s3Key.lastIndexOf('/');
                    return lastSlash >= 0 ? s3Key.substring(lastSlash + 1) : s3Key;
                }
            };
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read S3 object for key " + s3Key, e);
        }
    }
}
