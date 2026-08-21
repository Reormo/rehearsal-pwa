package com.bandclub.rehearsal.schedule.controller;

import com.bandclub.rehearsal.schedule.service.SwapService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/swaps")
public class SwapController {

    private final SwapService swapService;

    public SwapController(SwapService swapService) {
        this.swapService = swapService;
    }

    @GetMapping
    public List<SwapService.SwapView> mine(@AuthenticationPrincipal Jwt jwt) {
        return swapService.mine(userId(jwt));
    }

    @GetMapping("/candidates")
    public List<SwapService.CandidateView> candidates(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Long requesterReservationId
    ) {
        return swapService.candidates(userId(jwt), requesterReservationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SwapService.SwapView request(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSwapRequest request
    ) {
        return swapService.request(
                userId(jwt),
                request.requesterReservationId(),
                request.targetReservationId()
        );
    }

    @PostMapping("/{swapRequestId}/accept")
    public SwapService.SwapView accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long swapRequestId
    ) {
        return swapService.accept(userId(jwt), swapRequestId);
    }

    @PostMapping("/{swapRequestId}/reject")
    public SwapService.SwapView reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long swapRequestId
    ) {
        return swapService.reject(userId(jwt), swapRequestId);
    }

    @PostMapping("/{swapRequestId}/cancel")
    public SwapService.SwapView cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long swapRequestId
    ) {
        return swapService.cancel(userId(jwt), swapRequestId);
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record CreateSwapRequest(
            @NotNull Long requesterReservationId,
            @NotNull Long targetReservationId
    ) {
    }
}
