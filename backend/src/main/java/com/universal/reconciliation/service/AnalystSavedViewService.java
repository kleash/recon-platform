package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.SavedViewDto;
import com.universal.reconciliation.domain.dto.SavedViewRequest;
import com.universal.reconciliation.domain.entity.AnalystSavedView;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.repository.AnalystSavedViewRepository;
import com.universal.reconciliation.repository.ReconciliationDefinitionRepository;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages persistence of analyst saved views and ensures ownership rules are
 * enforced.
 */
@Service
public class AnalystSavedViewService {

    private static final HexFormat HEX = HexFormat.of();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReconciliationDefinitionRepository definitionRepository;
    private final AnalystSavedViewRepository savedViewRepository;
    private final BreakAccessService breakAccessService;

    public AnalystSavedViewService(
            ReconciliationDefinitionRepository definitionRepository,
            AnalystSavedViewRepository savedViewRepository,
            BreakAccessService breakAccessService) {
        this.definitionRepository = definitionRepository;
        this.savedViewRepository = savedViewRepository;
        this.breakAccessService = breakAccessService;
    }

    @Transactional(readOnly = true)
    public List<SavedViewDto> listViews(Long definitionId, String owner) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        return savedViewRepository.findByDefinitionAndOwnerOrderByUpdatedAtDesc(definition, owner).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SavedViewDto create(Long definitionId, String owner, SavedViewRequest request, List<String> groups) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        ensureAccess(definition, groups);
        AnalystSavedView view = new AnalystSavedView();
        view.setDefinition(definition);
        view.setOwner(owner);
        apply(view, request);
        if (request.defaultView()) {
            clearExistingDefault(definition, owner);
            view.setDefaultView(true);
        }
        savedViewRepository.save(view);
        return toDto(view);
    }

    @Transactional
    public SavedViewDto update(Long definitionId, Long viewId, String owner, SavedViewRequest request, List<String> groups) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        ensureAccess(definition, groups);
        AnalystSavedView view = savedViewRepository.findById(viewId)
                .orElseThrow(() -> new IllegalArgumentException("Saved view not found"));
        assertOwner(owner, view);
        apply(view, request);
        if (request.defaultView()) {
            clearExistingDefault(definition, owner);
            view.setDefaultView(true);
        } else if (view.isDefaultView() && !request.defaultView()) {
            view.setDefaultView(false);
        }
        view.touch();
        return toDto(view);
    }

    @Transactional
    public void delete(Long definitionId, Long viewId, String owner, List<String> groups) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        ensureAccess(definition, groups);
        AnalystSavedView view = savedViewRepository.findById(viewId)
                .orElseThrow(() -> new IllegalArgumentException("Saved view not found"));
        assertOwner(owner, view);
        savedViewRepository.delete(view);
    }

    @Transactional(readOnly = true)
    public Optional<SavedViewDto> findShared(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return savedViewRepository.findBySharedToken(token).map(this::toDto);
    }

    @Transactional
    public void setDefault(Long definitionId, Long viewId, String owner, List<String> groups) {
        ReconciliationDefinition definition = loadDefinition(definitionId);
        ensureAccess(definition, groups);
        AnalystSavedView view = savedViewRepository.findById(viewId)
                .orElseThrow(() -> new IllegalArgumentException("Saved view not found"));
        assertOwner(owner, view);
        clearExistingDefault(definition, owner);
        view.setDefaultView(true);
        view.touch();
    }

    private void apply(AnalystSavedView view, SavedViewRequest request) {
        view.setName(request.name().trim());
        view.setDescription(request.description());
        view.setSettingsJson(request.settingsJson());
        view.setShared(request.shared());
        if (request.shared()) {
            if (view.getSharedToken() == null) {
                view.setSharedToken(generateToken());
            }
        } else {
            view.setSharedToken(null);
        }
        view.touch();
    }

    private void assertOwner(String owner, AnalystSavedView view) {
        if (!Objects.equals(owner, view.getOwner())) {
            throw new IllegalArgumentException("Saved view does not belong to the current user");
        }
    }

    private ReconciliationDefinition loadDefinition(Long definitionId) {
        return definitionRepository.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation not found"));
    }

    private void ensureAccess(ReconciliationDefinition definition, List<String> groups) {
        breakAccessService.findEntries(definition, groups);
    }

    private void clearExistingDefault(ReconciliationDefinition definition, String owner) {
        savedViewRepository
                .findByDefinitionAndOwnerAndDefaultView(definition, owner, true)
                .ifPresent(existing -> {
                    existing.setDefaultView(false);
                    existing.touch();
                });
    }

    private String generateToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes).toLowerCase(Locale.ROOT);
    }

    private SavedViewDto toDto(AnalystSavedView view) {
        return new SavedViewDto(
                view.getId(),
                view.getName(),
                view.getDescription(),
                view.isShared(),
                view.isDefaultView(),
                view.getSharedToken(),
                view.getSettingsJson(),
                view.getUpdatedAt());
    }
}

