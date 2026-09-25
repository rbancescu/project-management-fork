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

import java.net.URI;
import java.net.http.HttpRequest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ApiKeyAuthenticator}.
 *
 * @version $Id$
 */
class ApiKeyAuthenticatorTest
{
    @Test
    void authenticateAddsBasicApiKeyHeader()
    {
        // Any valid absolute URL works here; the authenticator only adds headers, it does not depend on the URI.
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create("http://localhost"));

        new ApiKeyAuthenticator("someApiKey").authenticate(requestBuilder);

        // base64("apikey:someApiKey")
        assertEquals("Basic YXBpa2V5OnNvbWVBcGlLZXk=",
            requestBuilder.build().headers().firstValue("Authorization").orElse(null));
    }
}
