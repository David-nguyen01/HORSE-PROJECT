package com.racehorse.horse.controller;

import com.racehorse.horse.service.HorseService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import static org.mockito.Mockito.*;

class TrainerHorseSearchTest {
    @Test
    void searchUsesAuthenticatedTrainerAndManagersKeepTheirClubScope() {
        var service = mock(HorseService.class);
        var controller = new HorseController(service);
        var jwt = mock(Jwt.class);
        UUID id = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(id.toString());
        var page = PageRequest.of(2, 20);
        var trainer = new UsernamePasswordAuthenticationToken("user", null,
                List.of(new SimpleGrantedAuthority("ROLE_HEAD_TRAINER")));
        controller.search("horse", null, page, jwt, trainer);
        verify(service).searchForTrainer("horse", null, id, page);
        verify(service, never()).search(any(), any(), any());
        var manager = new UsernamePasswordAuthenticationToken("user", null,
                List.of(new SimpleGrantedAuthority("ROLE_HEAD_TRAINER"), new SimpleGrantedAuthority("ROLE_CLUB_MANAGER")));
        controller.search("horse", null, page, jwt, manager);
        verify(service).search("horse", null, page);
    }
}
