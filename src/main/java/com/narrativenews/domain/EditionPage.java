package com.narrativenews.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "edition_pages", uniqueConstraints = @UniqueConstraint(columnNames = {"edition_id", "page_no"}))
@Getter @Setter
public class EditionPage {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "edition_id", nullable = false) private UUID editionId;
    @Column(name = "page_no", nullable = false) private int pageNo;
    @Column(nullable = false) private UUID smallMediaId;
    @Column(nullable = false) private UUID largeMediaId;
    private UUID thumbMediaId;                      // null for pages rendered before thumbnails existed
    @Column(nullable = false) private int width;
    @Column(nullable = false) private int height;
}
