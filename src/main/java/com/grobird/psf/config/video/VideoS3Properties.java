package com.grobird.psf.config.video;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.videos.s3")
public class VideoS3Properties {

    private String bucket = "psf-videos";
    private String region = "us-east-1";
    private String keyPrefix = "videos/";
    private String endpointOverride;
    private int uploadUrlExpiryMinutes = 60;
    private int playbackUrlExpiryMinutes = 60;
    /** When true, return fake upload/playback URLs so you can test without AWS credentials or S3. */
    private boolean stub = false;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix != null ? keyPrefix : "";
    }

    public int getUploadUrlExpiryMinutes() {
        return uploadUrlExpiryMinutes;
    }

    public void setUploadUrlExpiryMinutes(int uploadUrlExpiryMinutes) {
        this.uploadUrlExpiryMinutes = uploadUrlExpiryMinutes;
    }

    public int getPlaybackUrlExpiryMinutes() {
        return playbackUrlExpiryMinutes;
    }

    public void setPlaybackUrlExpiryMinutes(int playbackUrlExpiryMinutes) {
        this.playbackUrlExpiryMinutes = playbackUrlExpiryMinutes;
    }

    public String getEndpointOverride() {
        return endpointOverride;
    }

    public void setEndpointOverride(String endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    public boolean isStub() {
        return stub;
    }

    public void setStub(boolean stub) {
        this.stub = stub;
    }
}
