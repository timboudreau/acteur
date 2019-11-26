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
package com.mastfrog.acteur.mongo.async;

import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.util.Base64;
import org.bson.AbstractBsonWriter;
import org.bson.BSONException;
import org.bson.BsonBinary;
import org.bson.BsonContextType;
import org.bson.BsonDbPointer;
import org.bson.BsonRegularExpression;
import org.bson.BsonTimestamp;
import org.bson.json.JsonWriterSettings;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
class PlainJsonWriter extends AbstractBsonWriter {

    private final CharsetEncoder enc = CharsetUtil.UTF_8.newEncoder();
    private final ByteBuf buf;
    private final byte[] TRUE = "true".getBytes(CharsetUtil.US_ASCII);
    private final byte[] FALSE = "false".getBytes(CharsetUtil.US_ASCII);
    private final JsonWriterSettings settings;

    public PlainJsonWriter(ByteBuf buf, JsonWriterSettings settings) {
        super(settings);
        this.settings = settings;
        this.buf = buf;
        setContext(new Context(null, BsonContextType.TOP_LEVEL, ""));
    }

    protected Context getContext() {
        return (Context) super.getContext();
    }

    @Override
    public void flush() {
        // do nothing
    }

    private void writeNameHelper(final String name) {
        switch (getContext().getContextType()) {
            case ARRAY:
                // don't write Array element names in JSON
                if (getContext().hasElements) {
                    rawWriteChar(',');
                }
                break;
            case DOCUMENT:
            case SCOPE_DOCUMENT:
                Checks.notNull("name", name);
                if (getContext().hasElements) {
                    rawWriteChar(',');
                }
                if (settings.isIndent()) {
                    rawWriteString(settings.getNewLineCharacters());
                    rawWriteString(getContext().indentation);
                } else {
                    rawWriteChar(' ');
                }
                rawWriteChar('"');
                _writeString(name);
                rawWriteChar('"');
                rawWriteString(" : ");
                break;
            case TOP_LEVEL:
                break;
            default:
                throw new BSONException("Invalid contextType.");
        }
        getContext().hasElements = true;
    }

    private void _writeString(String s) {
        for (char c : s.toCharArray()) {
            _writeChar(c);
        }
    }

    private void writeChar(char c) {
        _writeChar(c);
    }

    private void _writeChar(char c) {
        switch (c) {
            case '\\':
                buf.writeByte('\\').writeByte('\\');
                break;
            case '"':
                buf.writeByte('\\').writeByte('"');
                break;
            case '\n':
                buf.writeByte('\\').writeByte('n');
                break;
            case '\t':
                buf.writeByte('\\').writeByte('t');
                break;
            default:
                if (c <= 20 || c >= 128) {
                    buf.writeByte('\\').writeByte('u');
                    String s=Integer.toHexString(c);
                    for (int i = 0; i < 4-s.length(); i++) {
                        buf.writeByte('0');
                    }
                    buf.writeBytes(s.getBytes(CharsetUtil.US_ASCII));
//                    intString((int) c);
                } else {
                    //                        buf.writeChar((int) c);
                    rawWriteChar(c);
                }
        }
    }
    private final CharBuffer tmp = CharBuffer.allocate(1);

    private void rawWriteChar(char c) {
        if (c < 256) {
            buf.writeByte((byte) c);
        } else {
            tmp.put(0, c);
            tmp.rewind();
            tmp.limit(1);
            try {
                buf.writeBytes(enc.encode(tmp));
            } catch (CharacterCodingException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
    }

    private void rawWriteString(String s) {
        buf.writeBytes(s.getBytes(CharsetUtil.US_ASCII));
    }

    private void intString(int val) {
        try {
            char[] result = new char[]{'0', '0', '0', '0'};
            char[] s = Integer.toString(val).toCharArray();
            for (int i = 0; i < s.length; i++) {
                int index = (4 - s.length) + i;
                result[index] = s[i];
            }
            ByteBuffer buf = CharsetUtil.UTF_8.newEncoder().encode(CharBuffer.wrap(result));
            this.buf.writeBytes(buf);
        } catch (CharacterCodingException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    protected void doWriteStartDocument() {
        if (getState() == State.VALUE || getState() == State.SCOPE_DOCUMENT) {
            writeNameHelper(getName());
        }
        rawWriteChar('{');
        BsonContextType contextType = (getState() == State.SCOPE_DOCUMENT) ? BsonContextType.SCOPE_DOCUMENT : BsonContextType.DOCUMENT;
        setContext(new Context(getContext(), contextType, settings.getIndentCharacters()));
    }

    @Override
    protected void doWriteEndDocument() {
        if (settings.isIndent() && getContext().hasElements) {
            _writeString(settings.getNewLineCharacters());
            if (getContext().getParentContext() != null) {
                _writeString(getContext().getParentContext().indentation);
            }
            rawWriteChar('}');
        } else {
            rawWriteChar('}');
        }
        if (getContext().getContextType() == BsonContextType.SCOPE_DOCUMENT) {
            setContext(getContext().getParentContext());
            writeEndDocument();
        } else {
            setContext(getContext().getParentContext());
        }
    }

    @Override
    protected void doWriteStartArray() {
        writeNameHelper(getName());
        rawWriteChar('[');
        setContext(new Context(getContext(), BsonContextType.ARRAY, settings.getIndentCharacters()));
    }

    @Override
    protected void doWriteEndArray() {
        rawWriteChar(']');
        setContext(getContext().getParentContext());
    }

    @Override
    protected void doWriteBinaryData(BsonBinary bb) {
        writeNameHelper(getName());
        rawWriteChar('"');
//        _writeString(DatatypeConverter.printBase64Binary(bb.getData()));
        _writeString(Base64.getEncoder().encodeToString(bb.getData()));
        rawWriteChar('"');
    }

    @Override
    public void writeName(String name) {
        super.writeName(name);
    }

    @Override
    protected void doWriteBoolean(boolean bln) {
        writeNameHelper(getName());
        buf.writeBytes(bln ? TRUE : FALSE);
    }

    @Override
    protected void doWriteDateTime(long l) {
        writeNameHelper(getName());
        _writeString(Long.toString(l));
    }

    @Override
    protected void doWriteDBPointer(BsonDbPointer value) {
        writeStartDocument();
        writeString("$ref", value.getNamespace());
        writeObjectId("$id", value.getId());
        writeEndDocument();
    }

    @Override
    protected void doWriteDouble(double d) {
        writeNameHelper(getName());
        _writeString(Double.toString(d));
        setState(getNextState());
    }

    @Override
    protected void doWriteInt32(int i) {
        writeNameHelper(getName());
        _writeString(Integer.toString(i));
    }

    @Override
    protected void doWriteInt64(long l) {
        writeNameHelper(getName());
        _writeString(Long.toString(l));
    }

    @Override
    protected void doWriteJavaScript(String code) {
        writeStartDocument();
        writeString("$code", code);
        writeEndDocument();
    }

    @Override
    protected void doWriteJavaScriptWithScope(String code) {
        writeStartDocument();
        writeString("$code", code);
        writeName("$scope");
    }

    @Override
    protected void doWriteMaxKey() {
        // do nothing
    }

    @Override
    protected void doWriteMinKey() {
        // do nothing
    }

    @Override
    protected void doWriteNull() {
        writeNameHelper(getName());
        _writeString("null");
    }

    @Override
    protected void doWriteObjectId(ObjectId oi) {
        writeNameHelper(getName());
        setState(getNextState());
        rawWriteChar('"');
        rawWriteString(oi.toString());
        rawWriteChar('"');
    }

    @Override
    protected void doWriteRegularExpression(BsonRegularExpression bre) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void doWriteString(String string) {
        writeNameHelper(getName());
        setState(getNextState());
        rawWriteChar('"');
        _writeString(string);
        rawWriteChar('"');
    }

    @Override
    protected void doWriteSymbol(String string) {
        _writeString(string);
    }

    @Override
    protected void doWriteTimestamp(BsonTimestamp bt) {
        writeNameHelper(getName());
        _writeString(Long.toString(bt.getTime()));
    }

    @Override
    protected void doWriteUndefined() {
        writeNameHelper(getName());
        _writeString("null");
    }

    @Override
    protected void doWriteDecimal128(Decimal128 dcml) {
        writeNameHelper(getName());
        _writeString(dcml.toString());
    }

    class Context extends AbstractBsonWriter.Context {

        private final String indentation;
        private boolean hasElements;

        /**
         * Creates a new context.
         *
         * @param parentContext the parent context that can be used for going
         * back up to the parent level
         * @param contextType the type of this context
         * @param indentChars the String to use for indentation at this level.
         */
        public Context(final Context parentContext, final BsonContextType contextType, final String indentChars) {
            super(parentContext, contextType);
            this.indentation = (parentContext == null) ? indentChars : parentContext.indentation + indentChars;
        }

        @Override
        public Context getParentContext() {
            return (Context) super.getParentContext();
        }
    }

}
