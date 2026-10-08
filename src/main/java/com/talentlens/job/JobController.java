package com.talentlens.job;

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
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<JobResponse> create(@Valid @RequestBody JobRequest request) {
        JobResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + created.id())).body(created);
    }

    @GetMapping
    public List<JobResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public JobResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public JobResponse update(@PathVariable Long id, @Valid @RequestBody JobRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
