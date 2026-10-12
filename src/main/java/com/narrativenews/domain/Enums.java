package com.narrativenews.domain;

public final class Enums {
    private Enums() {}
    public enum EditionStatus { DRAFT, PUBLISHED }
    public enum PdfStatus { NONE, PROCESSING, READY, FAILED }
    public enum PostStatus { DRAFT, PUBLISHED }
    public enum CommentStatus { VISIBLE, HIDDEN, DELETED }
    public enum AccountStatus { ACTIVE, DISABLED }
}
