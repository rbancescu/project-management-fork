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
package com.xwiki.projectmanagement.openproject.config;

import com.xwiki.projectmanagement.openproject.auth.ApiKeyAuthenticator;
import com.xwiki.projectmanagement.openproject.auth.BearerTokenAuthenticator;
import com.xwiki.projectmanagement.openproject.auth.OpenProjectAuthenticator;

/**
 * The way in which the calls made to an OpenProject instance are authenticated.
 *
 * @version $Id$
 * @since 1.3
 */
public enum AuthenticationType
{
    /**
     * Each user authenticates with their own OAuth2 access token, obtained through the OAuth2 flow configured with the
     * client id and the client secret of the connection.
     */
    OAUTH("oauth")
    {
        @Override
        public OpenProjectAuthenticator createAuthenticator(String credential)
        {
            return new BearerTokenAuthenticator(credential);
        }
    },

    /**
     * All the calls are authenticated with the API key configured on the connection.
     */
    TOKEN("token")
    {
        @Override
        public OpenProjectAuthenticator createAuthenticator(String credential)
        {
            return new ApiKeyAuthenticator(credential);
        }
    };

    private final String value;

    AuthenticationType(String value)
    {
        this.value = value;
    }

    /**
     * @param credential the OAuth2 access token of the current user or the API key of the connection, depending on
     *     this authentication type
     * @return the strategy authenticating the requests sent to OpenProject with the given credential
     */
    public abstract OpenProjectAuthenticator createAuthenticator(String credential);

    /**
     * @return the value with which this authentication type is stored in the configuration
     */
    public String getValue()
    {
        return this.value;
    }

    /**
     * @param value the stored value of an authentication type
     * @return the matching authentication type or {@link #OAUTH} when the value is empty or unknown, so that the
     *     connections configured before this option existed keep their behaviour
     */
    public static AuthenticationType fromString(String value)
    {
        for (AuthenticationType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return OAUTH;
    }
}
