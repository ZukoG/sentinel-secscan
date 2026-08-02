package com.sentinel.secscan.domain;

/**
 * Only USER exists today. No admin/operator role is planned for v1 (see
 * docs/SRS.md section 2.3), this exists so User.role has a real type
 * instead of a free-text string, without inventing an ADMIN value nothing
 * uses yet.
 */
public enum Role {
    USER
}
