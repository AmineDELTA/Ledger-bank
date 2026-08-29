package io.github.aminedelta.core_banking.aop;

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
        long startTime = System.currentTimeMillis();
        
        log.info("Executing {}...", methodName);

        try {
            Object result = joinPoint.proceed();
            log.info("Method {} completed successfully.", methodName);
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