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

import com.mastfrog.util.Checks;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author tim
 */
final class URLParser {

    private final CharSequence url;
    private static final Pattern PROTOCOL_SPLIT = Pattern.compile("(.*?)://(.*)");
    private static final Pattern FILE_PROTOCOL_SPLIT = Pattern.compile("(.*?):[/{1}/{3}](.*)");
    private static final Pattern FILE_PROTOCOL_WITH_HOST = Pattern.compile("(.*?)://(.*?)/(.*)");
    private static final Pattern SLASH_SPLIT = Pattern.compile("(.*?)/(.*)");
    private static final Pattern PARAMS_SPLIT = Pattern.compile("(.*?)\\?(.*)");
    private static final Pattern ANCHOR_SPLIT = Pattern.compile("(.*)\\#(.*)");
    private static final Pattern USERINFO_SPLIT = Pattern.compile("(.*)\\@(.*)", Pattern.DOTALL);
    private static final Pattern USER_PASSWORD_SPLIT = Pattern.compile("(.*?)\\:(.*)");
    private static final Pattern HOST_PORT_SPLIT = Pattern.compile("(.*?)\\:(\\d*)");

    private static final Pattern PARAMETER_ELEMENT_SPLIT = Pattern.compile("(.*?)[\\;\\&$]");
    
    private static final Pattern IPV6_HOST_AND_PORT = Pattern.compile("^\\[([0-9A-Za-z\\:]+)\\]\\:(\\d+)$");

    URLParser(CharSequence url) {
        Checks.notNull("url", url);
        this.url = url;
    }

    public URL getURL() {
        String protocol = null;
        Matcher m = PROTOCOL_SPLIT.matcher(url);
        String remainder;
        String u = url.toString();
        String host = null;
        if (m.find()) {
            protocol = m.group(1);
            remainder = m.group(2);
        } else {
            remainder = u;
        }
        if (protocol != null && protocol.trim().length() == 0) {
            protocol = null;
        }
        boolean isFile = protocol == null && u.toLowerCase().startsWith("file:");
        if (isFile) {
            m = FILE_PROTOCOL_SPLIT.matcher(u);
            if (m.find()) {
                protocol = m.group(1);
                remainder = m.group(2);
                host = "";
            } else {
                m = FILE_PROTOCOL_WITH_HOST.matcher(u);
                protocol = m.group(1);
                host = m.group(2);
                remainder = m.group(3);
            }
        }
        String port = null;
        boolean isIpV6 = false;
        if (host == null) {
            m = SLASH_SPLIT.matcher(remainder);
            if (m.find()) {
                host = m.group(1);
                remainder = m.group(2);
            } else {
                host = remainder;
                remainder = null;
            }
        }

        String username = null;
        String password = null;
        Checks.notNull("host", host);
        m = USERINFO_SPLIT.matcher(host);
        String unpw = null;
        if (m.find()) {
            unpw = m.group(1);
            host = m.group(2);
        }
        if (unpw != null) {
            m = USER_PASSWORD_SPLIT.matcher(unpw);
            if (m.find()) {
                username = m.group(1);
                password = m.group(2);
            }
        }
        Matcher hm = IPV6_HOST_AND_PORT.matcher(host);
        if (hm.find()) {
            host = hm.group(1);
            port = hm.group(2);
            isIpV6 = true;
        } else {
            hm = Host.IPV6_REGEX.matcher(host);
            isIpV6 = hm.lookingAt();
        }
        if (port == null && !isIpV6) {
            m = HOST_PORT_SPLIT.matcher(host);
            if (m.lookingAt()) {
                host = m.group(1);
                if (m.groupCount() > 1) {
                    port = m.group(2);
                } else {
                    port = null;
                }
            }
        }
        if (remainder == null) {
            Protocol prot = protocol == null ? null : Protocols.forName(protocol);
            Port prt = port == null ? prot == null ? null : prot.getDefaultPort() : new Port(port);
            Host hst = host == null ? null : Host.parse(host);
            return new URL (username, password, prot, hst, prt, null, null, null);
        }

        String anchor = null;
        m = ANCHOR_SPLIT.matcher(remainder);
        if (m.find()) {
            anchor = m.group(2);
            remainder = m.group(1);
        }
        
        Parameters parameterSet = null;
        String path = null;
        String parameters = null;

        if (isFile) {
            path = remainder;
        } else {
            List<ParametersElement> params = new ArrayList<ParametersElement>();
            m = PARAMS_SPLIT.matcher(remainder);
            if (m.find()) {
                path = URLBuilder.unescape(m.group(1));
                parameters = m.group(2);
            } else {
                if (remainder.contains("=")) {
                    parameters = remainder;
                } else {
                    path = URLBuilder.unescape(remainder);
                }
            }
            ParametersDelimiter delim = ParametersDelimiter.AMPERSAND;
            if (parameters != null) {
                delim = processParameters(params, parameters);
            }
            parameterSet = params.isEmpty() ? parameters == null ? null : ParsedParameters.parse(parameters)
                    : new ParsedParameters(delim, params.toArray(new ParametersElement[params.size()]));
        }
        Port prt = port == null ? null : port.trim().length() == 0 ? null : new Port (port);
        Host hst = host == null ? null : Host.parse(host);
        if (host != null && !host.isEmpty()) {
            hst = hst.canonicalize();
        }
        Protocol proto = protocol == null ? null : Protocols.forName(protocol);
        Path pth = path == null ? null : Path.parse(path);
        if (pth != null) {
            pth = pth.normalize();
        }
        Anchor anch = anchor == null ? null : new Anchor(anchor);
        URL result = new URL(username, password, proto, hst, prt, pth, parameterSet, anch);
        return result;
    }

    private static ParametersDelimiter processParameters(List<ParametersElement> l, String parameters) {
        Matcher m = PARAMETER_ELEMENT_SPLIT.matcher(parameters);
        boolean match = m.find();
        ParametersDelimiter result = parameters.indexOf(ParametersDelimiter.AMPERSAND.charValue()) >= 0
                ? ParametersDelimiter.AMPERSAND : ParametersDelimiter.SEMICOLON;
        if (match) {
            do {
                l.add(ParametersElement.parse(m.group(1)));
                int end = m.end(1);
                match = m.find();
                if (!match && end != parameters.length() - 1) {
                    String rem = parameters.substring(end + 1);
                    l.add(ParametersElement.parse(rem));
                }
            } while (match);
        }
        for (int i = parameters.length() - 1; i > 0; i--) {
            if (result.charValue() == parameters.charAt(i)) {
                l.add(ParametersElement.EMPTY);
            } else {
                break;
            }
        }
        return result;
    }
}
