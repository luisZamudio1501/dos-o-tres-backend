package com.dosotres.prayer.dto;

/** Cuerpo de POST /prayer-requests/{id}/pray — "oré por esto" (botón directo). */
public record PrayRequest(boolean isPrivate) {}
