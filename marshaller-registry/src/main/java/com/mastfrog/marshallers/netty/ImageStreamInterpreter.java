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
package com.mastfrog.marshallers.netty;

import com.mastfrog.marshallers.Marshaller;
import com.mastfrog.mime.MimeType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import java.awt.image.RenderedImage;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 *
 * @author Tim Boudreau
 */
final class ImageStreamInterpreter implements Marshaller<RenderedImage, ByteBuf> {

    @Override
    public RenderedImage read(ByteBuf data, Object[] hints) throws Exception {
        try (final ByteBufInputStream in = new ByteBufInputStream(data)) {
            return ImageIO.read(in);
        }
    }

    @Override
    public void write(RenderedImage obj, ByteBuf into, Object[] hints) throws Exception {
        String format = NettyContentMarshallers.findHint(String.class, hints, null);
        if (format == null) {
            MimeType mimeType = NettyContentMarshallers.findHint(MimeType.class, hints, null);
            if (mimeType != null && mimeType.toString().startsWith("image") && mimeType.secondaryType().isPresent()) {
                format = mimeType.secondaryType().map(CharSequence::toString).orElseThrow(Error::new); // won't happen
                List<String> formats = Arrays.asList(ImageIO.getWriterFormatNames());
                if ("jpeg".equals(format) || !formats.contains(format)) {
                    format = "jpg";
                }
            }
            if (format == null) {
                format = "jpg";
            }
        }
        try (final ByteBufOutputStream out = new ByteBufOutputStream(into)) {
            ImageIO.write((RenderedImage) obj, format, out);
        }
    }

}
