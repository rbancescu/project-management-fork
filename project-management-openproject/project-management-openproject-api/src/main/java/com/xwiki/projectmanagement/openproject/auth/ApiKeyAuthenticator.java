/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.projectmanagement.openproject.auth;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * {@link OpenProjectAuthenticator} that authenticates requests with an OpenProject API key. OpenProject accepts these
 * keys only through HTTP Basic authentication, with the literal user name {@code apikey} and the key as password.
 *
 * @version $Id$
 * @since 1.3
 */
public class ApiKeyAuthenticator implements OpenProjectAuthenticator
{
    private static final String API_KEY_USER = "apikey";

    private final String headerValue;

    /**
     * @param apiKey the OpenProject API key to send with each request
     */
    public ApiKeyAuthenticator(String apiKey)
    {
        this.headerValue = "Basic " + Base64.getEncoder()
            .encodeToString((API_KEY_USER + ':' + apiKey).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void authenticate(HttpRequest.Builder builder)
    {
        builder.header("Authorization", this.headerValue);
    }
}
