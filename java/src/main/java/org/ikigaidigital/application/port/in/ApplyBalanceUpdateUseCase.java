package org.ikigaidigital.application.port.in;

/** The Balance Update: adds one Monthly Interest to the Balance of every Time Deposit, all or nothing. */
public interface ApplyBalanceUpdateUseCase {

    /**
     * Runs one Balance Update in a transaction. It joins the caller's transaction when there is one, so a caller can
     * wrap it with work of its own.
     */
    void applyBalanceUpdate();
}
