package com.universal.reconciliation.service.matching;

import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.entity.SourceRecordA;
import com.universal.reconciliation.domain.entity.SourceRecordB;
import com.universal.reconciliation.domain.enums.BreakType;
import com.universal.reconciliation.repository.SourceRecordARepository;
import com.universal.reconciliation.repository.SourceRecordBRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Implements the MVP exact-matching algorithm required for Phase 1.
 */
@Component
public class ExactMatchingEngine implements MatchingEngine {

    private final SourceRecordARepository sourceARepository;
    private final SourceRecordBRepository sourceBRepository;

    public ExactMatchingEngine(
            SourceRecordARepository sourceARepository,
            SourceRecordBRepository sourceBRepository) {
        this.sourceARepository = sourceARepository;
        this.sourceBRepository = sourceBRepository;
    }

    @Override
    public MatchingResult execute(ReconciliationDefinition definition) {
        List<SourceRecordA> aRecords = sourceARepository.findAll();
        List<SourceRecordB> bRecords = sourceBRepository.findAll();

        Map<String, SourceRecordA> aById = new HashMap<>();
        for (SourceRecordA record : aRecords) {
            aById.put(record.getTransactionId(), record);
        }

        Map<String, SourceRecordB> bById = new HashMap<>();
        for (SourceRecordB record : bRecords) {
            bById.put(record.getTransactionId(), record);
        }

        Set<String> allIds = new HashSet<>();
        allIds.addAll(aById.keySet());
        allIds.addAll(bById.keySet());

        int matched = 0;
        int mismatched = 0;
        int missing = 0;
        List<BreakCandidate> breakCandidates = new ArrayList<>();

        for (String id : allIds) {
            SourceRecordA a = aById.get(id);
            SourceRecordB b = bById.get(id);

            if (a != null && b != null) {
                if (recordsMatchExactly(a, b)) {
                    matched++;
                } else {
                    mismatched++;
                    breakCandidates.add(new BreakCandidate(BreakType.MISMATCH, mapRecordA(a), mapRecordB(b)));
                }
            } else if (a != null) {
                missing++;
                breakCandidates.add(new BreakCandidate(BreakType.MISSING_IN_SOURCE_B, mapRecordA(a), Map.of()));
            } else {
                missing++;
                breakCandidates.add(new BreakCandidate(BreakType.MISSING_IN_SOURCE_A, Map.of(), mapRecordB(b)));
            }
        }

        return new MatchingResult(matched, mismatched, missing, breakCandidates);
    }

    private boolean recordsMatchExactly(SourceRecordA a, SourceRecordB b) {
        return a.getAmount().compareTo(b.getAmount()) == 0
                && a.getCurrency().equalsIgnoreCase(b.getCurrency())
                && a.getTradeDate().equals(b.getTradeDate());
    }

    private Map<String, Object> mapRecordA(SourceRecordA record) {
        if (record == null) {
            return Map.of();
        }
        Map<String, Object> map = new HashMap<>();
        map.put("transactionId", record.getTransactionId());
        map.put("amount", record.getAmount());
        map.put("currency", record.getCurrency());
        map.put("tradeDate", record.getTradeDate());
        return map;
    }

    private Map<String, Object> mapRecordB(SourceRecordB record) {
        if (record == null) {
            return Map.of();
        }
        Map<String, Object> map = new HashMap<>();
        map.put("transactionId", record.getTransactionId());
        map.put("amount", record.getAmount());
        map.put("currency", record.getCurrency());
        map.put("tradeDate", record.getTradeDate());
        return map;
    }
}
