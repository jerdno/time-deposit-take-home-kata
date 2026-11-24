package org.ikigaidigital.application.port.in;

/**
 * Another Balance Update changed a Time Deposit after this one loaded it. This Balance Update was rolled back as a
 * whole: no Balance changed.
 */
public class ConcurrentBalanceUpdateException extends RuntimeException {

    public ConcurrentBalanceUpdateException(Throwable cause) {
        super("Another Balance Update changed a Time Deposit first; this one was rolled back", cause);
    }
}
