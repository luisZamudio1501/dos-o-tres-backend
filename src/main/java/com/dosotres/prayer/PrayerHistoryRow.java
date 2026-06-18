package com.dosotres.prayer;

import java.time.Instant;

/** Proyección para la agenda de oración personal: un pedido + cuántas veces oró + última vez. */
public interface PrayerHistoryRow {
    PrayerRequest getRequest();
    Long getCnt();
    Instant getLastPrayedAt();
}
