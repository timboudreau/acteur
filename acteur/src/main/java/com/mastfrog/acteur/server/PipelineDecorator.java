/*
 * The MIT License
 *
 * Copyright 2015 tim.
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
package com.mastfrog.acteur.server;

import com.google.inject.ImplementedBy;
import io.netty.channel.ChannelPipeline;

/**
 * Allows applications to inject encoders or decoders into the Netty channel
 * pipeline. This can be used to, for instance, replace the default
 * HttpObjectAggregator with one that write chunks to a file.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultPipelineDecorator.class)
public interface PipelineDecorator {

    /**
     * Name of the pipeline's HttpObjectDecoder
     */
    String DECODER = "decoder";
    /**
     * Name of the pipeline's HttpObjectAggregator
     */
    String AGGREGATOR = "aggregator";
    /**
     * Name of the pipeline's HttpObjectEncoder
     */
    String ENCODER = "encoder";
    /**
     * Name of the pipeline's compressor, if any
     */
    String COMPRESSOR = "deflater";
    /**
     * Name of the pipeline's dispatch handler that invokes acteurs
     */
    String HANDLER = "handler";

    /**
     * Name of the handler for &#064; early pages/acteurs.  Only
     * present if the application has some.
     */
    String PRE_CONTENT_PAGE_HANDLER = "early";

    String SSL_HANDLER = "ssl";

    default void onBeforeInstallSslHandler(ChannelPipeline pipeline) {
        // do nothing
    }
    /**
     * Called when the pipeline is first fetched/created, before
     * adding anything
     * @param pipeline The pipeline
     */
    void onCreatePipeline(ChannelPipeline pipeline);

    /**
     * Called after all the standard handlers have been added to the
     * pipeline.  You can replace handlers or add additional ones here.
     *
     * @param pipeline The pipeline
     */
    void onPipelineInitialized(ChannelPipeline pipeline);
}
