package com.universal.reconciliation.repository;

import com.universal.reconciliation.domain.entity.BreakComment;
import com.universal.reconciliation.domain.entity.BreakItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for break comments.
 */
public interface BreakCommentRepository extends JpaRepository<BreakComment, Long> {

    List<BreakComment> findByBreakItemOrderByCreatedAtAsc(BreakItem breakItem);
}
