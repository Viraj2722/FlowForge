package com.flowforge.domain.repository;

import com.flowforge.domain.entity.DeadLetterTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link DeadLetterTask}. Kept separate from the hot
 * task_executions path so dead-letter inspection/replay doesn't scan live work.
 */
public interface DeadLetterTaskRepository extends JpaRepository<DeadLetterTask, Long> {

    Page<DeadLetterTask> findByReplayedFalse(Pageable pageable);
}
