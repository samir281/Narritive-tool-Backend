package com.narrativenews.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/** OCR result of one page: JSON array of text blocks. Kept apart from EditionPage so page lists stay small. */
@Entity @Table(name = "page_texts") @Getter @Setter
public class PageText {
    @Id private UUID pageId;
    @Column(nullable = false, columnDefinition = "text") private String blocksJson;

    public PageText() {}
    public PageText(UUID pageId, String blocksJson) { this.pageId = pageId; this.blocksJson = blocksJson; }
}
