package com.dosotres.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dosotres.admin.dto.AdminMetricsResponse;
import com.dosotres.admin.dto.AdminUserResponse;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.user.GlobalRole;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class AdminMetricsServiceIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-22T12:00:00Z");

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(FIXED_NOW, ZoneId.of("America/Argentina/Buenos_Aires"));
        }
    }

    @Autowired
    private AdminMetricsService metricsService;

    @Autowired
    private AdminUserService userService;

    @Autowired
    private UserRepository userRepository;

    private Long adminId;
    private Long plainUserId;

    @BeforeEach
    void setUp() {
        adminId = persistUser(GlobalRole.ADMIN);
        plainUserId = persistUser(GlobalRole.USER);
    }

    private Long persistUser(GlobalRole role) {
        User u = new User();
        u.setEmail("admin-it-" + System.nanoTime() + "@test.com");
        u.setDisplayName("Admin IT User");
        u.setPasswordHash("hashed");
        u.setGlobalRole(role);
        return userRepository.save(u).getId();
    }

    @Test
    void getMetrics_runsEveryAggregationAndFillsTrends() {
        AdminMetricsResponse m = metricsService.getMetrics(adminId, 30);

        // Totales: al menos los dos usuarios sembrados.
        assertThat(m.totals().users()).isGreaterThanOrEqualTo(2);
        assertThat(m.totals().pushPercent()).isBetween(0.0, 100.0);
        assertThat(m.totals().profilePercent()).isBetween(0.0, 100.0);
        assertThat(m.totals().agePercent()).isBetween(0.0, 100.0);

        // Tendencias: ventana exacta y huecos rellenados.
        assertThat(m.trends().days()).isEqualTo(30);
        assertThat(m.trends().newUsers()).hasSize(30);
        assertThat(m.trends().newGroups()).hasSize(30);
        assertThat(m.trends().newRequests()).hasSize(30);
        assertThat(m.trends().sessions()).hasSize(30);
        assertThat(m.trends().wallActivity()).hasSize(30);
        assertThat(m.trends().messages()).hasSize(30);
        // Los dos usuarios nuevos caen en el último día de la ventana.
        long totalNew = m.trends().newUsers().stream().mapToLong(d -> d.count()).sum();
        assertThat(totalNew).isGreaterThanOrEqualTo(2);

        // Demografía: siempre hay buckets de edad (al menos 'unknown').
        assertThat(m.byAge()).isNotEmpty();
        assertThat(m.byCountry()).isNotNull();
        assertThat(m.byProvince()).isNotNull();
    }

    @Test
    void getMetrics_clampsDaysWindow() {
        assertThat(metricsService.getMetrics(adminId, 1).trends().days()).isEqualTo(7);
        assertThat(metricsService.getMetrics(adminId, 9999).trends().days()).isEqualTo(365);
    }

    @Test
    void getMetrics_forbiddenForNonAdmin() {
        assertThatThrownBy(() -> metricsService.getMetrics(plainUserId, 30))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listUsers_returnsPageForAdmin() {
        Page<AdminUserResponse> page = userService.listUsers(adminId, null, PageRequest.of(0, 25));
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(page.getContent()).allSatisfy(u -> assertThat(u.email()).isNotBlank());
    }

    @Test
    void listUsers_forbiddenForNonAdmin() {
        assertThatThrownBy(() -> userService.listUsers(plainUserId, null, PageRequest.of(0, 25)))
                .isInstanceOf(ForbiddenException.class);
    }
}
