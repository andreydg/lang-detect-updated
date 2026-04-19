package language.util;

/**
 * Immutable pair; use {@link #first()} and {@link #second()} accessors.
 */
public record Pair<A, B>(A first, B second) {}
