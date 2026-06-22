package com.dosotres.admin;

import com.dosotres.admin.dto.AdminMetricsResponse;
import com.dosotres.admin.dto.AdminMetricsResponse.DailyCount;
import com.dosotres.email.EmailService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Arma el resumen semanal de administración (snapshot + variación semana a
 * semana) y lo envía por email (Plan Panel de Administración, Fase 2). Reusa
 * {@link AdminMetricsService} y {@link EmailService}; sin estado propio.
 */
@Service
public class AdminWeeklyReportService {

    /** 7 días "esta semana" + 7 "semana anterior" para la variación. */
    private static final int WINDOW_DAYS = 14;

    private static final Logger log = LoggerFactory.getLogger(AdminWeeklyReportService.class);

    private final AdminMetricsService metricsService;
    private final EmailService emailService;
    private final String recipient;
    private final String baseUrl;

    public AdminWeeklyReportService(AdminMetricsService metricsService,
                                    EmailService emailService,
                                    @Value("${app.admin.weekly-report.recipient}") String recipient,
                                    @Value("${app.mail.base-url}") String baseUrl) {
        this.metricsService = metricsService;
        this.emailService = emailService;
        this.recipient = recipient;
        this.baseUrl = baseUrl;
    }

    public void sendWeeklyReport() {
        AdminMetricsResponse m = metricsService.computeMetrics(WINDOW_DAYS);
        var t = m.totals();

        long usersThisWeek = thisWeek(m.trends().newUsers());
        String subject = "Mateo1819 — Resumen semanal (%d usuarios, +%d esta semana)"
                .formatted(t.users(), usersThisWeek);

        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#1f2937;max-width:560px">
                  <h2 style="color:#0C6B6B;margin-bottom:4px">Resumen semanal</h2>
                  <p style="color:#6b7280;margin-top:0">Comunidad Mateo1819 · últimos 7 días vs. semana anterior</p>

                  <h3 style="margin-bottom:6px">Esta semana</h3>
                  <table style="border-collapse:collapse;width:100%%;font-size:14px">
                    <tr style="color:#6b7280;text-align:left">
                      <th style="padding:6px 8px;border-bottom:1px solid #e5e7eb">Métrica</th>
                      <th style="padding:6px 8px;border-bottom:1px solid #e5e7eb;text-align:right">Esta semana</th>
                      <th style="padding:6px 8px;border-bottom:1px solid #e5e7eb;text-align:right">Anterior</th>
                      <th style="padding:6px 8px;border-bottom:1px solid #e5e7eb;text-align:right">Var.</th>
                    </tr>
                    %s
                  </table>

                  <h3 style="margin-bottom:6px;margin-top:20px">Totales acumulados</h3>
                  <table style="border-collapse:collapse;width:100%%;font-size:14px">
                    %s
                  </table>

                  <h3 style="margin-bottom:6px;margin-top:20px">Actividad</h3>
                  <table style="border-collapse:collapse;width:100%%;font-size:14px">
                    %s
                  </table>

                  <p style="margin-top:24px">
                    <a href="%s/admin" style="background:#0C6B6B;color:#fff;padding:10px 20px;border-radius:8px;text-decoration:none;font-weight:bold">
                      Ver panel completo</a>
                  </p>
                </div>
                """.formatted(
                    weeklyRows(m),
                    totalsRows(t),
                    activityRows(m),
                    baseUrl);

        emailService.sendHtml(recipient, subject, html);
        log.info("Weekly admin report queued to {}", recipient);
    }

    // ── Composición de filas ─────────────────────────────────────────────────

    private String weeklyRows(AdminMetricsResponse m) {
        var tr = m.trends();
        return weeklyRow("Usuarios nuevos", tr.newUsers())
                + weeklyRow("Grupos nuevos", tr.newGroups())
                + weeklyRow("Pedidos nuevos", tr.newRequests())
                + weeklyRow("Sesiones de oración", tr.sessions())
                + weeklyRow("Actividad del muro", tr.wallActivity())
                + weeklyRow("Mensajes", tr.messages());
    }

    private String weeklyRow(String label, List<DailyCount> series) {
        long now = thisWeek(series);
        long prev = lastWeek(series);
        long delta = now - prev;
        String color = delta > 0 ? "#15803d" : delta < 0 ? "#b91c1c" : "#6b7280";
        String sign = delta > 0 ? "▲ +" + delta : delta < 0 ? "▼ " + delta : "–";
        return """
                <tr>
                  <td style="padding:6px 8px;border-bottom:1px solid #f3f4f6">%s</td>
                  <td style="padding:6px 8px;border-bottom:1px solid #f3f4f6;text-align:right;font-weight:bold">%d</td>
                  <td style="padding:6px 8px;border-bottom:1px solid #f3f4f6;text-align:right;color:#6b7280">%d</td>
                  <td style="padding:6px 8px;border-bottom:1px solid #f3f4f6;text-align:right;color:%s">%s</td>
                </tr>
                """.formatted(label, now, prev, color, sign);
    }

    private String totalsRows(AdminMetricsResponse.Totals t) {
        return kvRow("Usuarios", t.users())
                + kvRow("Grupos", t.groups())
                + kvRow("Pedidos activos", t.requestsActive())
                + kvRow("Pedidos respondidos", t.requestsAnswered())
                + kvRow("Pedidos en el muro", t.publicRequests())
                + kvRow("Mensajes", t.messages())
                + kvRow("Reportes abiertos", t.reportsOpen());
    }

    private String activityRows(AdminMetricsResponse m) {
        var a = m.active();
        return kvRow("Oraron (7 días)", a.prayed7d())
                + kvRow("Abrieron la app (7 días)", a.opened7d())
                + kvRow("Oraron (30 días)", a.prayed30d())
                + kvRow("Abrieron la app (30 días)", a.opened30d());
    }

    private String kvRow(String label, long value) {
        return """
                <tr>
                  <td style="padding:6px 8px;border-bottom:1px solid #f3f4f6">%s</td>
                  <td style="padding:6px 8px;border-bottom:1px solid #f3f4f6;text-align:right;font-weight:bold">%d</td>
                </tr>
                """.formatted(label, value);
    }

    // ── Ventanas de 7 días sobre la serie diaria (ordenada oldest→today) ──────

    private long thisWeek(List<DailyCount> series) {
        return sumLast(series, 7);
    }

    private long lastWeek(List<DailyCount> series) {
        int end = Math.max(0, series.size() - 7);
        return series.subList(0, end).stream().mapToLong(DailyCount::count).sum();
    }

    private long sumLast(List<DailyCount> series, int n) {
        int from = Math.max(0, series.size() - n);
        return series.subList(from, series.size()).stream().mapToLong(DailyCount::count).sum();
    }
}
