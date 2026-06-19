package com.dosotres.publicwall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.publicwall.dto.CreatePublicRequestRequest;
import com.dosotres.publicwall.dto.PublicRequestResponse;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private PublicWallService service;

    @BeforeEach
    void setUp() {
        service = new PublicWallService(requestRepository, prayerRepository, userRepository);
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
        when(requestRepository.findByModerationStatusOrderByCreatedAtDesc(
                eq(ModerationStatus.VISIBLE), any(PageRequest.class))).thenReturn(page);
        when(prayerRepository.findPrayedRequestIds(1L, List.of(10L))).thenReturn(List.of(10L));

        Page<PublicRequestResponse> res = service.feed(1L, PageRequest.of(0, 20));

        assertThat(res.getContent()).hasSize(1);
        assertThat(res.getContent().get(0).iPrayed()).isTrue();
        assertThat(res.getContent().get(0).mine()).isFalse();
    }

    @Test
    void pray_firstTime_incrementsAndSaves() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeUser(1L, "Luis", "AR")));

        PublicRequestResponse res = service.pray(1L, 10L);

        verify(prayerRepository).save(any(PublicPrayer.class));
        assertThat(r.getPrayCount()).isEqualTo(1);
        assertThat(res.prayCount()).isEqualTo(1);
        assertThat(res.iPrayed()).isTrue();
    }

    @Test
    void pray_idempotent_secondTime_noSave() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest r = makeRequest(10L, author, false, ModerationStatus.VISIBLE);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(prayerRepository.existsByRequestIdAndUserId(10L, 1L)).thenReturn(true);

        PublicRequestResponse res = service.pray(1L, 10L);

        verify(prayerRepository, never()).save(any(PublicPrayer.class));
        assertThat(r.getPrayCount()).isZero();
        assertThat(res.iPrayed()).isTrue();
    }

    @Test
    void pray_hiddenRequest_throwsNotFound() {
        User author = makeUser(2L, "Ana", "UY");
        PublicPrayerRequest hidden = makeRequest(10L, author, false, ModerationStatus.HIDDEN);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.pray(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(prayerRepository, never()).save(any(PublicPrayer.class));
    }
}
