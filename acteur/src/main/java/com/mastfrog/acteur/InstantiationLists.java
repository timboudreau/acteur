package com.mastfrog.acteur;

import com.mastfrog.giulius.Dependencies;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import java.util.List;

/**
 *
 * @author tim
 */
final class InstantiationLists {

    public <T> Iterable<T> iterable(final Dependencies deps, List<Object> obj, final Class<T> type) {
        return CollectionUtils.<T>toIterable(CollectionUtils.<Object, T>convertedIterator(new Converter<T, Object>() {

            @Override
            public Object unconvert(T r) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public T convert(Object t) {
                if (t instanceof Class<?>) {
                    return type.cast(deps.getInstance((Class<T>) t));
                } else {
                    return type.cast(t);
                }
            }
        }, obj.iterator()));
    }
}
