/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.servlet.handlers.security;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.servlet.ServletRequest;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RenegotiationRequiredException;
import io.undertow.server.SSLSessionInfo;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.HexConverter;

/**
 * Handler that associates SSL metadata with request
 * <p>
 * cipher suite - javax.servlet.request.cipher_suite String
 * bit size of the algorithm - javax.servlet.request.key_size Integer
 * SSL session id - javax.servlet.request.ssl_session_id String
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
public class SSLInformationAssociationHandler implements HttpHandler {
    private final HttpHandler next;

    public SSLInformationAssociationHandler(final HttpHandler next) {
        this.next = next;
    }

    /**
     * Given the name of a TLS/SSL cipher suite, return an int representing it effective stream
     * cipher key strength. i.e. How much entropy material is in the key material being fed into the
     * encryption routines.
     * <p>
     * http://www.thesprawl.org/research/tls-and-ssl-cipher-suites/
     * </p>
     *
     * @param cipherSuite String name of the TLS cipher suite.
     * @return int indicating the effective key entropy bit-length.
     */
    public static int getKeyLength(String cipherSuite) {
        return SSLSessionInfo.calculateKeySize(cipherSuite);
    }


     /* ------------------------------------------------------------ */

    /**
     * Return the chain of X509 certificates used to negotiate the SSL Session.
     *
     * @param session the   javax.net.ssl.SSLSession to use as the source of the cert chain.
     * @return the chain of java.security.cert.X509Certificates used to
     *         negotiate the SSL connection. <br>
     *         Will be null if the chain is missing or empty.
     */
  private X509Certificate[] getCerts(SSLSessionInfo session) {
    try {
      return session.getPeerCertificateChain();
    } catch (SSLPeerUnverifiedException | RenegotiationRequiredException e) {
      return null;
    }
  }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        ServletRequest request = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getServletRequest();
        SSLSessionInfo ssl = exchange.getConnection().getSslSessionInfo();
        if (ssl != null) {
            String cipherSuite = ssl.getCipherSuite();
            byte[] sessionId = ssl.getSessionId();
            request.setAttribute("javax.servlet.request.cipher_suite", cipherSuite);
            request.setAttribute("javax.servlet.request.key_size", ssl.getKeySize());
            request.setAttribute("javax.servlet.request.ssl_session_id", sessionId != null? HexConverter.convertToHexString(sessionId) : null);
            X509Certificate[] certs = getCerts(ssl);
            if (certs != null) {
                request.setAttribute("javax.servlet.request.X509Certificate", certs);
            }

        }
        next.handleRequest(exchange);
    }

}
