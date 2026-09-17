package com.racehorse.media.controller;

import com.racehorse.media.service.StoredFileService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api/files")
public class StoredFileController {
    private final StoredFileService service;
    public StoredFileController(StoredFileService service){this.service=service;}
    @PostMapping(consumes="multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PERM_PROFILE_EDIT','PERM_TRAINING_EDIT','PERM_MEDICAL_EDIT','PERM_STABLE_CARE_EDIT')")
    StoredFileService.FileView upload(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String scope,@RequestPart("file") MultipartFile file){return service.upload(UUID.fromString(jwt.getSubject()),scope,file);}
    @GetMapping @PreAuthorize("isAuthenticated()")
    List<StoredFileService.FileView> list(@AuthenticationPrincipal Jwt jwt,Authentication auth,@RequestParam(required=false) UUID uploadedBy,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        boolean manager=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_CLUB_MANAGER"));
        UUID requester=UUID.fromString(jwt.getSubject());
        return service.list(manager?uploadedBy:requester,page,size);
    }
    @GetMapping("/{id}") @PreAuthorize("isAuthenticated()")
    StoredFileService.FileView get(@PathVariable UUID id){return service.get(id);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CLUB_MANAGER')") void delete(@PathVariable UUID id){service.delete(id);}
}
