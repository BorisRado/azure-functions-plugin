package com.kumuluz.ee.serverless.azf.error_handling;

import java.util.function.Consumer;

public class ExceptionHandling {

    public static <T> Consumer<T> throwingConsumerWrapper(
            ThrowingConsumer<T, Exception> throwingConsumer) {

        return i -> {
            try {
                throwingConsumer.accept(i);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
    }

}