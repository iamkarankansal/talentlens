package com.talentlens.candidate;

import java.io.IOException;

import com.talentlens.common.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Pulls the plain text out of an uploaded PDF resume.
 */
@Component
public class ResumeTextExtractor {

    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};

    public String extract(byte[] content) {
        if (!isPdf(content)) {
            throw new BadRequestException("The uploaded file is not a PDF");
        }
        String text;
        try (PDDocument document = Loader.loadPDF(content)) {
            text = new PDFTextStripper().getText(document);
        } catch (InvalidPasswordException ex) {
            throw new BadRequestException("Password-protected PDFs are not supported");
        } catch (IOException ex) {
            throw new BadRequestException("The uploaded PDF could not be read");
        }
        // PostgreSQL text columns reject NUL characters.
        String cleaned = text.replace("\u0000", "").strip();
        if (cleaned.isEmpty()) {
            throw new BadRequestException("No text could be extracted from the PDF; scanned images are not supported");
        }
        return cleaned;
    }

    private static boolean isPdf(byte[] content) {
        if (content == null || content.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PDF_SIGNATURE.length; i++) {
            if (content[i] != PDF_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
}
