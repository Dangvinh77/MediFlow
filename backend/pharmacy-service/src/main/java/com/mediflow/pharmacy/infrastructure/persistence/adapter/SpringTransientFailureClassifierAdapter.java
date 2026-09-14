package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import com.mediflow.pharmacy.application.port.out.TransientFailureClassifierPort;

/**
 * Translates Spring's database failure hierarchy into the framework-free application port.
 */
@Component
public class SpringTransientFailureClassifierAdapter implements TransientFailureClassifierPort {

    /** {@inheritDoc} */
    @Override
    public boolean isTransient(RuntimeException exception) {
        return exception instanceof TransientDataAccessException
                || exception instanceof CannotAcquireLockException
                || exception instanceof PessimisticLockingFailureException
                || exception instanceof DataAccessResourceFailureException;
    }
}
