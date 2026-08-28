package io.github.aminedelta.core_banking.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // This sticky note can ONLY be placed on methods
@Retention(RetentionPolicy.RUNTIME) // Keep this note alive while the app is running
public @interface AuditLog {
}