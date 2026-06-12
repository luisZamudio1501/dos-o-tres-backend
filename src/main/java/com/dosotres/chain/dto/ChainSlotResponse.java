package com.dosotres.chain.dto;

import java.util.List;

public record ChainSlotResponse(
        int index,
        int startMinutes,
        List<SlotSubscriber> subscribers
) {
    public record SlotSubscriber(Long userId, String displayName) {}
}
