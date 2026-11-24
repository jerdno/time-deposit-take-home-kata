package org.ikigaidigital.application.model;

import org.ikigaidigital.TimeDeposit;

/**
 * A Time Deposit together with the {@code version} it was loaded at. It travels from the load to the save of a Balance
 * Update, so the save can tell whether another transaction changed the Time Deposit in between. The domain {@code
 * TimeDeposit} has no version and must not change, so the version rides alongside it.
 */
public record VersionedTimeDeposit(TimeDeposit timeDeposit, long version) {}
