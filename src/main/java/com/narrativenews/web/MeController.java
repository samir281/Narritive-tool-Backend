package com.narrativenews.web;

import com.narrativenews.security.Principal;
import com.narrativenews.service.EngagementService;
import com.narrativenews.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Signed-in reader actions: likes and comments. */
@RestController
@RequestMapping("/api")
public class MeController {
    private final EngagementService engagement;
    public MeController(EngagementService engagement) { this.engagement = engagement; }

    @GetMapping("/me/likes")
    public Map<String, List<UUID>> myLikes(@AuthenticationPrincipal Principal me, @RequestParam UUID editionId) {
        return Map.of("postIds", engagement.myLikes(me.id(), editionId));
    }

    @PutMapping("/me/likes/{postId}")
    public LikeResult like(@AuthenticationPrincipal Principal me, @PathVariable UUID postId) {
        return engagement.like(me.id(), postId);
    }

    @DeleteMapping("/me/likes/{postId}")
    public LikeResult unlike(@AuthenticationPrincipal Principal me, @PathVariable UUID postId) {
        return engagement.unlike(me.id(), postId);
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView comment(@AuthenticationPrincipal Principal me, @PathVariable UUID postId,
                               @Valid @RequestBody CommentRequest r) {
        return engagement.addComment(me.id(), postId, r.body());
    }

    @DeleteMapping("/me/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Principal me, @PathVariable UUID id) {
        engagement.deleteOwn(me.id(), id);
    }
}
