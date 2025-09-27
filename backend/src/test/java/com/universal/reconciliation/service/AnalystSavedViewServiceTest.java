package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universal.reconciliation.domain.dto.SavedViewDto;
import com.universal.reconciliation.domain.dto.SavedViewRequest;
import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.AnalystSavedView;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.repository.AnalystSavedViewRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalystSavedViewServiceTest {

    @Mock
    private ReconciliationDefinitionRepository definitionRepository;

    @Mock
    private AnalystSavedViewRepository savedViewRepository;

    @Mock
    private BreakAccessService breakAccessService;

    @InjectMocks
    private AnalystSavedViewService service;

    private ReconciliationDefinition definition;

    @BeforeEach
    void setUp() {
        definition = new ReconciliationDefinition();
        definition.setId(42L);
    }

    private void stubDefinitionAccess() {
        when(definitionRepository.findById(definition.getId())).thenReturn(Optional.of(definition));
        when(breakAccessService.findEntries(eq(definition), any())).thenReturn(List.of(new AccessControlEntry()));
    }

    @Test
    void createShouldPersistNewSavedView() {
        stubDefinitionAccess();
        SavedViewRequest request = new SavedViewRequest("My grid", "desc", "{}", true, false);
        ArgumentCaptor<AnalystSavedView> captor = ArgumentCaptor.forClass(AnalystSavedView.class);

        SavedViewDto dto = service.create(definition.getId(), "user1", request, List.of("grp"));

        verify(savedViewRepository).save(captor.capture());
        AnalystSavedView saved = captor.getValue();
        assertThat(saved.getDefinition()).isEqualTo(definition);
        assertThat(saved.getOwner()).isEqualTo("user1");
        assertThat(saved.isShared()).isTrue();
        assertThat(saved.getSharedToken()).isNotBlank();
        assertThat(dto.name()).isEqualTo("My grid");
    }

    @Test
    void createShouldUnsetExistingDefaultWhenRequested() {
        stubDefinitionAccess();
        AnalystSavedView currentDefault = new AnalystSavedView();
        currentDefault.setId(1L);
        currentDefault.setOwner("user1");
        currentDefault.setDefinition(definition);
        currentDefault.setDefaultView(true);
        when(savedViewRepository.findByDefinitionAndOwnerAndDefaultView(definition, "user1", true))
                .thenReturn(Optional.of(currentDefault));

        SavedViewRequest request = new SavedViewRequest("Default", null, "{}", false, true);

        service.create(definition.getId(), "user1", request, List.of("grp"));

        assertThat(currentDefault.isDefaultView()).isFalse();
    }

    @Test
    void updateShouldRejectDifferentOwner() {
        stubDefinitionAccess();
        AnalystSavedView view = new AnalystSavedView();
        view.setId(10L);
        view.setOwner("userA");
        when(savedViewRepository.findById(10L)).thenReturn(Optional.of(view));

        SavedViewRequest request = new SavedViewRequest("Renamed", null, "{}", false, false);

        assertThatThrownBy(() ->
                        service.update(definition.getId(), 10L, "otherUser", request, List.of("grp")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void updateShouldOverwriteAttributes() {
        stubDefinitionAccess();
        AnalystSavedView view = new AnalystSavedView();
        view.setId(99L);
        view.setOwner("owner");
        view.setName("Old name");
        when(savedViewRepository.findById(99L)).thenReturn(Optional.of(view));

        SavedViewRequest request = new SavedViewRequest("New", "text", "{}", true, true);

        SavedViewDto dto = service.update(definition.getId(), 99L, "owner", request, List.of("grp"));

        assertThat(dto.name()).isEqualTo("New");
        assertThat(view.getDescription()).isEqualTo("text");
        assertThat(view.isShared()).isTrue();
        assertThat(view.isDefaultView()).isTrue();
    }

    @Test
    void deleteShouldDelegateToRepository() {
        stubDefinitionAccess();
        AnalystSavedView view = new AnalystSavedView();
        view.setId(77L);
        view.setOwner("owner");
        when(savedViewRepository.findById(77L)).thenReturn(Optional.of(view));

        service.delete(definition.getId(), 77L, "owner", List.of("grp"));

        verify(savedViewRepository).delete(view);
    }

    @Test
    void setDefaultShouldClearOthers() {
        stubDefinitionAccess();
        AnalystSavedView view = new AnalystSavedView();
        view.setId(33L);
        view.setOwner("owner");
        when(savedViewRepository.findById(33L)).thenReturn(Optional.of(view));

        AnalystSavedView other = new AnalystSavedView();
        other.setId(44L);
        other.setOwner("owner");
        other.setDefinition(definition);
        other.setDefaultView(true);
        when(savedViewRepository.findByDefinitionAndOwnerAndDefaultView(definition, "owner", true))
                .thenReturn(Optional.of(other));

        service.setDefault(definition.getId(), 33L, "owner", List.of("grp"));

        assertThat(other.isDefaultView()).isFalse();
        assertThat(view.isDefaultView()).isTrue();
    }

    @Test
    void findSharedShouldReturnEmptyForBlankToken() {
        assertThat(service.findShared(" ")).isEmpty();
    }

    @Test
    void findSharedShouldMapEntityToDto() {
        AnalystSavedView view = new AnalystSavedView();
        view.setId(5L);
        view.setName("Team view");
        view.setSharedToken(UUID.randomUUID().toString());
        when(savedViewRepository.findBySharedToken(view.getSharedToken())).thenReturn(Optional.of(view));

        assertThat(service.findShared(view.getSharedToken())).hasValueSatisfying(dto -> {
            assertThat(dto.id()).isEqualTo(5L);
            assertThat(dto.name()).isEqualTo("Team view");
        });
    }
}
