package com.racehorse.media.service;

import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileFileValidatorTest {

    private final ProfileFileValidator validator = new ProfileFileValidator();

    @Test
    void acceptsImagePdfAndRealDocxContainer() throws IOException {
        assertThat(validator.validate(new MockMultipartFile(
                "file", "horse.png", "application/octet-stream", pngBytes())).category())
                .isEqualTo(ProfileFileCategory.IMAGE);
        assertThat(validator.validate(new MockMultipartFile(
                "file", "record.pdf", "application/pdf", "%PDF-1.7".getBytes())).category())
                .isEqualTo(ProfileFileCategory.PDF);
        assertThat(validator.validate(new MockMultipartFile(
                "file", "record.docx", "application/octet-stream", docxBytes())).category())
                .isEqualTo(ProfileFileCategory.DOCX);
    }

    @Test
    void rejectsEmptyExecutableAndFakeDocuments() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0])))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", new byte[]{1, 2, 3})))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "not-pdf".getBytes())))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "fake.png", "image/png", new byte[]{1,2,3})))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void checksCloudinaryDetectedResourceType() {
        CloudAsset rawAsset = new CloudAsset(
                "asset", "public", "raw", "exe", 10, "https://example.test/file");

        assertThatThrownBy(() -> validator.validateCloudinaryResult(
                ProfileFileCategory.IMAGE, rawAsset))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không khớp");
    }

    private byte[] docxBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes());
            zip.closeEntry();
        }
        return output.toByteArray();
    }
    private byte[] pngBytes(){return new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,1};}
}
