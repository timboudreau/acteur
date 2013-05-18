/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.url;

import com.mastfrog.util.AbstractBuilder;
import com.mastfrog.util.Checks;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Factory class for constructing URL objects w/ validation.
 *
 * @author Tim Boudreau
 */
public final class URLBuilder extends AbstractBuilder<PathElement, URL> {
    static final char ANCHOR_DELIMITER = '#';
    static final char PASSWORD_DELIMITER = ':';
    static final String PATH_ELEMENT_DELIMITER = "/";
    static final char PORT_DELIMITER = ':';
    static final String PROTOCOL_DELIMITER = "://";
    static final char LABEL_DELIMITER = '.';
    static final char UNENCODABLE_CHARACTER = '?';
    static final char URL_ESCAPE_PREFIX = '%';
    static final char QUERY_PREFIX = '?';
    private Protocol protocol;
    private Host host;
    private int port = -1;
    private String userName;
    private String password;
    private Path path;
    private Parameters query;
    private Anchor anchor;
    private List<ParametersElement> queryElements;
    private List<Label> labels;

    static final Charset ISO_LATIN = Charset.forName("ISO-8859-1");
    private ParametersDelimiter delimiter;
    
    static CharsetEncoder isoEncoder() {
        return ISO_LATIN.newEncoder();
    }

    public URLBuilder(Protocol protocol) {
        this.protocol = protocol;
    }

    public URLBuilder() {
        this (Protocols.HTTP);
    }

    public URLBuilder (URL prototype) {
        Checks.notNull("prototype", prototype);
        protocol = prototype.getProtocol();
        host = prototype.getHost();
        if (prototype.getPort() == null) { //file protocol
            port = 1;
        } else {
            port = prototype.getPort().intValue();
        }
        userName = prototype.getUserName() == null ? null :
            prototype.getUserName().toString();
        password = prototype.getPassword() == null ? null :
            prototype.getPassword().toString();
        if (prototype.getParameters() != null) {
            Parameters p = prototype.getParameters();
            if (p instanceof ParsedParameters) {
                for (ParametersElement qe : ((ParsedParameters)p).getElements()) {
                    addQueryPair(qe);
                }
            } else {
                this.query = p;
            }
        }
        anchor = prototype.getAnchor();
        Path origPath = prototype.getPath();
        if (origPath != null) {
            int sz = origPath.size();
            for (int i=0; i < sz; i++) {
                addPathElement(origPath.getElement(i));
            }
        }
    }

    public URLBuilder addPathElement (PathElement element) {
        Checks.notNull("element", element);
        if (this.path != null) {
            throw new IllegalStateException ("Path explicitly set");
        }
        add (element);
        return this;
    }

    public URLBuilder addPathElement (String element) {
        Checks.notNull("element", element);
        if (this.path != null) {
            throw new IllegalStateException ("Path explicitly set");
        }
        add (element);
        return this;
    }

    public URLBuilder addDomain(Label domain) {
        Checks.notNull("domain", domain);
        if (this.host != null) {
            throw new IllegalStateException ("Host explicitly set");
        }
        if (this.labels == null) {
            this.labels = new LinkedList<>();
        }
        labels.add (domain);
        return this;
    }

    public URLBuilder addDomain (String domain) {
        Checks.notNull("domain", domain);
        return addDomain (new Label(domain));
    }

    public URLBuilder addQueryPair (String key, String value) {
        Checks.notNull("value", value);
        Checks.notNull("key", key);
        if (query != null) {
            throw new IllegalStateException ("Query explictly set");
        }
        if (queryElements == null) {
            queryElements = new LinkedList<>();
        }
        queryElements.add (new ParametersElement(key, value));
        return this;
    }

    public URLBuilder addQueryPair (ParametersElement element) {
        Checks.notNull("element", element);
        queryElements.add (element);
        return this;
    }

    public URLBuilder setQueryDelimiter (ParametersDelimiter delimiter) {
        Checks.notNull("delimiter", delimiter);
        this.delimiter = delimiter;
        return this;
    }

    public URLBuilder setProtocol (String protocol) {
        Checks.notNull("protocol", protocol);
        Protocol p = Protocols.forName(protocol);
        return setProtocol (p);
    }

    public URLBuilder setProtocol (Protocol protocol) {
        Checks.notNull("protocol", protocol);
        this.protocol = protocol;
        return this;
    }

    public URLBuilder setAnchor(Anchor anchor) {
        Checks.notNull("anchor", anchor);
        this.anchor = anchor;
        return this;
    }

    public URLBuilder setAnchor(String anchor) {
        Checks.notNull("anchor", anchor);
        return setAnchor(new Anchor(anchor));
    }

    public URLBuilder setHost(Host host) {
        Checks.notNull("host", host);
        this.host = host;
        return this;
    }

    public URLBuilder setPassword(String password) {
        Checks.notNull("password", password);
        this.password = password;
        return this;
    }

    public URLBuilder setPath(Path path) {
        Checks.notNull("path", path);
        this.path = path;
        return this;
    }

    public URLBuilder setPath(String path) {
        Checks.notNull("path", path);
        String[] els = path.split(URLBuilder.PATH_ELEMENT_DELIMITER);
        List<PathElement> l = new LinkedList<>();
        for (int i= 0; i < els.length; i++) {
            String el = els[i];
            if (i == els.length - 1 && path.trim().endsWith("/")) {
                l.add(new PathElement(el, true));
            } else {
                l.add(new PathElement(el, false));
            }
        }
        return setPath (new Path(l.toArray(new PathElement[l.size()])));
    }

    public URLBuilder setPort(int port) {
        Checks.nonNegative("port", port);
        this.port = port;
        return this;
    }

    public URLBuilder setQuery(Parameters query) {
        Checks.notNull("query", query);
        if (queryElements != null && !queryElements.isEmpty()) {
            throw new IllegalStateException ("Query elements set");
        }
        this.query = query;
        return this;
    }

    public URLBuilder setUserName(String userName) {
        Checks.notNull("userName", userName);
        this.userName = userName;
        return this;
    }

    public URLBuilder setHost (String host) {
        Checks.notNull("host", host);
        String[] labels = host.split("\\" + LABEL_DELIMITER);
        List<Label> lbls = new LinkedList<>();
        for (String label : labels) {
            lbls.add(new Label(label));
        }
        setHost (new Host(lbls.toArray(new Label[lbls.size()])));
        return this;
    }

    @Override
    public URLBuilder add(PathElement element) {
        if (element.toString().equals("")) {
            return this;
        }
        return (URLBuilder) super.add(element);
    }

    @Override
    protected URLBuilder addElement(PathElement element) {
        if (element.toString().equals("")) {
            return this;
        }
        return (URLBuilder) super.addElement(element);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append (protocol);
        sb.append (PROTOCOL_DELIMITER);
        if (userName != null) {
            append (sb, userName);
        }
        if (password != null) {
            sb.append (PASSWORD_DELIMITER);
            append (sb, password);
        }
        sb.append (host);
        if (port != -1 && (protocol == null || port != protocol.getDefaultPort().intValue())) {
            sb.append (PORT_DELIMITER);
            sb.append (port);
        }
        sb.append (PATH_ELEMENT_DELIMITER);
        if (path != null) {
            sb.append (path);
        }
        if (queryElements != null && !queryElements.isEmpty()) {
            ParsedParameters q = new ParsedParameters (queryElements.toArray(new ParametersElement[queryElements.size()]));
            sb.append (delimiter == null ? q.toString(delimiter) : q);
        }
        if (anchor != null) {
            sb.append (ANCHOR_DELIMITER);
            sb.append (anchor);
        }

        return sb.toString();
    }

    static String escape (String toEscape, char... skip) {
        Checks.notNull("toEscape", toEscape);
        StringBuilder sb = new StringBuilder(toEscape.length() * 2);
        append (sb, toEscape);
        return sb.toString();
    }

    static void append (StringBuilder sb, String toEscape, char... skip) {
        Checks.notNull("toEscape", toEscape);
        Checks.notNull("sb", sb);
        Arrays.sort(skip);
        for (char c : toEscape.toCharArray()) {
            if (skip.length > 0 && Arrays.binarySearch(skip, c) >= 0) {
                sb.append (c);
                continue;
            }
            if (!isoEncoder().canEncode(c)) {
                sb.append (UNENCODABLE_CHARACTER);
                continue;
            }
            if (isLetter(c) || isNumber(c) || c == '.') {
                sb.append (c);
                continue;
            }
            appendEscaped(c, sb);
        }
    }

    static boolean isEncodableInLatin1(char c) {
        return isoEncoder().canEncode(c);
    }

    static boolean isEncodableInLatin1(CharSequence seq) {
        return isoEncoder().canEncode(seq);
    }
    
    static boolean isLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    static boolean isNumber(char c) {
        return c >='0' && c <= '9';
    }

    static boolean isValidHostCharacter(char c) {
        return isLetter(c) || isNumber(c);
    }

    static boolean isReserved(char c) {
        switch (c) {
            case '$':
            case '&':
            case '+':
            case ',':
            case '/':
            case ':':
            case ';':
            case '=':
            case '?':
            case '@':
                return true;
            default :
                return false;
        }
    }

    static void appendEscaped (char c, StringBuilder to) {
        Checks.notNull("to", to);
        String hex = Integer.toHexString(c);
        to.append (URL_ESCAPE_PREFIX);
        if (hex.length() == 1) {
            to.append ('0');
        }
        to.append (hex);
    }

    static String unescape (String seq) {
        Checks.notNull("seq", seq);
        if (seq.indexOf('%') < 0) {
            return seq;
        }
        char[] chars = seq.toCharArray();
        StringBuilder sb = new StringBuilder(seq.length());
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == URL_ESCAPE_PREFIX && i < chars.length - 2 && isHexCharacter(chars[i + 1]) && isHexCharacter(chars[i + 2])) {
                String hex = new String(chars, i+1, 2);
                int codePoint = Integer.valueOf(hex, 16);
                c = (char) codePoint;
                sb.append (c);
                i+=2;
                continue;
            } else {
                sb.append (c);
            }
        }
        return sb.toString();
    }
    
    static boolean isHexCharacter(char c) {
        switch (c) {
            case 'a':
            case 'b':
            case 'c':
            case 'd':
            case 'e':
            case 'f':
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return true;
            default :
                return false;
        }
    }

    @Override
    public URL create() {
        Host h = host == null ? labels == null ? null : new Host(labels.toArray(new Label[labels.size()])) : host;
        return new URL(userName, password, protocol, h, getPort(), getPath(), getQuery(), anchor);
    }

    Port getPort() {
        if (port != -1) {
            return new Port(port);
        } else if (protocol != null) {
            return protocol.getDefaultPort();
        }
        return null;
    }

    Path getPath() {
        if (path != null) {
            return path;
        }
        if (size() == 0) {
            return null;
        }
        PathElement[] els = this.elements().toArray(new PathElement[size()]);
        return new Path(els);
    }

    Parameters getQuery() {
        if (query != null) {
            return query;
        }
        if (queryElements != null && !queryElements.isEmpty()) {
            return new ParsedParameters (queryElements.toArray(new ParametersElement[queryElements.size()]));
        }
        return null;
    }

    @Override
    protected PathElement createElement(String string) {
        Checks.notNull("string", string);
        while (string.startsWith("/") && string.length() != 0) {
            string = string.substring(1);
        }
        return new PathElement(string, false);
    }
}
