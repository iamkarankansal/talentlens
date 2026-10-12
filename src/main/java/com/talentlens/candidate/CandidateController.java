package com.talentlens.candidate;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import com.talentlens.common.BadRequestException;
import com.talentlens.common.PageQuery;
import com.talentlens.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Candidates", description = "Candidates and their resume text")
@RequestMapping("/api/v1/candidates")
public class CandidateController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "fullName", "email", "createdAt");

    private final CandidateService service;
    private final ResumeTextExtractor resumeTextExtractor;

    public CandidateController(CandidateService service, ResumeTextExtractor resumeTextExtractor) {
        this.service = service;
        this.resumeTextExtractor = resumeTextExtractor;
    }

    @PostMapping
    public ResponseEntity<CandidateResponse> create(@Valid @RequestBody CandidateRequest request) {
        CandidateResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/candidates/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<CandidateResponse> findAll(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.findAll(q, PageQuery.of(page, size, sort, SORTABLE_FIELDS));
    }

    @GetMapping("/{id}")
    public CandidateResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public CandidateResponse update(@PathVariable Long id, @Valid @RequestBody CandidateRequest request) {
        return service.update(id, request);
    }

    /**
     * Replaces the candidate's resume with the text extracted from an uploaded PDF.
     */
    @PostMapping(path = "/{id}/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CandidateResponse uploadResume(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        service.getOrThrow(id);
        if (file.isEmpty()) {
            throw new BadRequestException("The uploaded file is empty");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("The uploaded file could not be read");
        }
        return service.replaceResume(id, resumeTextExtractor.extract(content));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
