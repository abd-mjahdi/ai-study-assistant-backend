package ma.ensa.aistudyassistant.model.enums;

/**
 * How much detail to include in a summary (prompt tuning only).
 */
public enum SummaryDepth {
    /** Short, high-level takeaways only */
    ESSENTIALS,
    /** Clear coverage without going deep */
    BALANCED,
    /** Rich detail, nuance, and connections where useful */
    THOROUGH
}
