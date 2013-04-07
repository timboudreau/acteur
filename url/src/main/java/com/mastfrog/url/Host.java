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
import java.util.Arrays;
import org.netbeans.validation.api.Problems;
import org.netbeans.validation.api.builtin.stringvalidation.StringValidators;
import org.openide.util.NbBundle;

/**
 * An internet host such as a host name or IP address.  Validation is
 * more extensive than that done by java.net.URL, including checking
 * name and label lengths for spec compatibility (max 63 chars per label,
 * 255 chars per host name).
 *
 * @author Tim Boudreau
 */
public final class Host implements URLComponent, Validating {
    private static final long serialVersionUID = 1L;
    private final Label[] labels;
    public Host (Label... domains) {
        Checks.notNull("domains", domains);
        Checks.noNullElements("domains", domains);
        this.labels = new Label[domains.length];
        System.arraycopy(domains, 0, this.labels, 0, domains.length);
    }

    public int size() {
        return labels.length;
    }

    public Label getElement(int ix) {
        return labels[ix];
    }

    public boolean isDomain (String domain) {
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
        String[] parts = path.split("\\" + URLBuilder.LABEL_DELIMITER);
        Label[] els = new Label[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            els[i] = new Label(part);
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
        for (int i=0; i < result.length; i++) {
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
            for (int i=0; i < labels.length; i++) {
                result &= labels[i].isValidIpComponent();
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
        boolean ip = isIpAddress();
        boolean result = ip ||  (getTopLevelDomain() != null && getDomain() != null);
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
        StringValidators.HOST_NAME_OR_IP_ADDRESS.validate(problems,
                getComponentName(), s);
        boolean isIp = isIpAddress();
        if (problems.allProblems().isEmpty()) {
            if (!isIp && getTopLevelDomain() == null) {
                problems.add (NbBundle.getMessage(Host.class, "TopLevelDomainMissing"));
            }
            if (!isIp && getDomain() == null) {
                problems.add (NbBundle.getMessage(Host.class, "DomainMissing"));
            }
            if (length() > 255) {
                problems.add (NbBundle.getMessage(Host.class, "HostTooLong"));
            }
            if (isIpAddress() && size() != 4 && size() != 6) {
                problems.add (NbBundle.getMessage(Host.class, "WrongNumberOfElementsForIpAddress"));
            }
            boolean someNumeric = false;
            boolean allNumeric = true;
            for (Label d : labels) {
                boolean num = d.isNumeric();
                allNumeric &= num;
                someNumeric |= num;
            }
            if (someNumeric && !allNumeric) {
                problems.add (NbBundle.getMessage(Host.class, "HostMixesNumericAndNonNumeric"));
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
        for (int i=0; i < labels.length; i++) {
            if (sb.length() > 0) {
                sb.append (URLBuilder.LABEL_DELIMITER);
            }
            labels[i].appendTo(sb);
        }
        return sb.toString();
    }

    @Override
    public void appendTo(StringBuilder sb) {
        sb.append (toString());
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
        if (!Arrays.equals(this.labels, other.labels)) {
            return false;
        }
        return true;
    }

    public boolean isLocalhost() {
        return (labels.length == 1 && "".equals(labels[0].toString())) || "127.0.0.1".equals(toString()) ||
                labels.length == 1 && "localhost".equals(labels[0].toString().toLowerCase());
    }

    @Override
    public int hashCode() {
        if (isLocalhost()) {
            return 1;
        }
        int hash = 5;
        hash = 73 * hash + Arrays.deepHashCode(this.labels);
        return hash;
    }

    private static final class HostBuilder extends AbstractBuilder<Label, Host> {

        @Override
        public Host create() {
            Label[] domains = elements().toArray(new Label[size()]);
            Label[] reversed = new Label[domains.length];
            for (int i=0; i < domains.length; i++) {
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
