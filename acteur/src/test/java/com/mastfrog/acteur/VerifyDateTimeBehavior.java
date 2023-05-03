package com.mastfrog.acteur;

import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.util.time.TimeUtil;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class VerifyDateTimeBehavior {

    private static final String WHEN = "1973-09-25T08:10:30.511-05:00";

    @Test
    public void test() throws Throwable {
        assertTrue(true);

        ZonedDateTime dt = ZonedDateTime.parse(WHEN, DateTimeFormatter.ISO_DATE_TIME);
        ZonedDateTime zeroed = dt.with(ChronoField.MILLI_OF_SECOND, 0);

        ZonedDateTime x = zeroed;
        for (int i = 0; i < 10; i++) {
            CharSequence pd = Headers.DATE.toCharSequence(zeroed);
            x = Headers.DATE.convert(pd);
        }

        String val = Headers.DATE.toCharSequence(zeroed).toString();

        ZonedDateTime parsed = Headers.DATE.toValue(val);
        String val2 = Headers.DATE.toCharSequence(parsed).toString();

        assertEquals(val, val2);

        assertEquals(TimeUtil.toUnixTimestamp(zeroed), TimeUtil.toUnixTimestamp(parsed));

        assertFalse(zeroed.isBefore(parsed));
        assertFalse(dt.isAfter(dt));
        assertFalse(dt.isBefore(dt));

        assertFalse(zeroed.isAfter(parsed));

        ZonedDateTime withZulu = dt.withZoneSameInstant(ZoneId.of("GMT"));

        ZonedDateTime withEST = zeroed.withZoneSameInstant(ZoneId.of("America/New_York"));
//                zeroed.toDateTime(DateTimeZone.forID("EST"));

        assertEquals(TimeUtil.toUnixTimestamp(zeroed), TimeUtil.toUnixTimestamp(withEST));

        String ss = "Tue, 25 Sep 1973 13:10:30 EST";
        String ss1 = "Tue, 25 Sep 1973 13:10:30 -05:00";
        String ss2 = "Tue, 25 Sep 73 13:10:30 -05:00";

        ZonedDateTime ugh = Headers.DATE.toValue(ss);
        ZonedDateTime ugh1 = Headers.DATE.toValue(ss1);
        assertEquals(ugh.toInstant(), ugh1.toInstant());

        assertEquals(TimeUtil.toUnixTimestamp(ugh), TimeUtil.toUnixTimestamp(ugh1));
//        assertEquals(dt, parsed);

        OffsetDateTime.from(ugh).withOffsetSameInstant(ZoneOffset.ofHours(7));
        OffsetDateTime ugh3 = OffsetDateTime.from(ugh).withOffsetSameInstant(ZoneOffset.ofHours(7));

//                ugh.withZone(DateTimeZone.forOffsetHours(7));
        assertEquals(ugh.toEpochSecond(), ugh3.toEpochSecond());

        ZonedDateTime ugh4 = Headers.DATE.toValue(ss2);
        assertEquals(TimeUtil.toUnixTimestamp(ugh), TimeUtil.toUnixTimestamp(ugh4), ugh4.toString() + " expected " + ugh);

        assertEquals(ugh.toInstant(), ugh.withZoneSameInstant(ZoneId.of("GMT")).toInstant());
    }

    @Test
    public void testPatternFromGlob() {
        String pattern = PathPatterns.patternFromGlob("foo/?ar");
        Pattern p = Pattern.compile(pattern);
        assertTrue(p.matcher("foo/bar").find());
        assertTrue(p.matcher("foo/car").find());
        assertTrue(p.matcher("foo/war").find());
        assertFalse(p.matcher("foo/warg").find());
        p = Pattern.compile(pattern = PathPatterns.patternFromGlob("foo/*/bar/*/baz"));
        assertTrue(p.matcher("foo/goo/bar/moo/baz").find());
        assertTrue(p.matcher("/foo/goo/bar/moo/baz").find());
        assertTrue(p.matcher("/foo/saldfhjalsdfhasdhfj/bar/moo/baz").find());
        assertFalse(p.matcher("foo/goo/bar/moo/wuggles").find());
        assertFalse(p.matcher("/foo/goo/bar/moo/baz/wunk").find());
        assertFalse(p.matcher("/foo/foo/bar/").find());
        assertEquals(PathPatterns.patternFromGlob("/foo"), PathPatterns.patternFromGlob("foo"));
    }
}
