package com.sentinel.secscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Unidirectional link to the owning User only, no back-collection on User.
 * Nothing requires walking User -> Websites via the entity graph;
 * WebsiteRepository (Day 6) will query by owner id directly.
 */
@Entity
@Table(name = "websites")
public class Website {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(name = "url", nullable = false)
    private String url;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected Website() {
        // required by JPA, not for direct use
    }

    public Website(User owner, String url) {
        this.owner = owner;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
