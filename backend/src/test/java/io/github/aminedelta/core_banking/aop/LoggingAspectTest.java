package io.github.aminedelta.core_banking.aop;

import io.github.aminedelta.core_banking.dto.TransferResult;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoggingAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    private LoggingAspect loggingAspect;

    @BeforeEach
    void setUp() {
        loggingAspect = new LoggingAspect();
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("transfer");
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("25.00"),
                "Test transfer",
                UUID.randomUUID().toString()
        });
    }

    @Test
    void logExecutionTime_returnsSuccessfulTransferResult() throws Throwable {
        TransferResult expected = new TransferResult(
                UUID.randomUUID(),
                "SUCCESS",
                "Transfer completed successfully"
        );
        when(joinPoint.proceed()).thenReturn(expected);

        Object actual = loggingAspect.logExecutionTime(joinPoint);

        assertSame(expected, actual);
    }

    @Test
    void logExecutionTime_rethrowsTransferFailure() throws Throwable {
        RuntimeException expected = new RuntimeException("Transfer failed");
        when(joinPoint.proceed()).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> loggingAspect.logExecutionTime(joinPoint)
        );

        assertSame(expected, actual);
    }
}
