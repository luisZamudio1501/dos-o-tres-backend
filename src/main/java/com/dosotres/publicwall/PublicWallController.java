package com.dosotres.publicwall;

import com.dosotres.messaging.dto.ConversationSummaryResponse;
import com.dosotres.publicwall.dto.CreatePublicRequestRequest;
import com.dosotres.publicwall.dto.MarkAnsweredRequest;
import com.dosotres.publicwall.dto.PrayRequest;
import com.dosotres.publicwall.dto.PrayerEntryResponse;
import com.dosotres.publicwall.dto.PublicRequestResponse;
import com.dosotres.publicwall.dto.UpdateVisibilityRequest;
import com.dosotres.security.annotations.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
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
    public PublicRequestResponse pray(@AuthUser Long userId, @PathVariable Long id,
                                      @RequestBody(required = false) PrayRequest req) {
        boolean visible = req != null && req.visible();
        return publicWallService.pray(userId, id, visible);
    }

    /** Solicitud de vínculo del orante hacia el autor (Fase 5). */
    @PostMapping("/{id}/link")
    public ConversationSummaryResponse requestLink(@AuthUser Long userId, @PathVariable Long id) {
        return publicWallService.requestLink(userId, id);
    }

    /** El autor ve quiénes oraron por su pedido (visibles con nombre, anónimos como "Anónimo"). */
    @GetMapping("/{id}/prayers")
    public List<PrayerEntryResponse> listPrayers(@AuthUser Long userId, @PathVariable Long id) {
        return publicWallService.listPrayers(userId, id);
    }

    /** Agradecimiento general del autor a todos los que oraron (un sentido, sin abrir chat). */
    @PostMapping("/{id}/thanks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendThanks(@AuthUser Long userId, @PathVariable Long id) {
        publicWallService.sendThanks(userId, id);
    }

    /** El autor inicia él mismo un vínculo con un orante visible. */
    @PostMapping("/{id}/link/{prayerUserId}")
    public ConversationSummaryResponse requestLinkFromAuthor(@AuthUser Long userId, @PathVariable Long id,
                                                              @PathVariable Long prayerUserId) {
        return publicWallService.requestLinkFromAuthor(userId, id, prayerUserId);
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
