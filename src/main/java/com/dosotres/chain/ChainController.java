package com.dosotres.chain;

import com.dosotres.chain.dto.ChainDetailResponse;
import com.dosotres.chain.dto.ChainResponse;
import com.dosotres.chain.dto.CreateChainRequest;
import com.dosotres.security.annotations.AuthUser;
import com.dosotres.security.annotations.CurrentGroupId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chains")
public class ChainController {

    private final ChainService chainService;

    public ChainController(ChainService chainService) {
        this.chainService = chainService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChainResponse create(@Valid @RequestBody CreateChainRequest req,
                                @CurrentGroupId Long groupId,
                                @AuthUser Long userId) {
        return chainService.create(req, groupId, userId);
    }

    @GetMapping
    public List<ChainResponse> list(@CurrentGroupId Long groupId) {
        return chainService.listByGroup(groupId);
    }

    @GetMapping("/{id}")
    public ChainDetailResponse detail(@PathVariable Long id,
                                      @CurrentGroupId Long groupId) {
        return chainService.detail(id, groupId);
    }

    @PostMapping("/{id}/slots/{slotIndex}")
    public ChainDetailResponse subscribe(@PathVariable Long id,
                                         @PathVariable int slotIndex,
                                         @CurrentGroupId Long groupId,
                                         @AuthUser Long userId) {
        return chainService.subscribe(id, slotIndex, groupId, userId);
    }

    @DeleteMapping("/{id}/slots/{slotIndex}")
    public ChainDetailResponse unsubscribe(@PathVariable Long id,
                                           @PathVariable int slotIndex,
                                           @CurrentGroupId Long groupId,
                                           @AuthUser Long userId) {
        return chainService.unsubscribe(id, slotIndex, groupId, userId);
    }
}
