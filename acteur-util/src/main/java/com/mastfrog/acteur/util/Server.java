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
package com.mastfrog.acteur.util;

import java.io.IOException;

/**
 * A server which can be started, waited on and shutdown.
 *
 * @author Tim Boudreau
 */

public interface Server {
    
    /**
     * Get the default port this server will run on, or if multiple instances
     * have been started on different ports, the last-used port.  For accurate
     * values, use ServerControl.getPort() after calling start().
     * @return The port
     */
    int getPort();

    /**
     * Start the server.
     * @return A ServerControl object which can be waited on for the server
     * to be shut down, and whose signal()/signalAll() methods will trigger
     * a shutdown
     * @throws IOException if something goes wrong
     */
    ServerControl start() throws IOException;
    /**
     * Start the server on a specific port.
     * 
     * @param port The port
     * @return A ServerControl object which can be waited on for the server
     * to be shut down, and whose signal()/signalAll() methods will trigger
     * a shutdown
     * @throws IOException 
     */
    ServerControl start(int port) throws IOException;
    
    /**
     * Start the server.
     * @return A ServerControl object which can be waited on for the server
     * to be shut down, and whose signal()/signalAll() methods will trigger
     * a shutdown
     * @throws IOException if something goes wrong
     */
    ServerControl start(boolean ssl) throws IOException;    
    /**
     * Start the server on a specific port.
     * 
     * @param port The port
     * @return A ServerControl object which can be waited on for the server
     * to be shut down, and whose signal()/signalAll() methods will trigger
     * a shutdown
     * @throws IOException 
     */
    ServerControl start(int port, boolean ssl) throws IOException;
    
}
