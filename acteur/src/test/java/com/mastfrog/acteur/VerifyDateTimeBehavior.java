package com.mastfrog.acteur;

import com.mastfrog.acteur.headers.Headers;
import java.util.regex.Pattern;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
public class VerifyDateTimeBehavior {

    private static final String WHEN = "1973-09-25T08:10:30.511-05:00";
    public static final DateTimeFormatter ISO2822DateFormat =
            new DateTimeFormatterBuilder().appendDayOfWeekShortText().appendLiteral(", ").appendDayOfMonth(2).appendLiteral(" ").appendMonthOfYearShortText().appendLiteral(" ").appendYear(4, 4).appendLiteral(" ").appendHourOfDay(2).appendLiteral(":").appendMinuteOfHour(2).appendLiteral(":").appendSecondOfMinute(2).appendTimeZoneId() //                .appendLiteral(" GMT")
            .toFormatter();

    @Test
    public void test() throws Throwable {
        assertTrue(true);

        DateTime dt = new DateTime(WHEN);

        DateTime zeroed = dt.withMillisOfSecond(0);

        System.err.println(dt);

        String val = Headers.DATE.toString(dt);

        System.err.println("HDATE " + val);

        DateTime parsed = Headers.DATE.toValue(val);
        String val2 = Headers.DATE.toString(parsed);

        System.err.println("HDATE " + val2);

        assertEquals(val, val2);

        assertEquals(zeroed.getMillis(), parsed.getMillis());

        assertFalse(zeroed.isBefore(parsed));
        assertFalse(dt.isAfter(dt));
        assertFalse(dt.isBefore(dt));

        assertFalse(zeroed.isAfter(parsed));

        DateTime withZulu = dt.withZone(DateTimeZone.UTC);

        System.err.println("WITH ZULU: " + Headers.DATE.toString(withZulu));

        DateTime withEST = zeroed.toDateTime(DateTimeZone.forID("EST"));

        System.err.println("EST " + withEST);
        assertEquals(zeroed.getMillis(), withEST.getMillis());

        System.err.println("WITH EST: " + Headers.DATE.toString(withEST));


        String ss = "Tue, 25 Sep 1973 13:10:30 EST";
        String ss1 = "Tue, 25 Sep 1973 13:10:30 -05:00";
        String ss2 = "Tue, 25 Sep 73 13:10:30 -05:00";

        DateTime ugh = Headers.DATE.toValue(ss);
        DateTime ugh1 = Headers.DATE.toValue(ss1);
        assertEquals(ugh, ugh1);

        System.err.println("UGH   " + ugh);
        System.err.println("UGH 1 " + ugh1);
        assertEquals(ugh.getMillis(), ugh1.getMillis());
//        assertEquals(dt, parsed);

        DateTime ugh3 = ugh.withZone(DateTimeZone.forOffsetHours(7));
        assertEquals(ugh.getMillis(), ugh3.getMillis());

        DateTime ugh4 = Headers.DATE.toValue(ss2);
        assertEquals(ugh4.toString() + " expected " + ugh.toString(), ugh.getMillis(), ugh4.getMillis());

//        DateTime ugh5 = ugh.toDateTime(DateTimeZone.forOffsetHours(7));
//        assertFalse(ugh3.getMillis() == ugh5.getMillis());
        
        assertEquals(ugh, ugh.toDateTimeISO());
    }
    
    @Test
    public void testPatternFromGlob() {
        String pattern = ActeurFactory.patternFromGlob("foo/?ar");
        Pattern p = Pattern.compile(pattern);
        assertTrue(p.matcher("foo/bar").find());
        assertTrue(p.matcher("foo/car").find());
        assertTrue(p.matcher("foo/war").find());
        assertFalse(p.matcher("foo/warg").find());
        p = Pattern.compile(pattern = ActeurFactory.patternFromGlob("foo/*/bar/*/baz"));
        assertTrue(p.matcher("foo/goo/bar/moo/baz").find());
        assertTrue(p.matcher("/foo/goo/bar/moo/baz").find());
        assertTrue(p.matcher("/foo/saldfhjalsdfhasdhfj/bar/moo/baz").find());
        assertFalse(p.matcher("foo/goo/bar/moo/wuggles").find());
        assertFalse(p.matcher("/foo/goo/bar/moo/baz/wunk").find());
        assertFalse(p.matcher("/foo/foo/bar/").find());
        assertEquals(ActeurFactory.patternFromGlob("/foo"), ActeurFactory.patternFromGlob("foo"));
    }
}
