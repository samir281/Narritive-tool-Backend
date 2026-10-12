package com.narrativenews.web;

import com.narrativenews.domain.Edition;
import com.narrativenews.domain.Enums.PostStatus;
import com.narrativenews.security.Principal;
import com.narrativenews.service.AdminManagementService;
import com.narrativenews.service.EditionService;
import com.narrativenews.service.EngagementService;
import com.narrativenews.service.PostService;
import com.narrativenews.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

/** Everything under /api/admin/** (requires an admin token; see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final EditionService editions;
    private final PostService posts;
    private final EngagementService engagement;
    private final AdminManagementService admins;

    public AdminController(EditionService editions, PostService posts, EngagementService engagement, AdminManagementService admins) {
        this.editions = editions; this.posts = posts; this.engagement = engagement; this.admins = admins;
    }

    // ---- editions ----
    @GetMapping("/editions")
    public List<EditionSummary> list(@RequestParam(required = false) String month) { return editions.listForAdmin(month); }

    @PostMapping("/editions")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminEditionView create(@AuthenticationPrincipal Principal me, @Valid @RequestBody CreateEditionRequest r) {
        Edition e = editions.create(me.id(), r.editionDate(), r.title());
        return editions.getForAdmin(e.getId());
    }

    @GetMapping("/editions/{id}")
    public AdminEditionView get(@PathVariable UUID id) { return editions.getForAdmin(id); }

    @PatchMapping("/editions/{id}")
    public AdminEditionView update(@AuthenticationPrincipal Principal me, @PathVariable UUID id,
                                   @Valid @RequestBody UpdateEditionRequest r) {
        editions.updateTitle(me.id(), id, r.title());
        return editions.getForAdmin(id);
    }

    @PostMapping(value = "/editions/{id}/pdf", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AdminEditionView uploadPdf(@AuthenticationPrincipal Principal me, @PathVariable UUID id,
                                      @RequestPart("file") MultipartFile file) {
        editions.uploadPdf(me.id(), id, file);
        return editions.getForAdmin(id);
    }

    @PostMapping("/editions/{id}/publish")
    public AdminEditionView publish(@AuthenticationPrincipal Principal me, @PathVariable UUID id) {
        editions.publish(me.id(), id);
        return editions.getForAdmin(id);
    }

    @PostMapping("/editions/{id}/unpublish")
    public AdminEditionView unpublish(@AuthenticationPrincipal Principal me, @PathVariable UUID id) {
        editions.unpublish(me.id(), id);
        return editions.getForAdmin(id);
    }

    @DeleteMapping("/editions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Principal me, @PathVariable UUID id) { editions.delete(me.id(), id); }

    // ---- posts ----
    @PostMapping("/editions/{id}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminPostView createPost(@AuthenticationPrincipal Principal me, @PathVariable UUID id,
                                    @Valid @RequestBody CreatePostRequest r) {
        return posts.create(me.id(), id, r.title(), r.body());
    }

    @PutMapping("/editions/{id}/posts/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@AuthenticationPrincipal Principal me, @PathVariable UUID id, @Valid @RequestBody ReorderRequest r) {
        posts.reorder(me.id(), id, r.postIds());
    }

    @PatchMapping("/posts/{id}")
    public AdminPostView updatePost(@AuthenticationPrincipal Principal me, @PathVariable UUID id,
                                    @Valid @RequestBody UpdatePostRequest r) {
        return posts.update(me.id(), id, r.title(), r.body());
    }

    @PostMapping("/posts/{id}/publish")
    public AdminPostView publishPost(@AuthenticationPrincipal Principal me, @PathVariable UUID id) {
        return posts.setStatus(me.id(), id, PostStatus.PUBLISHED);
    }

    @PostMapping("/posts/{id}/unpublish")
    public AdminPostView unpublishPost(@AuthenticationPrincipal Principal me, @PathVariable UUID id) {
        return posts.setStatus(me.id(), id, PostStatus.DRAFT);
    }

    @PostMapping(value = "/posts/{id}/image", consumes = "multipart/form-data")
    public AdminPostView setImage(@AuthenticationPrincipal Principal me, @PathVariable UUID id,
                                  @RequestPart("file") MultipartFile file) {
        return posts.setImage(me.id(), id, file);
    }

    @DeleteMapping("/posts/{id}/image")
    public AdminPostView removeImage(@AuthenticationPrincipal Principal me, @PathVariable UUID id) {
        return posts.removeImage(me.id(), id);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@AuthenticationPrincipal Principal me, @PathVariable UUID id) { posts.delete(me.id(), id); }

    // ---- moderation ----
    @GetMapping("/posts/{id}/comments")
    public List<CommentView> comments(@PathVariable UUID id) { return engagement.listForModeration(id); }

    @PostMapping("/comments/{id}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@AuthenticationPrincipal Principal me, @PathVariable UUID id) { engagement.hide(me.id(), id); }

    @PostMapping("/comments/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@AuthenticationPrincipal Principal me, @PathVariable UUID id) { engagement.restore(me.id(), id); }

    // ---- admins ----
    @GetMapping("/admins")
    public List<AdminUserView> listAdmins() { return admins.list(); }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserView createAdmin(@AuthenticationPrincipal Principal me, @Valid @RequestBody CreateAdminRequest r) {
        return admins.create(me.id(), r.name(), r.email(), r.password());
    }

    @DeleteMapping("/admins/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableAdmin(@AuthenticationPrincipal Principal me, @PathVariable UUID id) { admins.disable(me.id(), id); }
}
