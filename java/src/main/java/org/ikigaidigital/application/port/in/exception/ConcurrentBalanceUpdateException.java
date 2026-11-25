package org.ikigaidigital.application.port.in.exception;

/**
 * Another Balance Update changed a Time Deposit after this one loaded it. This Balance Update was rolled back as a
 * whole: no Balance changed. Its message is written for the API client.
 */
public class ConcurrentBalanceUpdateException extends RuntimeException {

    public ConcurrentBalanceUpdateException(Throwable cause) {
        super(
                "A concurrent Balance Update changed a Time Deposit first. This one was rolled back and applied no"
                        + " interest.",
                cause);
    }
}
