package com.kumuluz.ee.serverless.azf.error_handling;

@FunctionalInterface
public interface ThrowingConsumer<T, E extends Exception> {
    void accept(T t) throws E;
}