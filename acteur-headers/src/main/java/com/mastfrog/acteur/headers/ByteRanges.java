/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.acteur.headers;

import com.mastfrog.acteur.headers.Range;
import com.mastfrog.util.collections.CollectionUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of byte-range headers as described in
 * <a href="http://greenbytes.de/tech/webdav/draft-ietf-httpbis-p5-range-latest.html#range.units">this
 * spec</a>.
 *
 * @author Tim Boudreau
 */
public final class ByteRanges implements Iterable<Range> {

    private final boolean valid;
    public static final Pattern RANGE_PATTERN = Pattern.compile("^(\\d+)-(\\d+)");
    public static final Pattern START_RANGE_PATTERN = Pattern.compile("^(\\d+)-");
    public static final Pattern END_RANGE_PATTERN = Pattern.compile("^-(\\d+)");
    private final Range[] ranges;

    private ByteRanges(List<Range> ranges) {
        this.valid = true;
        this.ranges = ranges.toArray(new Range[ranges.size()]);
    }

    public ByteRanges(String rangeHeader) {
        boolean valid = true;
        List<Range> items = new ArrayList<>(4);
        if (!rangeHeader.startsWith("bytes=")) {
            valid = false;
        } else {
            String rangeInfo = rangeHeader.substring("bytes=".length()).trim();
            if (rangeInfo.length() == 0) {
                valid = false;
            } else {
                String[] ranges = rangeInfo.split(",");
                for (String range : ranges) {
                    range = range.trim();
                    Matcher m = RANGE_PATTERN.matcher(range);
                    if (m.find()) {
                        try {
                            long start = Long.parseLong(m.group(1));
                            long end = Long.parseLong(m.group(2));
                            if (start < 0 || end < 0) {
                                valid = false;
                                break;
                            }
                            if (end <= start) {
                                valid = false;
                                break;
                            }
                            items.add(new RangeImpl(start, end));
                        } catch (NumberFormatException nfe) {
                            valid = false;
                            break;
                        }
                    } else {
                        m = START_RANGE_PATTERN.matcher(range);
                        if (m.find()) {
                            try {
                                long val = Long.parseLong(m.group(1));
                                if (val == -1) {
                                    valid = false;
                                    break;
                                }
                                items.add(new StartRange(val));
                            } catch (NumberFormatException e) {
                                valid = false;
                                break;
                            }
                        } else {
                            m = END_RANGE_PATTERN.matcher(range);
                            if (m.find()) {
                                try {
                                    long val = Long.parseLong(m.group(1));
                                    if (val < 0) {
                                        valid = false;
                                        break;
                                    }
                                    items.add(new EndRange(val));
                                } catch (NumberFormatException nfe) {
                                    valid = false;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        ranges = items.toArray(new Range[items.size()]);
        this.valid = valid;
    }

    public int size() {
        return ranges.length;
    }

    public Range first() {
        return ranges.length > 0 ? ranges[0] : null;
    }

    public Range get(int which) {
        return ranges[which];
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public Iterator<Range> iterator() {
        return CollectionUtils.toIterator(ranges);
    }

    public String toString() {
        StringBuilder result = new StringBuilder("bytes=");
        for (int i = 0; i < ranges.length; i++) {
            result.append(ranges[i]);
            if (i < ranges.length - 1) {
                result.append(',');
            }
        }
        return result.toString();
    }

    public boolean equals(Object o) {
        return o instanceof ByteRanges && o.toString().equals(toString());
    }

    public int hashCode() {
        return valid ? 1 : -1 + (7 * Arrays.hashCode(ranges));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<Range> ranges = new ArrayList<>(4);

        private Builder() {

        }

        public final ByteRanges build() {
            return new ByteRanges(ranges);
        }

        public Builder add(Range range) {
            this.ranges.add(range);
            return this;
        }

        public Builder add(long start, long end) {
            if (end <= start) {
                throw new IllegalArgumentException("start=" + start + ", end=" + end);
            }
            ranges.add(new RangeImpl(start, end));
            return this;
        }

        public Builder addStartpoint(long start) {
            if (start < 0) {
                throw new IllegalArgumentException("Negative start " + start);
            }
            ranges.add(new StartRange(start));
            return this;
        }

        public Builder addFromEnd(long subtract) {
            if (subtract < 0) {
                throw new IllegalArgumentException("Negative subtract: " + subtract);
            }
            ranges.add(new EndRange(subtract));
            return this;
        }
    }

    private static final class RangeImpl implements Range {

        final long start;
        final long end;

        public RangeImpl(long start, long end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public long start(long max) {
            if (start > max) {
                return -1;
            }
            return start;
        }

        @Override
        public long end(long max) {
            if (end > max) {
                return -1;
            }
            return end;
        }

        public String toString() {
            return start + "-" + end;
        }

        @Override
        public BoundedRange toBoundedRange(long max) {
            return new BoundedRange(start(max), end(max), max);
        }
    }

    private static final class EndRange implements Range {

        final long endOffset;

        public EndRange(long endOffset) {
            this.endOffset = endOffset;
        }

        @Override
        public long start(long max) {
            long result = max - endOffset;
            if (result < 0) {
                return -1;
            }
            return result;
        }

        @Override
        public long end(long max) {
            if (endOffset > max) {
                return -1;
            }
            return max;
        }

        public String toString() {
            return "-" + endOffset;
        }

        @Override
        public BoundedRange toBoundedRange(long max) {
            return new BoundedRange(start(max), end(max), max);
        }
    }

    public static final class StartRange implements Range {

        final long startpoint;

        public StartRange(long startpoint) {
            this.startpoint = startpoint;
        }

        @Override
        public long start(long max) {
            if (startpoint > max) {
                return -1;
            }
            return startpoint;
        }

        @Override
        public long end(long max) {
            return max;
        }

        public String toString() {
            return startpoint + "-";
        }

        @Override
        public BoundedRange toBoundedRange(long max) {
            return new BoundedRange(start(max), end(max), max);
        }
    }
}
