package com.dosotres.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.admin.dto.AdminMetricsResponse;
import com.dosotres.admin.dto.AdminMetricsResponse.Active;
import com.dosotres.admin.dto.AdminMetricsResponse.DailyCount;
import com.dosotres.admin.dto.AdminMetricsResponse.Totals;
import com.dosotres.admin.dto.AdminMetricsResponse.Trends;
import com.dosotres.email.EmailService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminWeeklyReportServiceTest {

    @Mock
    private AdminMetricsService metricsService;

    @Mock
    private EmailService emailService;

    /** Serie diaria (oldest→today). Las primeras 7 son "semana anterior", las últimas 7 "esta semana". */
    private List<DailyCount> series(long... counts) {
        List<DailyCount> out = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 6, 1);
        for (long c : counts) {
            out.add(new DailyCount(d, c));
            d = d.plusDays(1);
        }
        return out;
    }

    private List<DailyCount> zeros14() {
        return series(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private AdminMetricsResponse sample() {
        // newUsers: semana anterior = 5, esta semana = 8 → variación +3.
        List<DailyCount> newUsers = series(1, 1, 1, 1, 1, 0, 0, 2, 2, 2, 1, 1, 0, 0);
        Totals totals = new Totals(
                42, 5, 3.2,
                10, 2, 7, 4,
                30, 1200, 6, 3, 8,
                9, 25, 2, 50, 12, 1, 4, 0, 1,
                21, 50.0, 30, 71.4, 20, 47.6);
        Active active = new Active(8, 18, 15, 33);
        Trends trends = new Trends(14, newUsers, zeros14(), zeros14(), zeros14(), zeros14(), zeros14());
        return new AdminMetricsResponse(totals, active, trends, List.of(), List.of(), List.of());
    }

    @Test
    void sendsReportWithWeekOverWeekVariation() {
        when(metricsService.computeMetrics(14)).thenReturn(sample());
        AdminWeeklyReportService service = new AdminWeeklyReportService(
                metricsService, emailService, "luis@test.com", "https://app.test");

        service.sendWeeklyReport();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtml(eq("luis@test.com"), subject.capture(), html.capture());

        // Asunto: total acumulado + altas de la semana (8).
        assertThat(subject.getValue()).contains("42 usuarios").contains("+8 esta semana");

        // Cuerpo: fila de variación con delta +3 y enlace al panel.
        assertThat(html.getValue())
                .contains("Usuarios nuevos")
                .contains("+3")
                .contains("Totales acumulados")
                .contains("https://app.test/admin");
    }
}
