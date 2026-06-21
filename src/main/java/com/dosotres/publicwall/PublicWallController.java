package com.dosotres.publicwall;

import com.dosotres.publicwall.dto.CreatePublicRequestRequest;
import com.dosotres.publicwall.dto.MarkAnsweredRequest;
import com.dosotres.publicwall.dto.PublicRequestResponse;
import com.dosotres.publicwall.dto.UpdateVisibilityRequest;
import com.dosotres.security.annotations.AuthUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/requests")
public class PublicWallController {

    private final PublicWallService publicWallService;

    public PublicWallController(PublicWallService publicWallService) {
        this.publicWallService = publicWallService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicRequestResponse create(@AuthUser Long userId,
                                        @Valid @RequestBody CreatePublicRequestRequest req) {
        return publicWallService.create(userId, req);
    }

    @GetMapping
    public Page<PublicRequestResponse> feed(@AuthUser Long userId,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return publicWallService.feed(userId, pageable);
    }

    /** Testimonios públicos permanentes (respondidos con testimonio). */
    @GetMapping("/testimonies")
    public Page<PublicRequestResponse> testimonies(@AuthUser Long userId,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return publicWallService.testimonies(userId, pageable);
    }

    @PostMapping("/{id}/pray")
    public PublicRequestResponse pray(@AuthUser Long userId, @PathVariable Long id) {
        return publicWallService.pray(userId, id);
    }

    /** El autor marca su pedido como respondido, con testimonio opcional. */
    @PatchMapping("/{id}/answered")
    public PublicRequestResponse markAnswered(@AuthUser Long userId,
                                              @PathVariable Long id,
                                              @Valid @RequestBody MarkAnsweredRequest req) {
        return publicWallService.markAnswered(userId, id, req.testimony());
    }

    /** Moderador global: ocultar/restaurar un pedido del muro. */
    @PatchMapping("/{id}/visibility")
    public PublicRequestResponse setVisibility(@AuthUser Long userId,
                                               @PathVariable Long id,
                                               @Valid @RequestBody UpdateVisibilityRequest req) {
        return publicWallService.setVisibility(userId, id, req.moderationStatus());
    }

    /** Borrar un pedido público: autor o moderador global. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthUser Long userId, @PathVariable Long id) {
        publicWallService.delete(userId, id);
    }
}
