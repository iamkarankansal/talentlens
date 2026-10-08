package com.talentlens.candidate;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/candidates")
public class CandidateController {

    private final CandidateService service;

    public CandidateController(CandidateService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CandidateResponse> create(@Valid @RequestBody CandidateRequest request) {
        CandidateResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/candidates/" + created.id())).body(created);
    }

    @GetMapping
    public List<CandidateResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public CandidateResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public CandidateResponse update(@PathVariable Long id, @Valid @RequestBody CandidateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
