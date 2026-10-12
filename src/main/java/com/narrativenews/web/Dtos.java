package com.narrativenews.web;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class Dtos {
    private Dtos() {}

    // ---- requests ----
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RegisterRequest(@NotBlank @Size(max = 60) String displayName,
                                  @NotBlank @Email @Size(max = 255) String email,
                                  @NotBlank @Size(min = 8, max = 100) String password) {}
    public record CreateAdminRequest(@NotBlank @Size(max = 100) String name,
                                     @NotBlank @Email @Size(max = 255) String email,
                                     @NotBlank @Size(min = 8, max = 100) String password) {}
    public record CreateEditionRequest(@NotNull LocalDate editionDate, @NotBlank @Size(max = 200) String title) {}
    public record UpdateEditionRequest(@Size(min = 1, max = 200) String title) {}
    public record CreatePostRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 20000) String body) {}
    public record UpdatePostRequest(@Size(min = 1, max = 200) String title, @Size(min = 1, max = 20000) String body) {}
    public record ReorderRequest(@NotEmpty List<UUID> postIds) {}
    public record CommentRequest(@NotBlank @Size(max = 1000) String body) {}

    // ---- responses ----
    public record UserView(UUID id, String name, String email, String role) {}
    public record AuthResponse(String accessToken, UserView user) {}

    public record PageView(int pageNo, String thumbUrl, String url, String zoomUrl, int width, int height) {}
    /** One OCR text block; position and size are fractions (0..1) of the page width / height. */
    public record TextBlock(double x, double y, double w, double h, String text) {}
    /** status: READY, PENDING (text recognition still running) or UNAVAILABLE (OCR not installed). */
    public record PageTextView(String status, List<TextBlock> blocks) {}
    public record PublicPostView(UUID id, String title, String body, String imageUrl,
                                 int likeCount, int commentCount, Instant publishedAt) {}
    public record PublicEditionView(UUID id, String date, String title, String shareUrl, List<PageView> pages,
                                    List<PublicPostView> posts, String prevDate, String nextDate) {}

    public record EditionSummary(UUID id, String date, String title, String status, String pdfStatus, Integer pageCount) {}
    public record AdminPostView(UUID id, String title, String body, String imageUrl, int position, String status,
                                int likeCount, int commentCount) {}
    public record AdminEditionView(UUID id, String date, String title, String status, String pdfStatus, String pdfError,
                                   Integer pageCount, List<PageView> pages, List<AdminPostView> posts) {}

    public record CommentView(UUID id, UUID authorId, String authorName, String body, Instant createdAt, String status) {}
    public record CommentPage(List<CommentView> items, String nextCursor) {}
    public record LikeResult(boolean liked, int likeCount) {}
    public record AdminUserView(UUID id, String name, String email, String status) {}
}
