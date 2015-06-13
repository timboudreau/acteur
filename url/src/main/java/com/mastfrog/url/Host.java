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

import org.netbeans.validation.api.Validating;
import com.mastfrog.util.AbstractBuilder;
import com.mastfrog.util.Checks;
import com.mastfrog.util.collections.CollectionUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import org.netbeans.validation.api.Problems;
import org.netbeans.validation.api.Validator;
import org.netbeans.validation.api.builtin.stringvalidation.StringValidators;
import org.openide.util.NbBundle;

/**
 * An internet host such as a host name or IP address. Validation is more
 * extensive than that done by java.net.URL, including checking name and label
 * lengths for spec compatibility (max 63 chars per label, 255 chars per host
 * name).
 *
 * @author Tim Boudreau
 */
public final class Host implements URLComponent, Validating, Iterable<Label> {

    static final Pattern IPV6_REGEX = Pattern.compile("(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))");

    private static final long serialVersionUID = 1L;
    final boolean ipv6;
    private final Label[] labels;

    public Host(boolean ipv6, Label... labels) {
        Checks.notNull("domains", labels);
        Checks.noNullElements("domains", labels);
        this.ipv6 = ipv6;
        this.labels = new Label[labels.length];
        System.arraycopy(labels, 0, this.labels, 0, labels.length);
    }

    public Host(Label... labels) {
        this(false, labels);
    }

    public int size() {
        return labels.length;
    }

    public Label getElement(int ix) {
        return labels[ix];
    }

    public boolean isDomain(String domain) {
        Checks.notNull("domain", domain);
        Host host = Host.parse(domain);
        boolean result = true;
        int labelCount = host.size() - 1;
        int mySize = size() - 1;
        if (labelCount < 0 || mySize < 0) {
            return false;
        }
        do {
            result &= host.getElement(labelCount).equals(getElement(mySize));
            if (!result) {
                break;
            }
            labelCount--;
            mySize--;
        } while (labelCount >= 0 && mySize >= 0);
        return result;
    }

    public static Host parse(String path) {
        if (IPV6_REGEX.matcher(path).matches() || path.startsWith("::")) {
            String[] parts = path.split(":");
            Label[] labels = new Label[parts.length];
            for (int i = 0; i < parts.length; i++) {
                labels[i] = new Label(parts[i]);
            }
            return new Host(true, labels);
        }
        String[] parts = path.split("\\" + URLBuilder.LABEL_DELIMITER);
        Label[] els = new Label[parts.length];
        for (int i = 0; i < parts.length; i++) {
            // PENDING:  Percent encode characters as UTF8, per
            // http://tools.ietf.org/html/rfc3986 and
            // http://tools.ietf.org/html/rfc3490
            els[i] = new Label(parts[i]);
        }
        return new Host(els);
    }

    public Label getTopLevelDomain() {
        if (isIpAddress()) {
            return null;
        }
        return labels.length > 1 ? labels[labels.length - 1] : null;
    }

    public Label getDomain() {
        if (isIpAddress()) {
            return null;
        }
        return labels.length > 1 ? labels[labels.length - 2] : null;
    }

    public Label[] getLabels() {
        Label[] result = new Label[labels.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = labels[labels.length - (1 + i)];
        }
        return result;
    }

    public Host getParentDomain() {
        if (labels.length > 1) {
            Label[] l = new Label[labels.length - 1];
            System.arraycopy(labels, 1, l, 0, l.length);
            return new Host(l);
        } else {
            return null;
        }
    }

    public boolean isIpAddress() {
        boolean result = labels.length > 0;
        if (result) {
            boolean ipv6Found = false;
            for (Label label : labels) {
                boolean isIpv6 = label.isValidIpV6Component();
                boolean isIpv4 = label.isValidIpV4Component();
                ipv6Found |= isIpv6;
                result = isIpv4 || isIpv6;
                if (!result) {
                    break;
                }
            }
            if (result && ipv6Found && labels.length > 8) {
                return false;
            } else if (!ipv6 && (result && labels.length != 4)) {
                return false;
            }
        }
        return result;
    }

    public boolean isIpV4Address() {
        boolean result = labels.length == 4;
        if (result) {
            for (Label label : labels) {
                result = label.isValidIpV4Component();
                if (!result) {
                    break;
                }
            }
        }
        return result;
    }

    public boolean isIpV6Address() {
        boolean result = ipv6 && labels.length > 0 && labels.length <= 8;
        if (result) {
            for (Label label : labels) {
                result &= label.isValidIpV6Component();
                if (!result) {
                    break;
                }
            }
        }
        return result;
    }

    public int length() {
        int len = 0;
        for (Label dom : labels) {
            boolean hadContents = len > 0;
            if (hadContents) {
                len++;
            }
            len += dom.length();
        }
        return len;
    }

    @Override
    public boolean isValid() {
        if (isLocalhost() && !"".equals(toString())) {
            return true;
        }
        if (isIpV6Address()) {
            return true;
        }
        boolean ip = isIpAddress();
        boolean result = ip || (getTopLevelDomain() != null && getDomain() != null);
        if (result) {
            boolean someNumeric = false;
            boolean allNumeric = true;
            for (Label d : labels) {
                result &= d.isValid();
                boolean num = d.isNumeric();
                allNumeric &= num;
                someNumeric |= num;
            }
            if (someNumeric && !allNumeric) {
                return false;
            }
        }
        if (result) {
            int length = length();
            result = length <= 255 && length > 0;
        }
        if (result && ip) {
            int sz = size();
            result = sz == 4 || sz == 6;
        }
        if (result) {
            Problems p = getProblems();
            result = p == null ? result : !p.hasFatal();
        }
        return result;
    }

    @Override
    public Problems getProblems() {
        if (isLocalhost() && !"".equals(toString())) {
            return null;
        }
        String s = toString();
        Problems problems = new Problems();
        Validator<String> validator = isIpV4Address() ? StringValidators.IP_ADDRESS : StringValidators.HOST_NAME; // Host name handles ipv6
        validator.validate(problems,
                getComponentName(), s);
        if (isIpV6Address()) {
            return problems;
        }
        boolean isIp = isIpAddress();
        if (problems.allProblems().isEmpty()) {
            if (!isIp && getTopLevelDomain() == null) {
                problems.append(NbBundle.getMessage(Host.class, "TopLevelDomainMissing"));
            }
            if (!isIp && getDomain() == null) {
                problems.append(NbBundle.getMessage(Host.class, "DomainMissing"));
            }
            if (length() > 255) {
                problems.append(NbBundle.getMessage(Host.class, "HostTooLong"));
            }
            if (isIpV4Address() && size() != 4) {
                problems.append(NbBundle.getMessage(Host.class, "WrongNumberOfElementsForIpAddress"));
            }
            boolean someNumeric = false;
            boolean allNumeric = true;
            for (Label d : labels) {
                boolean num = d.isNumeric();
                allNumeric &= num;
                someNumeric |= num;
            }
            if (someNumeric && !allNumeric) {
                problems.append(NbBundle.getMessage(Host.class, "HostMixesNumericAndNonNumeric"));
            }
        }
        return problems.hasFatal() ? problems : null;
    }

    public static AbstractBuilder<Label, Host> builder() {
        return new HostBuilder();
    }

    @Override
    public String getComponentName() {
        return NbBundle.getMessage(Host.class, "host");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean leadingEmpty = false;
        for (int i = 0; i < labels.length; i++) {
            Label label = labels[i];
            if (ipv6) {
                if (i == 0 && label.isEmpty()) {
                    leadingEmpty = true;
                }
            }
            if (sb.length() > 0) {
                sb.append(ipv6 ? ':' : URLBuilder.LABEL_DELIMITER);
            }
            label.appendTo(sb);
        }
        if (ipv6 && leadingEmpty) {
            sb.insert(0, "::");
        }
        return sb.toString();
    }

    @Override
    public void appendTo(StringBuilder sb) {
        sb.append(toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Host other = (Host) obj;
        if (isLocalhost() && other.isLocalhost()) {
            return true;
        }
        if (ipv6 || (isIpV4Address() && other.isIpV4Address()) || (isIpV6Address() && other.isIpV6Address())) {
            return Arrays.equals(toIntArray(), other.toIntArray());
        }
        return Arrays.equals(this.labels, other.labels);
    }
    
    private String arr2s() {
        StringBuilder sb = new StringBuilder();
        for (int i : toIntArray()) {
            if (sb.length() > 0) {
                sb.append(":");
            }
            sb.append(Integer.toHexString(i));
        }
        return sb.toString();
    }

    private boolean isLocalhostIpV6() {
        if (!isIpV6Address()) {
            return false;
        }
        boolean result = labels.length <= 8;
        if (result) {
            for (int i = 0; i < labels.length; i++) {
                int intValue = labels[i].asInt(true);

                if (i == labels.length - 1) {
                    result &= intValue == 1;
                } else {
                    result &= intValue == 0;
                }
                if (!result) {
                    break;
                }
            }
        }
        return result;
    }
    
    boolean isIpv6() {
        return ipv6;
    }

    public boolean isLocalhost() {
        if (labels.length == 1 && labels[0].toString().isEmpty()) {
            return true;
        }
        if (labels.length == 1 && "localhost".equals(labels[0].toString())) {
            return true;
        }
        String stringValue = toString();
        return "127.0.0.1".equals(stringValue) || "::1".equals(stringValue) || isLocalhostIpV6();
    }

    @Override
    public int hashCode() {
        if (isLocalhost()) {
            return 1;
        }
        if (isIpAddress()) {
            return Arrays.hashCode(toIntArray());
        }
        int hash = 5;
        hash = 73 * hash + Arrays.deepHashCode(this.labels);
        return hash;
    }

    private int[] findLongestZeroRunStart(int[] ints) {
        int length = 0;
        int start = -1;
        int currStart = -1;
        boolean inRun = false;
        for (int i = 0; i < ints.length; i++) {
            boolean isZero = ints[i] == 0;
            if (!inRun && isZero) {
                currStart = i;
                inRun = true;
                continue;
            }
            boolean endOfRun = inRun && !isZero;
            if (endOfRun) {
                inRun = false;
                if (i - currStart > length) {
                    length = i - currStart;
                    start = currStart;
                }
            }
        }
        if (length == 1 || length == 0) {
            return new int[]{-1, 0};
        }
        return new int[]{start, start + length};
    }

    public Host canonicalize() {
        if (isLocalhost()) {
            if (labels.length == 0 || (labels.length == 1 && labels[0].getLabel().equals(""))) {
                return this;
            }
            return ipv6 ? new Host(true, new Label(""), new Label(""), new Label("1")) : new Host(ipv6, new Label("127"), new Label("0"), new Label("0"), new Label("1"));
        }
        // This follows the recommendations in
        // https://en.wikipedia.org/wiki/IPv6_address - i.e.
        // a single 0 is not compressed;  the longest run of zeros is
        // compressed, and if there are two runs of equal length, the
        // leftmost run of zeros is compressed
        if (isIpV6Address()) {
            List<Label> result = new ArrayList<>(8);
            int[] ints = toIntArray();
            int[] skip = findLongestZeroRunStart(ints);
            boolean allZeros = true;
            for (int i = 0; i < ints.length; i++) {
                allZeros &= ints[i] == 0;
                if (i == skip[0]) {
                    result.add(new Label(""));
                }
                if (skip[0] != -1 && i >= skip[0] && i < skip[1])  {
                    continue;
                }
                result.add(new Label(Integer.toHexString(ints[i])));
            }
            if (allZeros) {
                return new Host(true, new Label(""), new Label(""));
            }
            return new Host(true, result.toArray(new Label[result.size()]));
        }
        return this;
    }

    @Override
    public Iterator<Label> iterator() {
        return CollectionUtils.toIterator(labels);
    }

    public int[] toIntArray() {
        int[] result = new int[ipv6 ? 8 : 4];
        Iterator<Label> forward = CollectionUtils.toIterator(labels);
        int remaining = labels.length;
        int ix = 0;
        while (forward.hasNext()) {
            Label label = forward.next();
            if (label.isEmpty()) {
                break;
            }
            result[ix++] = label.asInt(ipv6);
            remaining--;
        }
        ix = result.length - 1;
        if (remaining > 0) {
            Iterator<Label> backward = CollectionUtils.toReverseIterator(labels);
            while (remaining > 0 && ix > 0) {
                result[ix--] = backward.next().asInt(ipv6);
                remaining--;
            }
        }
        return result;
    }

    private static final class HostBuilder extends AbstractBuilder<Label, Host> {

        @Override
        public Host create() {
            return create(false);
        }

        public Host create(boolean ipv6) {
            Label[] domains = elements().toArray(new Label[size()]);
            Label[] reversed = new Label[domains.length];
            for (int i = 0; i < domains.length; i++) {
                reversed[i] = domains[domains.length - (i + 1)];
            }
            return new Host(reversed);
        }

        @Override
        protected Label createElement(String string) {
            return new Label(string);
        }

    }
}
