package com.jargoyle.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_summaries")
public class DocumentSummary {
    public DocumentSummary() { }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(columnDefinition = "text")
    private String plainSummary;

    @Column(columnDefinition = "jsonb")
    private String keyFacts;

    @Column(columnDefinition = "jsonb")
    private String flaggedTerms;

    @CreationTimestamp
    private Instant generatedAt;

    public UUID getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getPlainSummary() {
        return plainSummary;
    }

    public void setPlainSummary(String plainSummary) {
        this.plainSummary = plainSummary;
    }

    public String getKeyFacts() {
        return keyFacts;
    }

    public void setKeyFacts(String keyFacts) {
        this.keyFacts = keyFacts;
    }

    public String getFlaggedTerms() {
        return flaggedTerms;
    }

    public void setFlaggedTerms(String flaggedTerms) {
        this.flaggedTerms = flaggedTerms;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

}
