/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.acteur.mongo.reactive;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.name.Named;
import static com.mastfrog.acteur.mongo.reactive.ActeurMongoModule.JACKSON_BINDING_NAME;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.mongodb.reactive.MongoHarness;
import com.mastfrog.giulius.mongodb.reactive.util.Subscribers;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.IfBinaryAvailable;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.jackson.configuration.JacksonConfigurer;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.collections.StringObjectMap;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.netty.buffer.ByteBufAllocator;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import static java.util.Locale.UK;
import static java.util.Locale.US;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({LocaleSerializationTest.M.class, MongoHarness.Module.class})
@IfBinaryAvailable("mongod")
public class LocaleSerializationTest {

    @Inject
    ObjectMapper mapper;

    private Map<String, Object> toMap(Object o) throws JsonProcessingException, IOException {
        return mapper.readValue(mapper.writeValueAsBytes(o), new TypeReference<Map<String, Object>>() {
        });
    }

    @Test
    public void testBsonMapperSerializesLocaleCorrectly(@javax.inject.Named(JACKSON_BINDING_NAME) ObjectMapper m, Dependencies deps) throws Throwable {
        String s = m.writeValueAsString(map("loc").finallyTo(Locale.US));
        Map<String, Object> mm = m.readValue(s, StringObjectMap.class);
        assertEquals(s, "en-US", mm.get("loc"));
    }

    @Test
    public void testDefaultMapperSerializesLocaleCorrectly(ObjectMapper m) throws JsonProcessingException {
        String s = m.writeValueAsString(map("loc").finallyTo(Locale.US));
        Map<String, Object> mm = m.readValue(s, StringObjectMap.class);
        assertEquals(s, "en-US", mm.get("loc"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLocaleSerialization(@Named("locs") MongoCollection<LocThing> c,
            Subscribers subscribers) throws IOException, Throwable {
        // First, test that plain Jackson serialization works with JacksonModule. By default,
        // Jackson will serialize locale names using underscores, not dashes, to separate the
        // language and country codes.  This breaks cross-language portability.
        LocThing def = new LocThing();
        Map<String, Object> m = toMap(def);
        // {defaultLocale=en-US, text={en-US=hello, en-CA='ello, fr-FR=bonjour}}
        assertEquals("en-US", m.get("defaultLocale"));
        Map<String, Object> text = (Map<String, Object>) m.get("text");
        assertNotNull(m + "", text);
        assertTrue(text + "", text.containsKey("en-US"));
        assertTrue(text + "", text.containsKey("en-CA"));
        assertTrue(text + "", text.containsKey("fr-FR"));

        // Write one into mongodb
        SRC<Object> src = new SRC<>();
        c.insertOne(def).subscribe(subscribers.first(src));
        src.assertNotThrown();

        // Ensure that when encoded as string keys and values, we get
        // sane stuff using hyphens
        SRC<Document> dtc = new SRC<>();
        c.withDocumentClass(Document.class).find().subscribe(subscribers.first(dtc));
        Document d = dtc.assertNotThrown();
        text = new HashMap<>(d.get("text", Map.class));
        assertTrue(text + "", text.containsKey("en-US"));
        assertTrue(text + "", text.containsKey("en-CA"));
        assertTrue(text + "", text.containsKey("fr-FR"));

        // Make sure an identical object can really be deserialized
        SRC<LocThing> ltc = new SRC<>();
        c.find().subscribe(subscribers.first(ltc));

        LocThing lt2 = ltc.assertHasResult();
        assertEquals(def, lt2);

        // Ensure that we can also read an object using underscores
        Map<String, Object> legacy = map("en_US").to("hello").map("en_GB").to("greetings").build();

        Document leg = new Document("text", legacy).append("defaultLocale", "en_US");

        SRC<DeleteResult> del = new SRC<>();

        c.deleteOne(new Document(), new DeleteOptions()).subscribe(subscribers.first(del));
        del.assertNotThrown();

        SRC<Object> pt = new SRC<>();
        c.withDocumentClass(Document.class).insertOne(leg).subscribe(subscribers.first(pt));
        pt.assertNotThrown();

        c.withDocumentClass(Document.class).find().subscribe(subscribers.first(dtc));
        Document retrieved = dtc.assertHasResult();
        assertEquals("en_US", retrieved.get("defaultLocale", String.class));

        c.find().subscribe(subscribers.first(ltc));
        LocThing lt3 = ltc.assertHasResult();

        assertEquals(US, lt3.defaultLocale);
        assertEquals("greetings", lt3.text.get(UK));
        assertEquals("hello", lt3.text.get(US));
    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            ActeurMongoModule m = new ActeurMongoModule(new ReentrantScope())
                    .withCodec(ByteBufCodec.class)
                    .withJacksonConfigurer(JacksonConfigurer.localeConfigurer())
//                                        .withJacksonConfigurer(JacksonConfigurer.javaTimeConfigurer())
//                    .loadJacksonConfigurersFromMetaInfServices()
                    .bindCollection("locs", LocThing.class);
            
            bind(UncaughtExceptionHandler.class).toInstance((thread, thrown) -> {
                thrown.printStackTrace();
            });

            bind(ByteBufAllocator.class).toInstance(ByteBufAllocator.DEFAULT);
            install(new JacksonModule(true)
//                    .withConfigurer(JacksonConfigurer.localeConfigurer())
            );
            install(m);
        }
    }

    private static final class LT extends LinkedHashMap<Locale, String> {

        public LT() {

        }

        public LT(Locale loc, String what) {
            add(loc, what);
        }

        public LT add(Locale loc, String what) {
            this.put(loc, what);
            return this;
        }
    }

    private static final class LocThing {

        public Locale defaultLocale = Locale.US;
        public LT text = new LT(Locale.US, "hello").add(Locale.CANADA, "'ello").add(Locale.FRANCE, "bonjour");
        public ObjectId _id;

        public LocThing() {
            this._id = new ObjectId();
        }

        @JsonCreator
        public LocThing(@JsonProperty(value = "defaultLocale", required = true) Locale defaultLocale, @JsonProperty(value = "text", required = true) LT text, @JsonProperty("_id") ObjectId _id) {
            this.defaultLocale = defaultLocale;
            this.text = text;
            this._id = _id;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 59 * hash + Objects.hashCode(this.defaultLocale);
            hash = 59 * hash + Objects.hashCode(this.text);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final LocThing other = (LocThing) obj;
            if (!Objects.equals(this.defaultLocale, other.defaultLocale)) {
                return false;
            }
            return Objects.equals(this.text, other.text);
        }

        @Override
        public String toString() {
            return "LocThing{" + "defaultLocale=" + defaultLocale + ", text=" + text + '}';
        }
    }
}
