package com.dosotres.chain.dto;

import java.util.List;

public record ChainDetailResponse(
        ChainResponse chain,
        List<ChainSlotResponse> slots
) {}
