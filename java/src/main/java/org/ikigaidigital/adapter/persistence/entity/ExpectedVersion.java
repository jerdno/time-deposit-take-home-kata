package org.ikigaidigital.adapter.persistence.entity;

/** The {@code version} of a {@link VersionedEntity} that a change to it was computed from. */
public record ExpectedVersion(long value) {}
