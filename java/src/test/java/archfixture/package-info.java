/**
 * Fixtures for {@code org.ikigaidigital.architecture.HexagonalArchitectureRulesTest}: small class sets laid out like
 * the real packages, that the hexagonal rules must reject ({@code violating}) or accept ({@code compliant}).
 *
 * <p>They live outside {@code org.ikigaidigital} on purpose. Spring's component and entity scanning covers that package
 * in tests, so a fixture annotated with {@code @Component} or {@code @Entity} there would end up in every Spring test
 * context.
 */
package archfixture;
