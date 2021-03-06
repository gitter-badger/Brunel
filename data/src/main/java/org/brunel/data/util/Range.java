/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brunel.data.util;

import org.brunel.data.Data;
import org.brunel.translator.JSTranslation;

import java.util.Date;

public class Range implements Comparable<Range> {

    @JSTranslation(ignore = true)
    public static Range make(Double low, Double high) {
        return make(low, high, null);
    }

    public static Range make(Double low, Double high, DateFormat dateFormat) {
        return dateFormat == null ? makeNumeric(low, high, false) : makeDate(low, high, false, dateFormat);
    }

    public static Range makeNumeric(double low, double high, boolean nameAtMid) {
        double mid = (high + low) / 2;
        String name = nameAtMid ? Data.formatNumeric(mid, true)
                : Data.formatNumeric(low, true) + "\u2026" + Data.formatNumeric(high, true);
        return new Range(low, high, mid, name);
    }

    public static Range makeDate(double low, double high, boolean nameAtMid, DateFormat df) {
        Date lowDate = Data.asDate(low);
        Date highDate = Data.asDate(high);
        Date midDate = Data.asDate((high + low) / 2);
        String name = nameAtMid ? df.format(midDate) : df.format(lowDate) + "\u2026" + df.format(highDate);
        return new Range(lowDate, highDate, midDate, name);
    }

    public final Object high;
    public final Object mid;
    public final Object low;

    private final String name;

    private Range(Object low, Object high, Object mid, String name) {
        this.low = low;
        this.high = high;
        this.mid = mid;
        this.name = name;
    }

    public int compareTo(Range o) {
        return Data.compare(asNumeric(), o.asNumeric());
    }

    public Double asNumeric() {
        return (Data.asNumeric(low) + Data.asNumeric(high)) / 2.0;
    }

    private double extent() {
        return Data.asNumeric(high) - Data.asNumeric(low);
    }

    @JSTranslation(js = {"return this.name;"})
    public int hashCode() {
        return low.hashCode() + 31 * high.hashCode();
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof Range && ((Range) obj).low == low && ((Range) obj).high == high;
    }

    public String toString() {
        return name;
    }

}
