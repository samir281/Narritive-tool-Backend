package com.narrativenews.domain;

import com.narrativenews.domain.Enums.*;
import com.narrativenews.web.DomainException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "editions", indexes = @Index(name = "idx_editions_public", columnList = "status, edition_date"))
@Getter @Setter
public class Edition {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "edition_date", nullable = false, unique = true) private LocalDate editionDate;
    @Column(nullable = false, length = 200) private String title;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 10) private EditionStatus status = EditionStatus.DRAFT;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private PdfStatus pdfStatus = PdfStatus.NONE;
    @Column(length = 500) private String pdfError;
    private Integer pageCount;
    @Column(nullable = false) private UUID createdBy;
    private Instant publishedAt;
    @Version private int version;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    @PreUpdate void touch() { updatedAt = Instant.now(); }

    public void startProcessing() { pdfStatus = PdfStatus.PROCESSING; pdfError = null; }
    public void markPdfReady(int pages) { pdfStatus = PdfStatus.READY; pageCount = pages; pdfError = null; }
    public void markPdfFailed(String reason) {
        pdfStatus = PdfStatus.FAILED;
        pdfError = reason == null ? "Unknown error" : reason.substring(0, Math.min(reason.length(), 480));
    }
    public void publish() {
        if (pdfStatus != PdfStatus.READY) throw new DomainException(HttpStatus.CONFLICT, "PDF_NOT_READY", "Newspaper pages are not ready yet");
        status = EditionStatus.PUBLISHED;
        if (publishedAt == null) publishedAt = Instant.now();
    }
    public void unpublish() { status = EditionStatus.DRAFT; }
}
