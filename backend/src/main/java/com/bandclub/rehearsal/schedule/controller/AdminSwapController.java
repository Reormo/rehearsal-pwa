package com.bandclub.rehearsal.schedule.controller;

import com.bandclub.rehearsal.schedule.domain.SwapRequestStatus;
import com.bandclub.rehearsal.schedule.service.SwapService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/swaps")
public class AdminSwapController {

    private final SwapService swapService;

    public AdminSwapController(SwapService swapService) {
        this.swapService = swapService;
    }

    @GetMapping
    public List<SwapService.SwapView> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) SwapRequestStatus status
    ) {
        return swapService.adminList(userId(jwt), status);
    }

    @PostMapping("/{swapRequestId}/accept")
    public SwapService.SwapView accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long swapRequestId,
            @Valid @RequestBody ReasonRequest request
    ) {
        return swapService.adminAccept(userId(jwt), swapRequestId, request.reason());
    }

    @PostMapping("/{swapRequestId}/reject")
    public SwapService.SwapView reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long swapRequestId,
            @Valid @RequestBody ReasonRequest request
    ) {
        return swapService.adminReject(userId(jwt), swapRequestId, request.reason());
    }

    @PostMapping("/direct")
    @ResponseStatus(HttpStatus.CREATED)
    public SwapService.SwapView direct(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DirectSwapRequest request
    ) {
        return swapService.adminDirect(
                userId(jwt),
                request.firstReservationId(),
                request.secondReservationId(),
                request.reason()
        );
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record ReasonRequest(
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record DirectSwapRequest(
            @NotNull Long firstReservationId,
            @NotNull Long secondReservationId,
            @NotBlank @Size(max = 500) String reason
    ) {
    }
}
