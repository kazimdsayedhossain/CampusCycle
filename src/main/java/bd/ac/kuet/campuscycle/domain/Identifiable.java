package bd.ac.kuet.campuscycle.domain;

/**
 * Common contract for any domain entity or model that possesses a unique identifier.
 */
@FunctionalInterface
public interface Identifiable {
    String id();
}
