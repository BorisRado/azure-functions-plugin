package com.kumuluz.ee.serverless.azf.error_handling;

import java.util.function.Consumer;

/**
 * @author Boris Radovic
 * @since 1.0.0
 */

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
