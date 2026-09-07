package io.github.aminedelta.core_banking.aop;

import io.github.aminedelta.core_banking.dto.TransferResult;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;


@Aspect
@Component
@Slf4j // Gives you the 'log' object automatically
public class LoggingAspect {

    // This Pointcut looks for any method in your project tagged with @AuditLog
    @Around("@annotation(io.github.aminedelta.core_banking.aop.AuditLog)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        
        String methodName = joinPoint.getSignature().getName();
        Object[] arguments = joinPoint.getArgs();
        long startTime = System.currentTimeMillis();
        
        log.info("Executing {} fromAccountId={} toAccountId={} amount={}",
            methodName, arguments[0], arguments[1], arguments[2]);

        try {
            Object result = joinPoint.proceed();
            TransferResult transferResult = (TransferResult) result;
            log.info("Transfer succeeded transactionId={}", transferResult.transactionId());
            return result;
            
        } catch (Throwable e) {
            log.error("Method {} failed. Error: {}", methodName, e.getMessage());
            throw e; 
            
        } finally {
            // This runs NO MATTER WHAT - success or crash
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Method {} execution time: {} ms", methodName, executionTime);
        }
    }
}