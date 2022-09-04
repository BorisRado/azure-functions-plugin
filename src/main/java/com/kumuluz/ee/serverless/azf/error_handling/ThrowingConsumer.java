package com.kumuluz.ee.serverless.azf.error_handling;

/**
 * @author Boris Radovic
 * @since 1.0.0
 */

@FunctionalInterface
public interface ThrowingConsumer<T, E extends Exception> {
    void accept(T t) throws E;
}