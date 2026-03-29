package com.grobird.psf.video.entity;

/**
 * Type of reference video in the pitch-analyzer integration.
 * Both use the same Python golden-pitch-deck API; we distinguish in our DB.
 */
public enum ReferenceVideoType {
    GOLDEN_PITCH,
    SKILLSET
}
