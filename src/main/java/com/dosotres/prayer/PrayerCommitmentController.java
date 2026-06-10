package com.dosotres.prayer;

import com.dosotres.prayer.dto.CommitmentResponse;
import com.dosotres.prayer.dto.CreateCommitmentRequest;
import com.dosotres.prayer.dto.FulfilCommitmentRequest;
import com.dosotres.security.annotations.AuthUser;
import com.dosotres.security.annotations.CurrentGroupId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/commitments")
public class PrayerCommitmentController {

    private final PrayerCommitmentService commitmentService;

    public PrayerCommitmentController(PrayerCommitmentService commitmentService) {
        this.commitmentService = commitmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommitmentResponse create(@Valid @RequestBody CreateCommitmentRequest req,
                                      @CurrentGroupId Long groupId,
                                      @AuthUser Long userId) {
        return commitmentService.create(req, groupId, userId);
    }

    @GetMapping
    public List<CommitmentResponse> listByDate(@AuthUser Long userId,
                                                @CurrentGroupId Long groupId,
                                                @RequestParam String date) {
        return commitmentService.listByDate(userId, groupId, LocalDate.parse(date));
    }

    @GetMapping("/by-request/{requestId}")
    public List<CommitmentResponse> listByRequest(@PathVariable Long requestId,
                                                    @CurrentGroupId Long groupId) {
        return commitmentService.listByRequest(requestId, groupId);
    }

    @PutMapping("/{id}/fulfil")
    public CommitmentResponse fulfil(@PathVariable Long id,
                                      @Valid @RequestBody FulfilCommitmentRequest req,
                                      @AuthUser Long userId) {
        return commitmentService.fulfil(id, req, userId);
    }
}
