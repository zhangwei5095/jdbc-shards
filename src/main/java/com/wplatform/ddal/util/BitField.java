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
package com.wplatform.ddal.util;

/**
 * A list of bits.
 */
public final class BitField {

    private static final int ADDRESS_BITS = 6;
    private static final int BITS = 64;
    private static final int ADDRESS_MASK = BITS - 1;
    private long[] data;
    private int maxLength;

    public BitField() {
        this(64);
    }

    public BitField(int capacity) {
        data = new long[capacity >>> 3];
    }

    private static long getBitMask(int i) {
        return 1L << (i & ADDRESS_MASK);
    }

    /**
     * Get the index of the next bit that is not set.
     *
     * @param fromIndex where to start searching
     * @return the index of the next disabled bit
     */
    public int nextClearBit(int fromIndex) {
        int i = fromIndex >> ADDRESS_BITS;
        int max = data.length;
        for (; i < max; i++) {
            if (data[i] == -1) {
                continue;
            }
            int j = Math.max(fromIndex, i << ADDRESS_BITS);
            for (int end = j + 64; j < end; j++) {
                if (!get(j)) {
                    return j;
                }
            }
        }
        return max << ADDRESS_BITS;
    }

    /**
     * Get the bit at the given index.
     *
     * @param i the index
     * @return true if the bit is enabled
     */
    public boolean get(int i) {
        int addr = i >> ADDRESS_BITS;
        if (addr >= data.length) {
            return false;
        }
        return (data[addr] & getBitMask(i)) != 0;
    }

    /**
     * Get the next 8 bits at the given index.
     * The index must be a multiple of 8.
     *
     * @param i the index
     * @return the next 8 bits
     */
    public int getByte(int i) {
        int addr = i >> ADDRESS_BITS;
        if (addr >= data.length) {
            return 0;
        }
        return (int) (data[addr] >>> (i & (7 << 3)) & 255);
    }

    /**
     * Combine the next 8 bits at the given index with OR.
     * The index must be a multiple of 8.
     *
     * @param i the index
     * @param x the next 8 bits (0 - 255)
     */
    public void setByte(int i, int x) {
        int addr = i >> ADDRESS_BITS;
        checkCapacity(addr);
        data[addr] |= ((long) x) << (i & (7 << 3));
        if (maxLength < i && x != 0) {
            maxLength = i + 7;
        }
    }

    /**
     * Set bit at the given index to 'true'.
     *
     * @param i the index
     */
    public void set(int i) {
        int addr = i >> ADDRESS_BITS;
        checkCapacity(addr);
        data[addr] |= getBitMask(i);
        if (maxLength < i) {
            maxLength = i;
        }
    }

    /**
     * Set bit at the given index to 'false'.
     *
     * @param i the index
     */
    public void clear(int i) {
        int addr = i >> ADDRESS_BITS;
        if (addr >= data.length) {
            return;
        }
        data[addr] &= ~getBitMask(i);
    }

    private void checkCapacity(int size) {
        if (size >= data.length) {
            expandCapacity(size);
        }
    }

    private void expandCapacity(int size) {
        while (size >= data.length) {
            int newSize = data.length == 0 ? 1 : data.length * 2;
            long[] d = new long[newSize];
            System.arraycopy(data, 0, d, 0, data.length);
            data = d;
        }
    }

    /**
     * Enable or disable a number of bits.
     *
     * @param fromIndex the index of the first bit to enable or disable
     * @param toIndex   one plus the index of the last bit to enable or disable
     * @param value     the new value
     */
    public void set(int fromIndex, int toIndex, boolean value) {
        // go backwards so that OutOfMemory happens
        // before some bytes are modified
        for (int i = toIndex - 1; i >= fromIndex; i--) {
            set(i, value);
        }
        if (value) {
            if (toIndex > maxLength) {
                maxLength = toIndex;
            }
        } else {
            if (toIndex >= maxLength) {
                maxLength = fromIndex;
            }
        }
    }

    private void set(int i, boolean value) {
        if (value) {
            set(i);
        } else {
            clear(i);
        }
    }

    /**
     * Get the index of the highest set bit plus one, or 0 if no bits are set.
     *
     * @return the length of the bit field
     */
    public int length() {
        int m = maxLength >> ADDRESS_BITS;
        while (m > 0 && data[m] == 0) {
            m--;
        }
        maxLength = (m << ADDRESS_BITS) +
                (64 - Long.numberOfLeadingZeros(data[m]));
        return maxLength;
    }

}
