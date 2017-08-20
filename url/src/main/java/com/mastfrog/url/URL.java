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
import org.netbeans.validation.api.Validating;
import com.mastfrog.util.Checks;
import com.mastfrog.util.NullArgumentException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.netbeans.validation.api.Problems;
import org.netbeans.validation.api.Severity;
import org.openide.util.NbBundle;

/**
 * Represents an internet URL.  Unlike <code>java.net.URL</code>, invalid
 * URLs can be constructed (see isValid() and getInvalidComponent() for
 * validation).
 * <p/>
 * Also, unlike <code>java.net.URL</code>, the equals() and hashCode()
 * methods of this class will not attempt to make network connections.
 * This class <i>does</i> attempt basic normalization of URLs - a URL
 * representing <code>http://FOO.com/bar.html?</code> is equal to a URL
 * representing <code>http://foo.com/bar.html</code>.
 * <p/>
 * To create instances, use either <code><a href="#parse()">URL.parse()</a></code>
 * or <code><a href="URLBuilder.html">URLBuilder</a></code>.
 *
 * @author Tim Boudreau
 */
public final class URL implements URLComponent, Validating, Comparable<URL> {
    private static final long serialVersionUID = 1L;
    private final Anchor anchor;
    private final Protocol protocol;
    private final Host host;
    private final Port port;
    private final Path path;
    private final Parameters parameters;
    private final String password;
    private final String userName;
    URL (String userName, String password, Protocol protocol, Host host, Port port, Path path, Parameters query, Anchor anchor) {
        this.userName = userName;
        this.password = password;
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.path = path;
        this.parameters = query;
        this.anchor = anchor;
    }

    public static URLBuilder builder() {
        return new URLBuilder();
    }

    public static URLBuilder builder(Protocol protocol) {
        Checks.notNull("protocol", protocol);
        return new URLBuilder(protocol);
    }

    public static URLBuilder builder(URL startWith) {
        Checks.notNull("startWith", startWith);
        return new URLBuilder(startWith);
    }


    /**
     * Parse a URL from a string.  Note that this method will not throw any
     * exception if the URL is invalid.  Call isValid() on the result if you
     * are parsing a URL you did not construct yourself.  getInvalidComponent().getName()
     * will indicate exactly what part is invalid.
     * <p/>
     * It is not guaranteed that all parts of the passed-in string will be
     * preserved in the returned URL if it is invalid.
     * @param url A URL string
     * @return A URL
     */
    public static URL parse (String url) {
        Checks.notNull("url", url);
        return new URLParser(url).getURL();
    }

    /**
     * Get this URL in its typical browser-based form, omitting any query
     * strings and username/password.  I.e., if you have a URL for
     * <code>http://username:password@foo.com/something?bar=baz#x, you get
     * back <code>http://foo.com/something</code>.
     * @return
     */
    public URL getBaseURL(boolean retainPort) {
        return new URL (null, null, protocol, host, retainPort ? port : null, path, null, null);
    }

    /**
     * Get the anchor, if any, of this URL, as in
     * <code>http://foo.com/index.html<b>#anchor</b></code>
     * @return An anchor or null
     */
    public Anchor getAnchor() {
        return anchor;
    }

    /**
     * Get the host portion, if any, of this URL, as in
     * <code>http://<b>foo.com</b>/index.html</code>
     * @return A host or null
     */
    public Host getHost() {
        return host;
    }

    /**
     * Get the path, if any, of this URL, as in
     * <code>http://foo.com:332/<b>stuff/index.html</b>?foo=bar;bar=baz;#anchor</code>
     * @return An anchor or null
     */
    public Path getPath() {
        return path;
    }

    /**
     * Get the port, if any, of this URL.  The port returned will either be the
     * explicit port in this URL, as in <code>http://foo.com:<b>332</b>/stuff/index.html</code>
     * or the default port from the protocol, if any.
     * @return A port or null
     */
    public Port getPort() {
        return port == null ? protocol == null ? null : protocol.getDefaultPort() : port;
    }

    /**
     * Get the protocol, if any, of this URL, as in
     * <code><b>http</b>://foo.com/stuff/index.html</code>.  If the protocol
     * is null, the URL is invalid.
     * 
     * @return A protocol, or null.
     */
    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Get the parameters of this query, if any, as in
     * <code>http://foo.com/stuff/index.html?<b>foo=bar;bar=baz&baz=boo</b>#anchor</code>
     * @return The parameters or null if empty or unspecified
     */
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Get the user-name portion of this URL, as in
     * <code>http://<b>tim</b>:password@foo.com/stuff/index.html</code>
     * @return The user name if specified, otherwise null
     */
    public URLComponent getUserName() {
        return userName == null ? null : new GenericUrlComponent(
                NbBundle.getMessage(URL.class, "user_name"), userName, true);
    }

    /**
     * Get the user-name portion of this URL, as in
     * <code>http://tim:<b>password</b>@foo.com/stuff/index.html</code>
     * @return The user name if specified, otherwise null
     */
    public URLComponent getPassword() {
        return password == null ? null : new GenericUrlComponent(
                NbBundle.getMessage(URL.class, "password"), password, true);
    }

    /**
     * Returns true if this protocol of this URL is a well-known protocol.
     * @return Whether or not the protocol is a supported one, such as
     * <code>http, https, ftp, file</code>
     */
    public boolean isKnownProtocol() {
        return protocol == null ? false : protocol.isKnownProtocol();
    }

    /**
     * Determine if this URL is inferred to refer to a file, not a folder.
     * If false, the path portion will include a trailing / character.
     * @return whether or not this is a file reference
     */
    public boolean isProbableFileReference() {
        return path == null ? false : path.isProbableFileReference();
    }

    /**
     * Determine if the domain is the same as the passed internet domain.
     * For example, "foo.com" and "www.foo.com" have the same domain.  
     * @param domain
     * @return True if the domain matches
     */
    public boolean isSameDomain (String domain) {
        return host == null ? false : host.isDomain(domain);
    }
    
    /**
     * Get an aggregate of the host and port.
     * @return A host and port
     */
    public HostAndPort getHostAndPort() {
        return new HostAndPort(getHost(), getPort());
    }

    /**
     * Append this URL to a StringBuilder, in normalized form.
     * @param sb A StringBuilder to append to
     */
    public void appendTo(StringBuilder sb) {
        Checks.notNull("sb", sb);
        appendIfNotNull (sb, protocol);
        if (protocol != null) {
            sb.append (URLBuilder.PROTOCOL_DELIMITER);
        }
        if (userName != null) {
            getUserName().appendTo(sb);
        }
        if (userName != null || password != null) {
            sb.append (URLBuilder.PASSWORD_DELIMITER);
        }
        if (password != null) {
            getPassword().appendTo(sb);
        }
        if (userName != null || password != null) {
            sb.append ('@');
        }
        if (host != null && host.length() == 0 && host.isLocalhost() && !Protocols.FILE.match(protocol)) {
            // XXX ipv6?
            sb.append ("127.0.0.1");
        } else {
            if (host != null) {
                Host hst = host.canonicalize();
                // Use brackets if we have an IP address and a port, per the spec
                if (hst.isIpv6() && (port != null && protocol != null && !port.equals(protocol.getDefaultPort()))) {
                    sb.append('[').append(hst).append(']');
                } else {
                    sb.append(hst);
                }
            }
        }
        if (port != null) {
            int val = port.intValue();
            if (protocol != null) {
                if (protocol.getDefaultPort() != null) {
                    int defVal = protocol.getDefaultPort().intValue();
                    if (defVal != val) {
                        sb.append(URLBuilder.PORT_DELIMITER);
                        port.appendTo(sb);
                    }
                }
            }
        }
        sb.append(URLBuilder.PATH_ELEMENT_DELIMITER);
        appendIfNotNull (sb, path);
        if (parameters != null) {
            parameters.appendTo(sb);
        }
        if (anchor != null) {
            anchor.appendTo(sb);
        }
    }
    
    public String getPathAndQuery() {
        StringBuilder sb = new StringBuilder();
        if (path != null) {
            sb.append('/');
            path.appendTo(sb);
        }
        if (anchor != null) {
            anchor.appendTo(sb);
        }
        if (this.parameters != null) {
            this.parameters.appendTo(sb);
        }
        return sb.toString();
    }

    /**
     * Returns true if the protocol is non-null, is a known protocol, and
     * the protocol is known to use SSL.
     * @return True if this is a secure URL.
     */
    public boolean isSecure() {
        return protocol != null && protocol.isSecure();
    }

    /**
     * Get the components of this URL.
     * @return The components
     */
    public URLComponent[] components() {
        List<URLComponent> comps = new ArrayList<URLComponent>(6);
        addIfNotNull (comps, protocol);
        addIfNotNull (comps, getUserName());
        addIfNotNull (comps, getPassword());
        addIfNotNull (comps, host);
        addIfNotNull (comps, port);
        addIfNotNull (comps, path);
        addIfNotNull (comps, parameters);
        addIfNotNull (comps, anchor);
        return comps.toArray(new URLComponent[comps.size()]);
    }

    /**
     * Get all components of this URL, drilling into nested components and
     * not including their parents where necessary (as in 
     * <a href="Host.html"><code>Host</code></a>, <a href="Path.html"><code>Path</code></a>
     * and <a href="Parameters.html"><code>Parameters</code></a>).
     * @return An array of all low-level components of this URL.
     */
    public URLComponent[] allComponents() {
        List<URLComponent> comps = new ArrayList<URLComponent>(6);
        addIfNotNull (comps, protocol == null ? Protocol.INVALID : protocol);
        if (host != null) {
            for (URLComponent c : host.getLabels()) {
                addIfNotNull (comps, c);
            }
        }
        addIfNotNull (comps, port);
        if (path != null) {
            for (URLComponent c : path.getElements()) {
                addIfNotNull (comps, c);
            }
        }
        if (parameters != null) {
            if (parameters instanceof ParsedParameters) {
                for (URLComponent c : ((ParsedParameters) parameters).getElements()) {
                    addIfNotNull (comps, c);
                }
            } else {
                addIfNotNull (comps, parameters);
            }
        }
        return comps.toArray(new URLComponent[comps.size()]);
    }

    /**
     * Get the first URLComponent which is not valid, if isValid() == false.
     * @return The invalid component, or null
     */
    public URLComponent getInvalidComponent() {
        for (URLComponent c : allComponents()) {
            if (!c.isValid()) {
                return c;
            }
        }
        return null;
    }

    /**
     * Determine if this URL is a legal URL according to the relevant RFCs.
     * Does <i>not</i> determine if this URL is reachable via a network, only
     * that its syntax is correct.
     * @return true if this URL is probably valid.
     */
    @Override
    public boolean isValid() {
        URLComponent[] comps = components();
        boolean result = comps.length > 0 && protocol != null && (!protocol.isNetworkProtocol() || host != null);
        if (result) {
            for (URLComponent c : comps) {
                if (c instanceof Host) {
                    Host h = (Host) c;
                    if (h != null && Protocols.FILE.match(getProtocol()) && "".equals(h.toString())) {
                        //allow "" to represent localhost in file urls
                        continue;
                    }
                }
                result = c.isValid();
                if (!result) {
                    break;
                }
            }
        }
        return result;
    }

    private static <T> void addIfNotNull (List<T> list, T obj) {
        if (obj != null) {
            list.add(obj);
        }
    }

    public String getComponentName() {
        return NbBundle.getMessage(URL.class, "url");
    }

    private boolean isHostOnlyURL() {
        if (protocol != null && host != null) {
            boolean hasAnchor = anchor != null;
            boolean hasParameters = parameters != null && parameters.toString().length() > 0;
            boolean hasPath = path != null && path.size() > 0;
            if (!hasPath && !hasAnchor && !hasParameters) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get this URL in normalized form as a string.
     * @return This URL as a string.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendTo (sb);
        if (sb.length() > 0 && isHostOnlyURL() && sb.charAt(sb.length() - 1) != '/') {
            //normalize all host-only URLs to have a trailing slash
            sb.append ('/');
        }
        return sb.toString();
    }

    public String toUnescapedForm() {
        return URLBuilder.unescape(toString());
    }

    /**
     * Create a version of this URL minus its anchor.
     * @return A version of this URL minus everything following the last #
     * character.
     */
    public URL stripAnchor() {
        return new URL(userName, password, protocol, host, port, path, parameters, null);
    }

    /**
     * Create a version of this URL minus its parameters.
     * @return A version of this URL minus everything following the last ? character.
     */
    public URL stripQuery() {
        return new URL(userName, password, protocol, host, port, path, null, anchor);
    }

    /**
     * Creates a version of this URL minus its username, password, query and
     * anchor.
     * @return A URL with the same protocol, host, port and path as this one.
     */
    public URL toSimpleURL() {
        return new URL(null, null, protocol, host, port, path, null, null);
    }
    
    public URI toURI() throws URISyntaxException {
        return new URI(toString());
    }
    
    public URL withParameter(String name, String value) {
        AbstractBuilder<ParametersElement, Parameters> b = ParsedParameters.builder();
        List<ParametersElement> els = new LinkedList<>();
        if (this.parameters != null) {
            for (URLComponent comp : this.parameters.getElements()) {
                if (comp instanceof ParametersElement) {
                    b.add((ParametersElement) comp);
                } else {
                    b.add(comp.toString());
                }
            }
        }
        b.add(new ParametersElement(name, value));
        
        URL url = new URL(null, null, protocol, host, port, path, b.create(), null);
        return url;
    }

    /**
     * Determine if this URL points to the same file on the same host,
     * ignoring username/password, anchor and parameters.
     *
     * @param other Another URL
     * @return true if the other URL points to the same file via the same
     * protocol
     */
    public boolean simpleEquals (URL other) {
        if (other == this) {
            return true;
        }
        return other != null && other.toSimpleURL().equals(toSimpleURL());
    }

    /**
     * Get a URL to the parent path of this URL, stripping out any parameters or
     * anchor.
     * @return A parent URL to this one, if the path is deeper than one element.
     * Returns a path to the host with no path if the path is exactly one element
     * deep.
     */
    public URL getParentURL() {
        Path p = getPath();
        p = p == null ? null : p.getParentPath();
        if (p != null) {
            return new URL(userName, password, protocol, host, port, p, null, null);
//        } else if (p.size() == 1) {
        } else {
            return new URL(userName, password, protocol, host, port, null, null, null);
        }
//        return null;
    }

    /**
     * Get a URL for a file on disk in file protocol.
     * @param file A file
     * @return A URL
     */
    public static URL fromFile (File file) {
        Checks.notNull("file", file);
        try {
            return fromJavaUrl(file.toURI().toURL());
        } catch (MalformedURLException ex) {
            String path = file.getAbsolutePath();
            path = path.replace(File.separatorChar, '/');
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            Protocol p = Protocols.FILE;
            Path pth = Path.parse(path);
            return new URL (null, null, p, null, null, pth, null, null);
        }
    }

    /**
     * Convert a java.net.URL to URL.
     * @param url A Java URL
     * @return A URL
     */
    public static URL fromJavaUrl (java.net.URL url) {
        Checks.notNull("url", url);
        return parse(url.toString());
    }

    private static void appendIfNotNull (StringBuilder sb, URLComponent c) {
        Checks.notNull("sb", sb);
        if (c != null) {
            c.appendTo(sb);
        }
    }

    /**
     * Determine if the passed object is a URL and is an exact match for this
     * URL.  Note that sometimes <code>simpleEquals(URL)</code> may be preferable,
     * as it will match URLs which point to the same document but may specify
     * a different user, password, parameters or anchor.
     * @param obj An object
     * @return Whether or not the object argument is equal to this object.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof URL)) {
            return false;
        }
        //This test is not always sufficient - for example,
        //file:// and file:///urls are equivalent, and so are
        //http://foo.com:80/ and http://foo.com
        if (toString().equals(obj.toString())) {
            return true;
        }
        final URL other = (URL) obj;
        boolean result = Objects.equals(protocol, other.protocol) && 
                Objects.equals(anchor, other.anchor) &&
                Objects.equals(host, other.host) &&
                Objects.equals(path, other.path) &&
                Objects.equals(parameters, other.parameters) &&
                Objects.equals(userName, other.userName) &&
                Objects.equals(password, other.password);
        if (!result && toString().equals(obj.toString())) {
            result = true;
        }
        return result;
//        URLComponent[] ac = components();
//        URLComponent[] bc = other.components();
//        if (ac.length != bc.length) {
//            return toString().equals(obj.toString());
//        }
//        for (int i= 0; i < ac.length; i++) {
//            if (!ac[i].equals(bc[i])) {
//                return false;
//            }
//        }
//        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 19 * hash + (this.anchor != null ? this.anchor.hashCode() : 0);
        hash = 19 * hash + (this.protocol != null ? this.protocol.hashCode() : 0);
        hash = 19 * hash + (this.host != null ? this.host.hashCode() : 0);
        hash = 19 * hash + (this.port != null ? this.port.hashCode() : 0);
        hash = 19 * hash + (this.path != null ? this.path.hashCode() : 0);
        hash = 19 * hash + (this.parameters != null ? this.parameters.hashCode() : 0);
        hash = 19 * hash + (this.password != null ? this.password.hashCode() : 0);
        hash = 19 * hash + (this.userName != null ? this.userName.hashCode() : 0);
        return hash;
    }

    public static Charset getURLEncoding() {
        return URLBuilder.ISO_LATIN;
    }

    public java.net.URL toJavaURL() throws MalformedURLException {
        // java.net.url does not encode underscores
        return new java.net.URL(toString().replaceAll("%5f", "_"));
    }

    @Override
    @SuppressWarnings("EmptyIfStmt")
    public Problems getProblems() {
        if (!isValid()) {
            Problems problems = new Problems();
            Host h = getHost();
            if (h != null && Protocols.FILE.match(getProtocol()) && "".equals(h.toString())) {
                //allow it - "" is a valid localhost in file protocol
            } else {
                if (h != null && !h.isValid()) {
                    try {
                        Problems p = h.getProblems();
                        if (p == null) {
                            p = new Problems();
                            p.append("Host '" + h + "' reports itself invalid but lists no problems");
                        }
                        problems.addAll (p);
                    } catch (NullArgumentException e) {
                        throw new IllegalStateException (h.toString() + " reports itself invalid but returns no problems", e);
                    }
                } else if (h == null) {
                    problems.append("Host not set: " + toString());
                }
            }
            Path p = getPath();
            if (p != null && !p.isValid()) {
                if (p.isIllegal()) {
                    problems.append (NbBundle.getMessage(URL.class, "RELATIVE_PATH_HAS_TO_MANY_BACKWARD_STEPS", p.toString()));
                } else {
                    problems.append (NbBundle.getMessage(URL.class, "PATH_CONTAINS_ILLEGAL_CHARACTERS", p.toString()));
                }
            }
            Port prt = getPort();
            if (prt != null) {
                if (!prt.isValid()) {
                    if (prt.isIllegalChars()) {
                        problems.append (NbBundle.getMessage(URL.class, "PORT_IS_NOT_A_NUMBER", prt.toString()));
                    } else {
                        problems.append (NbBundle.getMessage(URL.class, "PORT_OUT_OF_RANGE", prt.toString()));
                    }
                }
            }
            if (!problems.hasFatal()) {
                URLComponent comp = getInvalidComponent();
                if (comp != null) {
                    problems.append(NbBundle.getMessage (URL.class,
                        "BAD_URL_COMPONENT", comp.getComponentName()),
                        Severity.FATAL);
                }
            }
            return problems.hasFatal() ? problems : null;
        }
        return null;
    }

    @Override
    public int compareTo(URL o) {
        return toString().compareToIgnoreCase(o.toString());
    }
}
