package com.racehorse.media.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.media.dto.HorsePhotoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorsePhotoServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private StoredFileService storedFileService;
    @Mock private HorseAccessService horseAccessService;

    private HorsePhotoService service;
    private final ObjectMapper json = new ObjectMapper();
    private final ProfileFileValidator validator = new ProfileFileValidator();

    @BeforeEach
    void setUp() {
        service = new HorsePhotoService(
                jdbc,
                json,
                storedFileService,
                horseAccessService,
                validator);
    }

    @Test
    void uploadRejectsWhenTenPhotoLimitReached() {
        UUID horseId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Horse horse = horse();
        when(horseAccessService.requireActive(horseId)).thenReturn(horse);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(horseId))).thenReturn(10);

        MockMultipartFile file = imageFile();

        assertThatThrownBy(() -> service.upload(horseId, managerId, file, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tối đa 10 ảnh");
    }

    @Test
    void uploadRejectsNonImageFiles() {
        UUID horseId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Horse horse = horse();
        when(horseAccessService.requireActive(horseId)).thenReturn(horse);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(horseId))).thenReturn(2);

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.4 test".getBytes());

        assertThatThrownBy(() -> service.upload(horseId, managerId, pdfFile, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chỉ chấp nhận file ảnh");
    }

    @Test
    void uploadAutoAssignsLowestUnusedDisplayOrder() {
        UUID horseId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID storedId = UUID.randomUUID();
        Horse horse = horse();

        when(horseAccessService.requireActive(horseId)).thenReturn(horse);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(horseId))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(Integer.class), eq(horseId))).thenReturn(List.of(1));

        StoredFileService.FileView storedFile = new StoredFileService.FileView(
                storedId,
                managerId,
                "public-id",
                "image",
                "png",
                "horse.png",
                "image/png",
                1024L,
                "https://cloudinary.com/photo.png",
                OffsetDateTime.now());

        when(storedFileService.upload(eq(managerId), eq("horses/" + horseId + "/gallery"), any()))
                .thenReturn(storedFile);

        HorsePhotoResponse response = service.upload(
                horseId, managerId, imageFile(), null, "Mặt nghiêng");

        assertThat(response.displayOrder()).isEqualTo(2);
        assertThat(response.caption()).isEqualTo("Mặt nghiêng");
        assertThat(response.secureUrl()).isEqualTo("https://cloudinary.com/photo.png");
        verify(jdbc).update(anyString(), any(), eq(horseId), eq(managerId), any());
    }

    @Test
    void deleteMarksRecordRemovedAndDeletesStoredFile() {
        UUID horseId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UUID storedId = UUID.randomUUID();
        Horse horse = horse();

        when(horseAccessService.requireActive(horseId)).thenReturn(horse);
        String dataJson = "{\"storedFileId\":\"" + storedId + "\",\"displayOrder\":1}";
        when(jdbc.queryForList(anyString(), eq(String.class), eq(photoId), eq(horseId)))
                .thenReturn(List.of(dataJson));

        service.delete(horseId, photoId, UUID.randomUUID());

        verify(jdbc).update(anyString(), eq(photoId));
        verify(storedFileService).delete(storedId);
    }

    @Test
    void deleteThrowsNotFoundWhenPhotoMissing() {
        UUID horseId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        when(horseAccessService.requireActive(horseId)).thenReturn(horse());
        when(jdbc.queryForList(anyString(), eq(String.class), eq(photoId), eq(horseId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.delete(horseId, photoId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy ảnh bổ sung");
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile(
                "file",
                "horse.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3});
    }

    private Horse horse() {
        return new Horse(
                "H-001",
                "Lightning",
                null,
                LocalDate.of(2022, 3, 10),
                HorseGender.STALLION,
                "White blaze",
                "Thoroughbred",
                "Stable A-01",
                HorseStatus.ACTIVE);
    }
}
