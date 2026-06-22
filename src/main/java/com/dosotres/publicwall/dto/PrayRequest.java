package com.dosotres.publicwall.dto;

/** Orar por un pedido público; visible=true muestra el nombre del orante, default anónimo. */
public record PrayRequest(
        boolean visible
) {}
