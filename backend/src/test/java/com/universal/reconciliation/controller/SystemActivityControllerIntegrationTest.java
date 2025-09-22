package com.universal.reconciliation.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.universal.reconciliation.domain.dto.SystemActivityDto;
import com.universal.reconciliation.domain.enums.SystemEventType;
import com.universal.reconciliation.service.SystemActivityService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class SystemActivityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemActivityService systemActivityService;

    @Test
    void recentActivity_withoutGroupsReturnsForbidden() throws Exception {
        List<GrantedAuthority> authorities = List.of();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("no-groups", "password", authorities);

        mockMvc.perform(get("/api/activity")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authentication)))
                .andExpect(status().isForbidden());

        verify(systemActivityService, never()).fetchRecent();
    }

    @Test
    @WithMockUser(username = "auditor", authorities = {"recon-makers"})
    void recentActivity_returnsTimelineWhenGroupsPresent() throws Exception {
        List<SystemActivityDto> feed = List.of(new SystemActivityDto(
                1L, SystemEventType.RECONCILIATION_RUN, "Cash run executed", Instant.parse("2024-01-01T10:00:00Z")));
        when(systemActivityService.fetchRecent()).thenReturn(feed);

        mockMvc.perform(get("/api/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].details").value("Cash run executed"));

        verify(systemActivityService).fetchRecent();
    }
}
