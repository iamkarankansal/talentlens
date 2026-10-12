package com.talentlens.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.talentlens.ai.ResumeParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ResumeUploadApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandidateRepository repository;

    @MockitoBean
    private ResumeParser resumeParser;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void extractsTextFromAnUploadedPdf() throws Exception {
        Long id = saveCandidate(null);

        mockMvc.perform(multipart("/api/v1/candidates/" + id + "/resume")
                        .file(file(pdf("Senior Java developer with Kafka experience"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasResume", is(true)));

        String stored = repository.findById(id).orElseThrow().getResumeText();
        assertThat(stored).contains("Senior Java developer with Kafka experience");
    }

    @Test
    void replacingTheResumeClearsTheParsedProfile() throws Exception {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Verma");
        candidate.setEmail("asha@example.com");
        candidate.setResumeText("Old resume");
        candidate.setProfile("Backend Engineer", 4.0, List.of("Java"), Instant.now());
        Long id = repository.save(candidate).getId();

        mockMvc.perform(multipart("/api/v1/candidates/" + id + "/resume").file(file(pdf("New resume text"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", empty()));
    }

    @Test
    void rejectsFilesThatAreNotPdfs() throws Exception {
        Long id = saveCandidate(null);
        byte[] text = "just some text".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/v1/candidates/" + id + "/resume").file(file(text)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The uploaded file is not a PDF")));
    }

    @Test
    void rejectsPdfsWithNoText() throws Exception {
        Long id = saveCandidate(null);

        mockMvc.perform(multipart("/api/v1/candidates/" + id + "/resume").file(file(pdf(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnEmptyFile() throws Exception {
        Long id = saveCandidate(null);

        mockMvc.perform(multipart("/api/v1/candidates/" + id + "/resume").file(file(new byte[0])))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownCandidate() throws Exception {
        mockMvc.perform(multipart("/api/v1/candidates/9999/resume").file(file(pdf("Resume"))))
                .andExpect(status().isNotFound());
    }

    private Long saveCandidate(String resume) {
        Candidate candidate = new Candidate();
        candidate.setFullName("Asha Verma");
        candidate.setEmail("asha@example.com");
        candidate.setResumeText(resume);
        return repository.save(candidate).getId();
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", content);
    }

    private static byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (text != null) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
