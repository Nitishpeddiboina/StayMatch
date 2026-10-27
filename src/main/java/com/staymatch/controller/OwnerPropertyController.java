package com.staymatch.controller;

import com.staymatch.dto.OwnerPropertyRequest;
import com.staymatch.dto.OwnerPropertyResponse;
import com.staymatch.security.StayMatchUserDetails;
import com.staymatch.service.OwnerPropertyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/properties")
public class OwnerPropertyController {

    private final OwnerPropertyService ownerPropertyService;

    public OwnerPropertyController(OwnerPropertyService ownerPropertyService) {
        this.ownerPropertyService = ownerPropertyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerPropertyResponse create(
            @AuthenticationPrincipal StayMatchUserDetails owner,
            @Valid @RequestBody OwnerPropertyRequest request) {
        return ownerPropertyService.create(owner, request);
    }

    @GetMapping
    public List<OwnerPropertyResponse> listMine(@AuthenticationPrincipal StayMatchUserDetails owner) {
        return ownerPropertyService.listMine(owner);
    }

    @PutMapping("/{id}")
    public OwnerPropertyResponse update(
            @AuthenticationPrincipal StayMatchUserDetails owner,
            @PathVariable Long id,
            @Valid @RequestBody OwnerPropertyRequest request) {
        return ownerPropertyService.update(owner, id, request);
    }

    @PatchMapping("/{id}/deactivate")
    public OwnerPropertyResponse deactivate(
            @AuthenticationPrincipal StayMatchUserDetails owner,
            @PathVariable Long id) {
        return ownerPropertyService.deactivate(owner, id);
    }
}
