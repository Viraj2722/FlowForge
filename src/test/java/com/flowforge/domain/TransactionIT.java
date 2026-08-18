package com.flowforge.domain;

import com.flowforge.domain.entity.Workflow;
import com.flowforge.domain.repository.WorkflowRepository;
import com.flowforge.engine.model.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves two transaction guarantees against the real database. This test is deliberately
 * NOT {@code @Transactional} at the class level - we run several SEPARATE transactions
 * via {@link TransactionTemplate} so we can observe cross-transaction behaviour, and we
 * clean up manually.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
class TransactionIT {

    @Autowired
    private WorkflowRepository workflows;

    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
    }

    private <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    private void inTxVoid(Runnable work) {
        tx.executeWithoutResult(status -> work.run());
    }

    @Test
    void optimisticLockingRejectsAStaleUpdate() {
        Long id = inTx(() -> workflows.save(new Workflow("OptLock", null, Priority.LOW)).getId());
        try {
            // Load a copy, then let it go stale by updating the row in another transaction.
            Workflow stale = inTx(() -> workflows.findById(id).orElseThrow());
            inTxVoid(() -> workflows.findById(id).orElseThrow().setDescription("bumped by writer #2"));

            // Writer #1 tries to save its stale copy (old @Version) -> conflict.
            assertThatThrownBy(() -> inTxVoid(() -> {
                stale.setDescription("bumped by writer #1");
                workflows.save(stale);
            })).isInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            inTxVoid(() -> workflows.deleteById(id));
        }
    }

    @Test
    void runtimeExceptionRollsBackTheWholeTransaction() {
        AtomicReference<Long> idRef = new AtomicReference<>();

        assertThatThrownBy(() -> inTxVoid(() -> {
            Workflow saved = workflows.save(new Workflow("RollbackDemo", null, Priority.LOW));
            idRef.set(saved.getId());       // identity assigned by the INSERT inside this tx
            assertThat(saved.getId()).isNotNull();
            throw new RuntimeException("boom -> must roll back the INSERT");
        })).isInstanceOf(RuntimeException.class);

        // The INSERT was undone by the rollback: the row must not exist.
        boolean stillThere = inTx(() -> workflows.findById(idRef.get()).isPresent());
        assertThat(stillThere).isFalse();
    }
}
