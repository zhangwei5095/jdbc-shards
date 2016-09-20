/*
 * Copyright 2014-2015 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wplatform.ddal.value;

import java.text.CollationKey;
import java.text.Collator;

import com.wplatform.ddal.engine.SysProperties;
import com.wplatform.ddal.message.DbException;
import com.wplatform.ddal.util.SmallLRUCache;

/**
 * The default implementation of CompareMode. It uses java.text.Collator.
 */
public class CompareModeDefault extends CompareMode {

    private final Collator collator;
    private final SmallLRUCache<String, CollationKey> collationKeys;

    protected CompareModeDefault(String name, int strength,
                                 boolean binaryUnsigned) {
        super(name, strength, binaryUnsigned);
        collator = CompareMode.getCollator(name);
        if (collator == null) {
            throw DbException.throwInternalError(name);
        }
        collator.setStrength(strength);
        int cacheSize = SysProperties.COLLATOR_CACHE_SIZE;
        if (cacheSize != 0) {
            collationKeys = SmallLRUCache.newInstance(cacheSize);
        } else {
            collationKeys = null;
        }
    }

    @Override
    public int compareString(String a, String b, boolean ignoreCase) {
        if (ignoreCase) {
            // this is locale sensitive
            a = a.toUpperCase();
            b = b.toUpperCase();
        }
        int comp;
        if (collationKeys != null) {
            CollationKey aKey = getKey(a);
            CollationKey bKey = getKey(b);
            comp = aKey.compareTo(bKey);
        } else {
            comp = collator.compare(a, b);
        }
        return comp;
    }

    @Override
    public boolean equalsChars(String a, int ai, String b, int bi,
                               boolean ignoreCase) {
        return compareString(a.substring(ai, ai + 1), b.substring(bi, bi + 1),
                ignoreCase) == 0;
    }

    private CollationKey getKey(String a) {
        synchronized (collationKeys) {
            CollationKey key = collationKeys.get(a);
            if (key == null) {
                key = collator.getCollationKey(a);
                collationKeys.put(a, key);
            }
            return key;
        }
    }

}
