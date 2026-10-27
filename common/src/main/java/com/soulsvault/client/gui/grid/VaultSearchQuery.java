package com.soulsvault.client.gui.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Casket search, parsed once per keystroke instead of per entry per frame.
 * <p>
 * Space-separated terms must all match; {@code |} separates alternatives. Prefixes: {@code @mod},
 * {@code #tag-or-tooltip}, {@code $tag}, {@code *registry:id}; a leading {@code -} negates a term.
 * Plain terms match the item name or registry path.
 */
public final class VaultSearchQuery {
    public static final VaultSearchQuery EMPTY = new VaultSearchQuery("", List.of());

    private enum Kind {
        NAME,
        MOD,
        TAG_OR_TOOLTIP,
        TAG,
        ID
    }

    private record Term(Kind kind, String text, boolean negated) {
        boolean test(VaultEntryInfo info) {
            boolean match = switch (this.kind) {
                case NAME -> info.lowerName.contains(this.text) || info.path.contains(this.text);
                case MOD -> info.namespace.contains(this.text);
                case TAG -> matchesTag(info);
                case TAG_OR_TOOLTIP -> matchesTag(info) || info.tooltip().contains(this.text);
                case ID -> info.registryId.contains(this.text);
            };
            return match != this.negated;
        }

        private boolean matchesTag(VaultEntryInfo info) {
            for (String tag : info.tags()) {
                if (tag.contains(this.text)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final String raw;
    private final List<List<Term>> alternatives;

    private VaultSearchQuery(String raw, List<List<Term>> alternatives) {
        this.raw = raw;
        this.alternatives = alternatives;
    }

    /**
     * Parses the search box text (case-insensitive); blank text gives {@link #EMPTY}.
     */
    public static VaultSearchQuery parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return EMPTY;
        }
        List<List<Term>> alternatives = new ArrayList<>();
        for (String group : trimmed.split("\\|")) {
            List<Term> terms = new ArrayList<>();
            for (String word : group.trim().split("\\s+")) {
                Term term = parseTerm(word);
                if (term != null) {
                    terms.add(term);
                }
            }
            if (!terms.isEmpty()) {
                alternatives.add(List.copyOf(terms));
            }
        }
        return alternatives.isEmpty() ? EMPTY : new VaultSearchQuery(trimmed, List.copyOf(alternatives));
    }

    private static Term parseTerm(String word) {
        boolean negated = word.startsWith("-") && word.length() > 1;
        String body = negated ? word.substring(1) : word;
        if (body.isEmpty()) {
            return null;
        }
        Kind kind = switch (body.charAt(0)) {
            case '@' -> Kind.MOD;
            case '#' -> Kind.TAG_OR_TOOLTIP;
            case '$' -> Kind.TAG;
            case '*' -> Kind.ID;
            default -> Kind.NAME;
        };
        String text = kind == Kind.NAME ? body : body.substring(1);
        return text.isEmpty() ? null : new Term(kind, text, negated);
    }

    public boolean isEmpty() {
        return this.alternatives.isEmpty();
    }

    /**
     * The normalised (trimmed, lower-case) query text, used to detect real changes.
     */
    public String raw() {
        return this.raw;
    }

    boolean test(VaultEntryInfo info) {
        if (isEmpty()) {
            return true;
        }
        for (List<Term> terms : this.alternatives) {
            boolean all = true;
            for (Term term : terms) {
                if (!term.test(info)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }
}
