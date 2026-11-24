package org.ikigaidigital.application.readmodel;

/**
 * Which page of Time Deposits to load.
 *
 * @param page zero-based page index
 * @param size number of Time Deposits per page
 */
public record PageQuery(int page, int size) {

    /** Number of Time Deposits before this page. A {@code long}, so a large page index cannot overflow. */
    public long offset() {
        return (long) page * size;
    }
}
