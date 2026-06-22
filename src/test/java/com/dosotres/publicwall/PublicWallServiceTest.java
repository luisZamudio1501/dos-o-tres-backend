package com.dosotres.publicwall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.messaging.MessagingService;
import com.dosotres.moderation.ModerationAccess;
import com.dosotres.publicwall.dto.CreatePublicRequestRequest;
import com.dosotres.publicwall.dto.PrayerEntryResponse;
import com.dosotres.publicwall.dto.PublicRequestResponse;
import com.dosotres.push.PushNotificationService;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PublicWallServiceTest {

    @Mock
    private PublicPrayerRequestRepository requestRepository;
    @Mock
    private PublicPrayerRepository prayerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ModerationAccess moderationAccess;
    @Mock
    private MessagingService messagingService;
    @Mock
    private PushNotificationService pushService;

    private final Instant now = Instant.parse("2026-06-21T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(now, ZoneId.of("UTC"));

    private PublicWallService service;

    @BeforeEach
    void setUp() {
        service = new PublicWallService(
                requestRepository, prayerRepository, userRepository, moderationAccess,
                messagingService, pushService, new ObjectMapper(), fixedClock);
    }

    private User makeUser(Long id, String name, String country) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        u.setCountry(country);
        return u;
    }

    private PublicPrayerRequest makeRequest(Long id, User author, boolean anonymous, ModerationStatus mod) {
        PublicPrayerRequest r = new PublicPrayerRequest();
        r.setId(id);
        r.setAuthor(author);
        r.setTitle("Por mi familia");
        r.setAnonymous(anonymous);
        r.setStatus(PublicRequestStatus.ACTIVE);
        r.setModerationStatus(mod);
        r.setPrayCount(0);
        return r;
    }

    @Test
    void create_savesAndReturnsMineNotAnonymous() {
        User author = makeUser(1L, "Luis", "AR");
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(requestRepository.save(any(PublicPrayerRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        PublicRequestResponse res = service.create(1L,
                new CreatePublicRequestRequest("Por mi familia", "Texto", false));

        assertThat(res.mine()).isTrue();
        assertThat(res.anonymous()).isFalse();
        assertThat(res.authorId()).isEqualTo(1L);
        assertThat(res.authorName()).isEqualTo("Luis");
        assertThat(res.prayCount()).isZero();
        assertThat(res.iPrayed()).isFalse();
        assertThat(res.archived()).isFalse();
    }

    @Test
    void create_anonymous_masksAuthor() {
        User author = makeUser(1L, "Luis", "AR");
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(requestRepository.save(any(PublicPrayerRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        PublicRequestResponse res = service.create(1L,
                new CreatePublicRequestRequest("Por mi familia", null, true));

        assertThat(res.anonymous()).isTrue();
        assertThat(res.authorId()).isNull();
        assertThat(res.authorName()).isNull();
        assertThat(res.authorCountry()).isNull();
        assertThat(res.mine()).isTrue();
    }

    @Test
    void feed_marksIPrayedForPrayedRequests() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        Page<PublicPrayerRequest> page = new PageImpl<>(List.of(r));
        when(requestRepository.findByModerationStatusAndStatusAndArchivedAtIsNullOrderByCreatedAtDesc(
                eq(ModerationStatus.VISIBLE), eq(PublicRequestStatus.ACTIVE), any(PageRequest.class)))
                .thenReturn(page);
        when(prayerRepository.findPrayedRequestIds(1L, List.of(10L))).thenReturn(List.of(10L));

        Page<PublicRequestResponse> res = service.feed(1L, PageRequest.of(0, 20));

        assertThat(res.getContent()).hasSize(1);
        assertThat(res.getContent().get(0).iPrayed()).isTrue();
        assertThat(res.getContent().get(0).mine()).isFalse();
    }

    @Test
    void testimonies_returnsAnsweredWithTestimony() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        r.setStatus(PublicRequestStatus.ANSWERED);
        r.setTestimony("Dios respondió");
        Page<PublicPrayerRequest> page = new PageImpl<>(List.of(r));
        when(requestRepository.findTestimonies(eq(ModerationStatus.VISIBLE), any(PageRequest.class)))
                .thenReturn(page);
        when(prayerRepository.findPrayedRequestIds(1L, List.of(10L))).thenReturn(List.of());

        Page<PublicRequestResponse> res = service.testimonies(1L, PageRequest.of(0, 20));

        assertThat(res.getContent()).hasSize(1);
        assertThat(res.getContent().get(0).testimony()).isEqualTo("Dios respondió");
        assertThat(res.getContent().get(0).status()).isEqualTo("ANSWERED");
    }

    @Test
    void pray_firstTime_incrementsAndUpdatesActivity() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeUser(1L, "Luis", "AR")));

        PublicRequestResponse res = service.pray(1L, 10L, false);

        ArgumentCaptor<PublicPrayer> captor = ArgumentCaptor.forClass(PublicPrayer.class);
        verify(prayerRepository).save(captor.capture());
        assertThat(captor.getValue().isVisible()).isFalse();
        assertThat(r.getPrayCount()).isEqualTo(1);
        assertThat(r.getLastActivityAt()).isEqualTo(now);
        assertThat(res.prayCount()).isEqualTo(1);
        assertThat(res.iPrayed()).isTrue();
    }

    @Test
    void pray_visible_savesVisibleTrue() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeUser(1L, "Luis", "AR")));

        service.pray(1L, 10L, true);

        ArgumentCaptor<PublicPrayer> captor = ArgumentCaptor.forClass(PublicPrayer.class);
        verify(prayerRepository).save(captor.capture());
        assertThat(captor.getValue().isVisible()).isTrue();
    }

    @Test
    void pray_idempotent_secondTime_noSave() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 1L)).thenReturn(true);

        PublicRequestResponse res = service.pray(1L, 10L, false);

        verify(prayerRepository, never()).save(any(PublicPrayer.class));
        assertThat(r.getPrayCount()).isZero();
        assertThat(res.iPrayed()).isTrue();
    }

    @Test
    void pray_hiddenRequest_throwsNotFound() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest hidden = makeRequest(10L, author, false, ModerationStatus.HIDDEN);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.pray(1L, 10L, false))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(prayerRepository, never()).save(any(PublicPrayer.class));
    }

    @Test
    void requestLink_happyPath_delegatesToMessaging() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 1L)).thenReturn(true);

        service.requestLink(1L, 10L);

        verify(messagingService).startLinkRequest(1L, 2L, r);
    }

    @Test
    void requestLink_notPrayed_throwsForbidden() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.requestLink(1L, 10L))
                .isInstanceOf(ForbiddenException.class);
        verify(messagingService, never()).startLinkRequest(any(), any(), any());
    }

    @Test
    void requestLink_ownRequest_throwsValidation() {
        User author = makeUser(1L, "Luis", "AR");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.requestLink(1L, 10L))
                .isInstanceOf(ValidationException.class);
        verify(messagingService, never()).startLinkRequest(any(), any(), any());
    }

    private PublicPrayer makePrayer(User user, boolean visible) {
        PublicPrayer p = new PublicPrayer();
        p.setUser(user);
        p.setVisible(visible);
        p.setPrayedAt(now);
        return p;
    }

    @Test
    void listPrayers_byAuthor_masksAnonymous() {
        User author = makeUser(1L, "Luis", "AR");
        User visible = makeUser(2L, "Ana", "UY");
        User anon = makeUser(3L, "Tomás", "AR");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.findByRequestIdOrderByPrayedAtDesc(10L))
                .thenReturn(List.of(makePrayer(visible, true), makePrayer(anon, false)));

        List<PrayerEntryResponse> res = service.listPrayers(1L, 10L);

        assertThat(res).hasSize(2);
        assertThat(res.get(0).userId()).isEqualTo(2L);
        assertThat(res.get(0).displayName()).isEqualTo("Ana");
        assertThat(res.get(1).userId()).isNull();
        assertThat(res.get(1).displayName()).isEqualTo("Anónimo");
    }

    @Test
    void listPrayers_byNonAuthor_throwsForbidden() {
        User author = makeUser(1L, "Luis", "AR");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.listPrayers(99L, 10L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendThanks_byAuthor_pushesToAllPrayers() {
        User author = makeUser(1L, "Luis", "AR");
        User visible = makeUser(2L, "Ana", "UY");
        User anon = makeUser(3L, "Tomás", "AR");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.findByRequestIdOrderByPrayedAtDesc(10L))
                .thenReturn(List.of(makePrayer(visible, true), makePrayer(anon, false)));

        service.sendThanks(1L, 10L);

        verify(pushService).sendToUsers(eq(List.of(2L, 3L)), any());
    }

    @Test
    void sendThanks_byNonAuthor_throwsForbidden() {
        User author = makeUser(1L, "Luis", "AR");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.sendThanks(99L, 10L))
                .isInstanceOf(ForbiddenException.class);
        verify(pushService, never()).sendToUsers(any(), any());
    }

    private PublicPrayer prayerWith(Long id, User user, boolean visible, PublicPrayerRequest request) {
        PublicPrayer p = makePrayer(user, visible);
        p.setId(id);
        p.setRequest(request);
        return p;
    }

    @Test
    void requestLinkFromAuthor_resolvesPrayerAndDelegates() {
        User author = makeUser(1L, "Luis", "AR");
        User prayerUser = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.findById(50L)).thenReturn(Optional.of(prayerWith(50L, prayerUser, true, r)));

        service.requestLinkFromAuthor(1L, 10L, 50L);

        verify(messagingService).startLinkRequest(1L, 2L, r);
    }

    @Test
    void requestLinkFromAuthor_anonymousPrayer_alsoDelegates() {
        User author = makeUser(1L, "Luis", "AR");
        User prayerUser = makeUser(3L, "Tomás", "AR");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.findById(51L)).thenReturn(Optional.of(prayerWith(51L, prayerUser, false, r)));

        service.requestLinkFromAuthor(1L, 10L, 51L);

        verify(messagingService).startLinkRequest(1L, 3L, r);
    }

    @Test
    void requestLinkFromAuthor_prayerNotForThisRequest_throwsNotFound() {
        User author = makeUser(1L, "Luis", "AR");
        User prayerUser = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        PublicPrayerRequest other = makeRequest(99L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.findById(50L)).thenReturn(Optional.of(prayerWith(50L, prayerUser, true, other)));

        assertThatThrownBy(() -> service.requestLinkFromAuthor(1L, 10L, 50L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(messagingService, never()).startLinkRequest(any(), any(), any());
    }

    @Test
    void requestLinkFromAuthor_byNonAuthor_throwsForbidden() {
        User author = makeUser(1L, "Luis", "AR");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.requestLinkFromAuthor(99L, 10L, 50L))
                .isInstanceOf(ForbiddenException.class);
        verify(messagingService, never()).startLinkRequest(any(), any(), any());
    }

    @Test
    void markAnswered_byAuthor_setsAnsweredAndTestimony() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 2L)).thenReturn(false);

        PublicRequestResponse res = service.markAnswered(2L, 10L, "  Dios respondió  ");

        assertThat(r.getStatus()).isEqualTo(PublicRequestStatus.ANSWERED);
        assertThat(r.getAnsweredAt()).isEqualTo(now);
        assertThat(r.getTestimony()).isEqualTo("Dios respondió");
        assertThat(res.status()).isEqualTo("ANSWERED");
        assertThat(res.testimony()).isEqualTo("Dios respondió");
    }

    @Test
    void markAnswered_anonymousAuthor_allowed() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, true, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 2L)).thenReturn(false);

        PublicRequestResponse res = service.markAnswered(2L, 10L, "Gracias");

        assertThat(r.getStatus()).isEqualTo(PublicRequestStatus.ANSWERED);
        assertThat(res.anonymous()).isTrue();
        assertThat(res.authorId()).isNull();
        assertThat(res.mine()).isTrue();
        assertThat(res.testimony()).isEqualTo("Gracias");
    }

    @Test
    void markAnswered_noTestimony_ok() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 2L)).thenReturn(false);

        PublicRequestResponse res = service.markAnswered(2L, 10L, "   ");

        assertThat(r.getStatus()).isEqualTo(PublicRequestStatus.ANSWERED);
        assertThat(r.getTestimony()).isNull();
        assertThat(res.testimony()).isNull();
    }

    @Test
    void markAnswered_byNonAuthor_throwsForbidden() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.markAnswered(99L, 10L, "x"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(r.getStatus()).isEqualTo(PublicRequestStatus.ACTIVE);
    }

    @Test
    void markAnswered_alreadyAnswered_throwsConflict() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        r.setStatus(PublicRequestStatus.ANSWERED);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.markAnswered(2L, 10L, "x"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void archiveStale_callsRepoWithThreshold() {
        when(requestRepository.archiveStaleActive(eq(now), eq(now.minus(PublicWallService.STALE_THRESHOLD))))
                .thenReturn(3);

        int archived = service.archiveStale();

        assertThat(archived).isEqualTo(3);
        verify(requestRepository).archiveStaleActive(now, now.minus(PublicWallService.STALE_THRESHOLD));
    }

    @Test
    void setVisibility_byModerator_hidesRequest() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 5L)).thenReturn(false);

        service.setVisibility(5L, 10L, ModerationStatus.HIDDEN);

        assertThat(r.getModerationStatus()).isEqualTo(ModerationStatus.HIDDEN);
    }

    @Test
    void setVisibility_byNonModerator_throwsForbidden() {
        when(moderationAccess.requireModerator(3L)).thenThrow(new ForbiddenException("no mod"));

        assertThatThrownBy(() -> service.setVisibility(3L, 10L, ModerationStatus.HIDDEN))
                .isInstanceOf(ForbiddenException.class);
        verify(requestRepository, never()).findById(any());
    }

    @Test
    void delete_byAuthor_removes() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));

        service.delete(2L, 10L);

        verify(prayerRepository).deleteByRequestId(10L);
        verify(requestRepository).delete(r);
    }

    @Test
    void delete_byModerator_removes() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(moderationAccess.isModerator(5L)).thenReturn(true);

        service.delete(5L, 10L);

        verify(prayerRepository).deleteByRequestId(10L);
        verify(requestRepository).delete(r);
    }

    @Test
    void delete_byOtherNonModerator_throwsForbidden() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(moderationAccess.isModerator(3L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(3L, 10L))
                .isInstanceOf(ForbiddenException.class);
        verify(requestRepository, never()).delete(any(PublicPrayerRequest.class));
    }
}
