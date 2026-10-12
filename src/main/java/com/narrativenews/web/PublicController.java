package com.narrativenews.web;

import com.narrativenews.domain.Media;
import com.narrativenews.repo.MediaRepository;
import com.narrativenews.service.EditionService;
import com.narrativenews.service.EngagementService;
import com.narrativenews.web.Dtos.*;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Everything a reader can do without signing in. */
@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final EditionService editions;
    private final EngagementService engagement;
    private final MediaRepository media;

    public PublicController(EditionService editions, EngagementService engagement, MediaRepository media) {
        this.editions = editions; this.engagement = engagement; this.media = media;
    }

    @GetMapping("/editions/latest")
    public PublicEditionView latest() { return editions.getLatest(); }

    @GetMapping("/editions/{date}")
    public PublicEditionView byDate(@PathVariable String date) {
        try { return editions.getByDate(LocalDate.parse(date)); }
        catch (DateTimeParseException e) { throw DomainException.bad("VALIDATION_FAILED", "Date must look like 2026-10-12"); }
    }

    @GetMapping("/editions/{date}/pages/{pageNo}/text")
    public PageTextView pageText(@PathVariable String date, @PathVariable int pageNo) {
        try { return editions.pageText(LocalDate.parse(date), pageNo); }
        catch (DateTimeParseException e) { throw DomainException.bad("VALIDATION_FAILED", "Date must look like 2026-10-12"); }
    }

    @GetMapping("/editions")
    public List<String> dates(@RequestParam(required = false) String month) { return editions.listPublishedDates(month); }

    @GetMapping("/posts/{id}/comments")
    public CommentPage comments(@PathVariable UUID id, @RequestParam(required = false) String cursor) {
        return engagement.listComments(id, cursor);
    }

    /** Page and post images. Ids are random UUIDs and never change, so browsers may cache them forever. */
    @GetMapping("/media/{id}")
    public ResponseEntity<byte[]> media(@PathVariable UUID id) {
        Media m = media.findById(id).orElseThrow(() -> DomainException.notFound("MEDIA_NOT_FOUND", "Not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(m.getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(m.getData());
    }
}
