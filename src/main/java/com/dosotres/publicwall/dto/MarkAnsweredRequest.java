package com.dosotres.publicwall.dto;

import jakarta.validation.constraints.Size;

/** Marcar un pedido público como respondido, con testimonio opcional. */
public record MarkAnsweredRequest(
        @Size(max = 2000) String testimony
) {}
