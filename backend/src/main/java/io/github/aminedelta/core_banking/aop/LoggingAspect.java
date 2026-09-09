package io.github.aminedelta.core_banking.aop;

import io.github.aminedelta.core_banking.dto.TransferResult;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;


@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Around("@annotation(io.github.aminedelta.core_banking.aop.AuditLog)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        
        String methodName = joinPoint.getSignature().getName();
        Object[] arguments = joinPoint.getArgs();
        long startTime = System.currentTimeMillis();
        
        if (arguments != null && arguments.length >= 3) {
            log.info("Executing {} fromAccountId={} toAccountId={} amount={}",
                methodName, arguments[0], arguments[1], arguments[2]);
        } else {
            log.info("Executing {}", methodName);
        }

        try {
            Object result = joinPoint.proceed();
            if (result instanceof TransferResult transferResult) {
                log.info("Transfer succeeded transactionId={}", transferResult.transactionId());
            } else {
                log.info("Method {} completed successfully", methodName);
            }
            return result;
            
        } catch (Throwable e) {
            log.error("Method {} failed. Error: {}", methodName, e.getMessage());
            throw e; 
            
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Method {} execution time: {} ms", methodName, executionTime);
        }
    }
}