package awesome.console.util;

import com.intellij.openapi.diagnostic.ControlFlowException;

/**
 * Exception handling utilities for IntelliJ plugin development.
 * Handles control-flow exceptions that must never be logged.
 */
public class ExceptionHandling {

    /**
     * Rethrows control-flow exceptions that must not be logged.
     * <p>
     * Control-flow exceptions (e.g., CannotReadException) are used by IntelliJ to signal
     * cancellation and should never be logged. This method ensures they are rethrown
     * immediately without being suppressed. See [com.intellij.openapi.diagnostic.Logger#ensureNotControlFlow]
     *
     * @param e the exception to check and potentially rethrow
     */
    public static void rethrowIfExceptionMustNotBeLogged(Exception e) {
        if (e instanceof ControlFlowException) {
            com.intellij.util.ExceptionUtil.rethrow(e);
        }
    }
}
