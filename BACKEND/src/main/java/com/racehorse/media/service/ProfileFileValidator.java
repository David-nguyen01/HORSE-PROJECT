package com.racehorse.media.service;

import com.racehorse.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ProfileFileValidator {

    private static final String PDF = "application/pdf";
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public ValidatedProfileFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File hồ sơ không được để trống");
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("Không thể đọc file tải lên");
        }

        String contentType = normalizeContentType(file.getContentType());
        ProfileFileCategory category;
        String detectedImageType = detectImageType(content);
        if (detectedImageType != null) {
            category = ProfileFileCategory.IMAGE;
            contentType = detectedImageType;
        } else if (PDF.equals(contentType) && isPdf(content)) {
            category = ProfileFileCategory.PDF;
        } else if ((DOCX.equals(contentType) || filename.toLowerCase(Locale.ROOT).endsWith(".docx"))
                && isDocx(content)) {
            category = ProfileFileCategory.DOCX;
            contentType = DOCX;
        } else {
            throw new BusinessException("Chỉ chấp nhận file hình ảnh, PDF hoặc DOCX hợp lệ");
        }
        return new ValidatedProfileFile(content, filename, contentType, category);
    }

    public void validateCloudinaryResult(ProfileFileCategory category, CloudAsset asset) {
        boolean valid = switch (category) {
            case IMAGE -> "image".equals(asset.resourceType());
            case PDF -> ("image".equals(asset.resourceType()) || "raw".equals(asset.resourceType()))
                    && "pdf".equalsIgnoreCase(asset.format());
            case DOCX -> "raw".equals(asset.resourceType());
        };
        if (!valid) {
            throw new BusinessException("Cloudinary nhận diện loại file không khớp với file được phép");
        }
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "profile-file";
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (filename.isBlank()) {
            return "profile-file";
        }
        return filename.length() <= 255 ? filename : filename.substring(filename.length() - 255);
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "application/octet-stream" : contentType.toLowerCase(Locale.ROOT);
    }

    private boolean isPdf(byte[] content) {
        return content.length >= 5
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F'
                && content[4] == '-';
    }

    private String detectImageType(byte[] bytes) {
        if (starts(bytes,0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)) return "image/png";
        if (starts(bytes,0xff,0xd8,0xff)) return "image/jpeg";
        if (ascii(bytes,0,"GIF87a")||ascii(bytes,0,"GIF89a")) return "image/gif";
        if (ascii(bytes,0,"RIFF")&&ascii(bytes,8,"WEBP")) return "image/webp";
        if (starts(bytes,0x42,0x4d)) return "image/bmp";
        if (starts(bytes,0x49,0x49,0x2a,0x00)||starts(bytes,0x4d,0x4d,0x00,0x2a)) return "image/tiff";
        return null;
    }

    private boolean starts(byte[] bytes,int... signature){
        if(bytes.length<signature.length)return false;
        for(int i=0;i<signature.length;i++)if((bytes[i]&0xff)!=signature[i])return false;
        return true;
    }

    private boolean ascii(byte[] bytes,int offset,String value){
        if(bytes.length<offset+value.length())return false;
        for(int i=0;i<value.length();i++)if((char)bytes[offset+i]!=value.charAt(i))return false;
        return true;
    }

    private boolean isDocx(byte[] content) {
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("[Content_Types].xml".equals(entry.getName())) {
                    hasContentTypes = true;
                } else if ("word/document.xml".equals(entry.getName())) {
                    hasWordDocument = true;
                }
                if (hasContentTypes && hasWordDocument) {
                    return true;
                }
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    public record ValidatedProfileFile(
            byte[] content,
            String filename,
            String contentType,
            ProfileFileCategory category
    ) {
        public ValidatedProfileFile {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
