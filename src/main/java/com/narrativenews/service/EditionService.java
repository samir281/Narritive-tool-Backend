package com.narrativenews.service;

import com.narrativenews.config.AppProperties;
import com.narrativenews.domain.*;
import com.narrativenews.domain.Enums.EditionStatus;
import com.narrativenews.domain.Enums.PdfStatus;
import com.narrativenews.domain.Enums.PostStatus;
import com.narrativenews.repo.*;
import com.narrativenews.web.DomainException;
import com.narrativenews.web.Dtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EditionService {
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes();

    private final EditionRepository editions;
    private final EditionPageRepository pages;
    private final PostRepository posts;
    private final PostLikeRepository likes;
    private final CommentRepository comments;
    private final MediaRepository media;
    private final PageTextRepository texts;
    private final PdfRenderService renderer;
    private final OcrService ocr;
    private final AuditService audit;
    private final AppProperties props;
    private final ObjectMapper json;

    public EditionService(EditionRepository editions, EditionPageRepository pages, PostRepository posts,
                          PostLikeRepository likes, CommentRepository comments, MediaRepository media,
                          PageTextRepository texts, PdfRenderService renderer, OcrService ocr, AuditService audit,
                          AppProperties props, ObjectMapper json) {
        this.editions = editions; this.pages = pages; this.posts = posts; this.likes = likes;
        this.comments = comments; this.media = media; this.texts = texts; this.renderer = renderer; this.ocr = ocr;
        this.audit = audit; this.props = props; this.json = json;
    }

    public static String mediaUrl(UUID id) { return id == null ? null : "/api/public/media/" + id; }

    // =============== Admin ===============

    public List<EditionSummary> listForAdmin(String month) {
        YearMonth ym = parseMonth(month);
        return editions.findByEditionDateBetweenOrderByEditionDate(ym.atDay(1), ym.atEndOfMonth()).stream()
                .map(e -> new EditionSummary(e.getId(), e.getEditionDate().toString(), e.getTitle(),
                        e.getStatus().name(), e.getPdfStatus().name(), e.getPageCount()))
                .toList();
    }

    public Edition create(UUID adminId, LocalDate date, String title) {
        if (editions.findByEditionDate(date).isPresent())
            throw DomainException.conflict("EDITION_DATE_EXISTS", "There is already a newspaper for " + date);
        Edition e = new Edition();
        e.setEditionDate(date);
        e.setTitle(title.trim());
        e.setCreatedBy(adminId);
        e = editions.save(e);
        audit.log(adminId, "EDITION_CREATED", "EDITION", e.getId());
        return e;
    }

    public AdminEditionView getForAdmin(UUID id) {
        Edition e = load(id);
        List<PageView> pv = pageViews(e.getId());
        List<AdminPostView> av = posts.findByEditionIdOrderByPositionAsc(id).stream().map(PostService::toAdminView).toList();
        return new AdminEditionView(e.getId(), e.getEditionDate().toString(), e.getTitle(), e.getStatus().name(),
                e.getPdfStatus().name(), e.getPdfError(), e.getPageCount(), pv, av);
    }

    public void updateTitle(UUID adminId, UUID id, String title) {
        if (title == null) return;
        Edition e = load(id);
        e.setTitle(title.trim());
        editions.save(e);
        audit.log(adminId, "EDITION_UPDATED", "EDITION", id);
    }

    /** Accepts the PDF, marks the edition PROCESSING and hands the bytes to the background renderer. */
    public void uploadPdf(UUID adminId, UUID id, MultipartFile file) {
        Edition e = load(id);
        if (e.getPdfStatus() == PdfStatus.PROCESSING)
            throw DomainException.conflict("PDF_BUSY", "A PDF is already being processed for this edition");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException ex) { throw DomainException.bad("UPLOAD_FAILED", "Could not read the file"); }
        if (bytes.length < PDF_MAGIC.length || !startsWithMagic(bytes))
            throw DomainException.bad("INVALID_FILE_TYPE", "That file is not a PDF");

        e.startProcessing();
        editions.save(e);
        audit.log(adminId, "PDF_UPLOADED", "EDITION", id);
        try {
            renderer.render(id, bytes);
        } catch (TaskRejectedException ex) {
            e = load(id);
            e.markPdfFailed("The server is busy rendering other newspapers. Try again in a minute.");
            editions.save(e);
            throw new DomainException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "BUSY", "Server is busy, try again shortly");
        }
    }

    public void publish(UUID adminId, UUID id) {
        Edition e = load(id);
        e.publish();
        editions.save(e);
        audit.log(adminId, "EDITION_PUBLISHED", "EDITION", id);
    }

    public void unpublish(UUID adminId, UUID id) {
        Edition e = load(id);
        e.unpublish();
        editions.save(e);
        audit.log(adminId, "EDITION_UNPUBLISHED", "EDITION", id);
    }

    /** Hard delete: frees database space (page images are large). Removes posts, likes, comments, images. */
    @Transactional
    public void delete(UUID adminId, UUID id) {
        Edition e = load(id);
        List<Post> ps = posts.findByEditionIdOrderByPositionAsc(id);
        List<UUID> postIds = ps.stream().map(Post::getId).toList();
        List<UUID> mediaIds = new ArrayList<>();
        ps.forEach(p -> { if (p.getImageMediaId() != null) mediaIds.add(p.getImageMediaId()); });
        List<UUID> pageIds = new ArrayList<>();
        pages.findByEditionIdOrderByPageNo(id).forEach(p -> {
            pageIds.add(p.getId());
            mediaIds.add(p.getSmallMediaId());
            mediaIds.add(p.getLargeMediaId());
            if (p.getThumbMediaId() != null) mediaIds.add(p.getThumbMediaId());
        });
        texts.deleteAllById(pageIds);
        if (!postIds.isEmpty()) {
            likes.deleteByPostIdIn(postIds);
            comments.deleteByPostIdIn(postIds);
            posts.deleteAllInBatch(ps);
        }
        pages.deleteByEditionId(id);
        media.deleteAllById(mediaIds);
        editions.delete(e);
        audit.log(adminId, "EDITION_DELETED", "EDITION", id);
    }

    // =============== Public ===============

    public PublicEditionView getLatest() {
        Edition e = editions.findFirstByStatusOrderByEditionDateDesc(EditionStatus.PUBLISHED)
                .orElseThrow(() -> DomainException.notFound("EDITION_NOT_FOUND", "No newspaper has been published yet"));
        return toPublicView(e);
    }

    public PublicEditionView getByDate(LocalDate date) {
        return toPublicView(findPublished(date));
    }

    public Edition findPublished(LocalDate date) {
        return editions.findByEditionDate(date).filter(e -> e.getStatus() == EditionStatus.PUBLISHED)
                .orElseThrow(() -> DomainException.notFound("EDITION_NOT_FOUND", "No newspaper for " + date));
    }

    public List<String> listPublishedDates(String month) {
        YearMonth ym = parseMonth(month);
        return editions.findByStatusAndEditionDateBetweenOrderByEditionDate(EditionStatus.PUBLISHED, ym.atDay(1), ym.atEndOfMonth())
                .stream().map(e -> e.getEditionDate().toString()).toList();
    }

    /** Recognized text of one page of a published edition. */
    public PageTextView pageText(LocalDate date, int pageNo) {
        Edition e = findPublished(date);
        EditionPage p = pages.findByEditionIdAndPageNo(e.getId(), pageNo)
                .orElseThrow(() -> DomainException.notFound("PAGE_NOT_FOUND", "Page not found"));
        return texts.findById(p.getId())
                .map(t -> new PageTextView("READY", parseBlocks(t.getBlocksJson())))
                .orElseGet(() -> new PageTextView(ocr.available() || props.ocr().external() ? "PENDING" : "UNAVAILABLE", List.of()));
    }

    private List<TextBlock> parseBlocks(String blocksJson) {
        try { return List.of(json.readValue(blocksJson, TextBlock[].class)); }
        catch (Exception ex) { throw new IllegalStateException("Corrupt page text", ex); }
    }

    private PublicEditionView toPublicView(Edition e) {
        List<PublicPostView> pv = posts.findByEditionIdAndStatusOrderByPositionAsc(e.getId(), PostStatus.PUBLISHED).stream()
                .map(p -> new PublicPostView(p.getId(), p.getTitle(), p.getBody(), mediaUrl(p.getImageMediaId()),
                        p.getLikeCount(), p.getCommentCount(), p.getPublishedAt()))
                .toList();
        String prev = editions.findFirstByStatusAndEditionDateLessThanOrderByEditionDateDesc(EditionStatus.PUBLISHED, e.getEditionDate())
                .map(x -> x.getEditionDate().toString()).orElse(null);
        String next = editions.findFirstByStatusAndEditionDateGreaterThanOrderByEditionDateAsc(EditionStatus.PUBLISHED, e.getEditionDate())
                .map(x -> x.getEditionDate().toString()).orElse(null);
        String date = e.getEditionDate().toString();
        return new PublicEditionView(e.getId(), date, e.getTitle(), props.baseUrl() + "/e/" + date, pageViews(e.getId()), pv, prev, next);
    }

    // =============== helpers ===============

    private List<PageView> pageViews(UUID editionId) {
        return pages.findByEditionIdOrderByPageNo(editionId).stream()
                .map(p -> new PageView(p.getPageNo(),
                        mediaUrl(p.getThumbMediaId() != null ? p.getThumbMediaId() : p.getSmallMediaId()),
                        mediaUrl(p.getSmallMediaId()), mediaUrl(p.getLargeMediaId()), p.getWidth(), p.getHeight()))
                .toList();
    }

    Edition load(UUID id) {
        return editions.findById(id).orElseThrow(() -> DomainException.notFound("EDITION_NOT_FOUND", "Edition not found"));
    }

    private static boolean startsWithMagic(byte[] b) {
        for (int i = 0; i < PDF_MAGIC.length; i++) if (b[i] != PDF_MAGIC[i]) return false;
        return true;
    }

    private static YearMonth parseMonth(String month) {
        try { return month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month); }
        catch (Exception e) { throw DomainException.bad("VALIDATION_FAILED", "month must look like 2026-10"); }
    }
}
