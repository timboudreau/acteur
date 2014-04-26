package com.mastfrog.acteur.spi;

import com.mastfrog.acteur.Event;
import io.netty.channel.Channel;
import java.util.concurrent.CountDownLatch;

/**
 * SPI interface which the request handling infrastructure uses to dispatch
 * events to and otherwise manage the application.
 *
 * @author Tim Boudreau
 */
public interface ApplicationControl {

    void enableDefaultCorsHandling();

    CountDownLatch onEvent(final Event<?> event, final Channel channel);

    void internalOnError(Throwable err);
}
