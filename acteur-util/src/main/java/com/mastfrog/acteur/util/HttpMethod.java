package com.mastfrog.acteur.util;

import com.mastfrog.util.Strings;

/**
 *
 * @author Tim Boudreau
 */
public interface HttpMethod {

    String name();

    CharSequence toCharSequence();

    /**
     * Checks equivalency with other libraries or string's classes that
     * represent HTTP methods, using the string value (name if an enum,
     * otherwise toString()); can also be used passing a String or
     * CharSequence.
     *
     * @param o Some object that might represent this HTTP method
     * @return True if it appears to
     */
    default boolean is(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof HttpMethod) {
            CharSequence a = toCharSequence();
            CharSequence b = ((HttpMethod) o).toCharSequence();
            return Strings.charSequencesEqual(a, b, true);
        } else if (o instanceof Enum<?>) {
            Enum<?> e = (Enum<?>) o;
            return name().equals(e.name());
        } else if (o instanceof String) {
            return ((String) o).equalsIgnoreCase(name());
        } else if (o instanceof CharSequence) {
            return Strings.charSequencesEqual(toCharSequence(), (CharSequence) o, true);
        } else {
            return o.toString().equalsIgnoreCase(name());
        }
    }
}
