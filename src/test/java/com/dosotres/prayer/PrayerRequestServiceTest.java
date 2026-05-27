package com.dosotres.prayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.dosotres.group.Group;
import com.dosotres.group.GroupRepository;
import com.dosotres.prayer.dto.CreatePrayerRequest;
import com.dosotres.prayer.dto.PrayerRequestResponse;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PrayerRequestServiceTest {

    @Mock
    private PrayerRequestRepository prayerRequestRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private UserRepository userRepository;

    private PrayerRequestService service;

    @BeforeEach
    void setUp() {
        service = new PrayerRequestService(prayerRequestRepository, groupRepository, userRepository);
    }

    private Group makeGroup(Long id) {
        Group g = new Group();
        g.setId(id);
        return g;
    }

    private User makeUser(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        return u;
    }

    private PrayerRequest makePrayerRequest(Long id, Group group, User author, PrayerRequestStatus status) {
        PrayerRequest pr = new PrayerRequest();
        pr.setId(id);
        pr.setGroup(group);
        pr.setAuthor(author);
        pr.setTitle("Test prayer");
        pr.setStatus(status);
        pr.onCreate();
        return pr;
    }

    @Test
    void create_setsPendingStatus() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(prayerRequestRepository.save(any(PrayerRequest.class))).thenAnswer(inv -> {
            PrayerRequest pr = inv.getArgument(0);
            pr.setId(10L);
            pr.onCreate();
            return pr;
        });

        PrayerRequestResponse response = service.create(
                new CreatePrayerRequest("Salud para mamá", "Por favor orar"), 1L, 1L);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.title()).isEqualTo("Salud para mamá");
        assertThat(response.authorName()).isEqualTo("Luis");
        assertThat(response.answeredAt()).isNull();
    }

    @Test
    void listByGroup_withStatusFilter_usesFindByGroupIdAndStatus() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, user, PrayerRequestStatus.PENDING);

        Pageable pageable = PageRequest.of(0, 10);
        when(prayerRequestRepository.findByGroupIdAndStatus(eq(1L), eq(PrayerRequestStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pr), pageable, 1));

        Page<PrayerRequestResponse> result = service.listByGroup(1L, PrayerRequestStatus.PENDING, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void listByGroup_withoutStatus_usesFindByGroupId() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");
        PrayerRequest pr1 = makePrayerRequest(10L, group, user, PrayerRequestStatus.PENDING);
        PrayerRequest pr2 = makePrayerRequest(11L, group, user, PrayerRequestStatus.ANSWERED);

        Pageable pageable = PageRequest.of(0, 10);
        when(prayerRequestRepository.findByGroupId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pr1, pr2), pageable, 2));

        Page<PrayerRequestResponse> result = service.listByGroup(1L, null, pageable);

        assertThat(result.getContent()).hasSize(2);
    }
}
