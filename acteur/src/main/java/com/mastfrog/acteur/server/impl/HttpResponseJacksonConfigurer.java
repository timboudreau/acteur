package com.mastfrog.acteur.server.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mastfrog.jackson.JacksonConfigurer;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service=JacksonConfigurer.class)
public class HttpResponseJacksonConfigurer implements JacksonConfigurer {

    @Override
    public ObjectMapper configure(ObjectMapper mapper) {
        SimpleModule sm = new SimpleModule("responseStatus", 
                new Version(1, 0, 0, null, "com.mastfrog", "acteur"));
        sm.addSerializer(new HttpResponseStatusJsonSerializer());
        return mapper;
    }

    private static class HttpResponseStatusJsonSerializer extends JsonSerializer<HttpResponseStatus> {

        @Override
        public Class<HttpResponseStatus> handledType() {
            return HttpResponseStatus.class;
        }

        @Override
        public void serialize(HttpResponseStatus t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            jg.writeString(t.code() + " " + t.reasonPhrase());
        }
    }
}
