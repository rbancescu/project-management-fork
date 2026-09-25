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
package com.xwiki.projectmanagement.openproject.internal.rest.workPackages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Maps the plain {@code customFields} values of a work package creation / update request onto the shape OpenProject
 * expects, driven by the custom field definitions found in the schema of a work package form.
 * <p>
 * OpenProject names custom fields {@code customField<id>} and places their values depending on the field format:
 * <ul>
 *     <li>link-based formats (list, user, version, and every multi-value format) go under {@code _links} as
 *     {@code {"href": ...}} or as an array of such objects;</li>
 *     <li>long text ({@code Formattable}) goes at the top level as {@code {"raw": ...}};</li>
 *     <li>everything else (string, integer, float, boolean, date) goes at the top level as is.</li>
 * </ul>
 *
 * @version $Id$
 * @since 1.3
 */
final class WorkPackageCustomFields
{
    private static final String CUSTOM_FIELD_PREFIX = "customField";

    private static final String LINKS = "_links";

    private static final String EMBEDDED = "_embedded";

    private static final String HREF = "href";

    private static final String TITLE = "title";

    private static final String TYPE = "type";

    private static final String NAME = "name";

    private static final String VALUE = "value";

    private static final String ALLOWED_VALUES = "allowedValues";

    private static final String RAW = "raw";

    private static final String REQUIRED = "required";

    private static final String WRITABLE = "writable";

    private static final String SELF = "self";

    private static final String MULTI_VALUE_PREFIX = "[]";

    private static final Set<String> LINK_TYPES = Set.of("CustomOption", "User", "Version", "Group", "Principal");

    private WorkPackageCustomFields()
    {
    }

    /**
     * Adds the given custom field values to an OpenProject form request. Keys may be the OpenProject attribute name
     * ({@code customField12}) or the display name of the custom field; keys that match no custom field of the schema
     * are ignored.
     *
     * @param formRequest the form request to enrich; its {@code _links} entry, when present, must be mutable
     * @param customFields the values sent by the caller, keyed by attribute or display name
     * @param schemaNode the schema of a work package form for the same project and type
     * @return {@code true} if at least one custom field was added to the request
     */
    @SuppressWarnings("unchecked")
    static boolean apply(Map<String, Object> formRequest, Map<String, Object> customFields, JsonNode schemaNode)
    {
        if (customFields == null || customFields.isEmpty()) {
            return false;
        }
        boolean applied = false;
        for (Map.Entry<String, Object> entry : customFields.entrySet()) {
            String attribute = resolveAttribute(entry.getKey(), schemaNode);
            if (attribute == null) {
                continue;
            }
            JsonNode definition = schemaNode.path(attribute);
            Object value = entry.getValue();
            if (isLink(definition)) {
                Map<String, Object> links =
                    (Map<String, Object>) formRequest.computeIfAbsent(LINKS, key -> new HashMap<String, Object>());
                links.put(attribute, toLinkValue(value));
            } else if ("Formattable".equals(definition.path(TYPE).asText()) && !(value instanceof Map)) {
                Map<String, Object> formattable = new HashMap<>();
                formattable.put(RAW, value);
                formRequest.put(attribute, formattable);
            } else {
                formRequest.put(attribute, value);
            }
            applied = true;
        }
        return applied;
    }

    /**
     * Describes the custom fields available in a work package form, so that callers can discover their attribute
     * names, formats and allowed values.
     *
     * @param schemaNode the schema of the work package form
     * @param payloadNode the payload of the work package form, used for the current values
     * @return the custom field descriptions keyed by attribute name ({@code customField12})
     */
    static Map<String, Object> describe(JsonNode schemaNode, JsonNode payloadNode)
    {
        Map<String, Object> descriptions = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = schemaNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!isCustomField(field.getKey())) {
                continue;
            }
            JsonNode definition = field.getValue();
            Map<String, Object> description = new LinkedHashMap<>();
            description.put(NAME, definition.path(NAME).asText());
            description.put(TYPE, definition.path(TYPE).asText());
            description.put(REQUIRED, definition.path(REQUIRED).booleanValue());
            description.put(WRITABLE, definition.path(WRITABLE).booleanValue());
            description.put("multiValue", definition.path(TYPE).asText().startsWith(MULTI_VALUE_PREFIX));
            putAllowedValues(description, definition);
            description.put("defaultValue", getCurrentValue(payloadNode, field.getKey(), isLink(definition)));
            descriptions.put(field.getKey(), description);
        }
        return descriptions;
    }

    private static String resolveAttribute(String key, JsonNode schemaNode)
    {
        if (key == null) {
            return null;
        }
        if (isCustomField(key) && schemaNode.path(key).isObject()) {
            return key;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = schemaNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (isCustomField(field.getKey()) && key.trim().equalsIgnoreCase(field.getValue().path(NAME).asText())) {
                return field.getKey();
            }
        }
        return null;
    }

    private static boolean isCustomField(String attribute)
    {
        return attribute.startsWith(CUSTOM_FIELD_PREFIX);
    }

    private static boolean isLink(JsonNode definition)
    {
        String type = definition.path(TYPE).asText();
        return LINKS.equals(definition.path("location").asText()) || type.startsWith(MULTI_VALUE_PREFIX)
            || LINK_TYPES.contains(type);
    }

    private static Object toLinkValue(Object value)
    {
        if (value instanceof Collection) {
            List<Map<String, Object>> hrefs = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                hrefs.add(toHref(item));
            }
            return hrefs;
        }
        return toHref(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toHref(Object value)
    {
        // A null href is how OpenProject clears a link; an object is assumed to already be a link.
        if (value instanceof Map) {
            return new HashMap<>((Map<String, Object>) value);
        }
        Map<String, Object> href = new HashMap<>();
        href.put(HREF, value);
        return href;
    }

    private static void putAllowedValues(Map<String, Object> description, JsonNode definition)
    {
        JsonNode embedded = definition.path(EMBEDDED).path(ALLOWED_VALUES);
        JsonNode linked = definition.path(LINKS).path(ALLOWED_VALUES);
        if (embedded.isArray()) {
            List<Map<String, String>> allowedValues = new ArrayList<>();
            for (JsonNode allowedValue : embedded) {
                allowedValues.add(toValueLabel(allowedValue.path(LINKS).path(SELF).path(HREF).asText(),
                    firstText(allowedValue.path(VALUE), allowedValue.path(NAME),
                        allowedValue.path(LINKS).path(SELF).path(TITLE))));
            }
            description.put(ALLOWED_VALUES, allowedValues);
        } else if (linked.isArray()) {
            List<Map<String, String>> allowedValues = new ArrayList<>();
            for (JsonNode allowedValue : linked) {
                allowedValues.add(toValueLabel(allowedValue.path(HREF).asText(), allowedValue.path(TITLE).asText()));
            }
            description.put(ALLOWED_VALUES, allowedValues);
        } else if (linked.path(HREF).isTextual()) {
            // Users and versions are not listed inline: OpenProject links to a collection that has to be queried.
            description.put("allowedValuesHref", linked.path(HREF).asText());
        }
    }

    private static Map<String, String> toValueLabel(String value, String label)
    {
        Map<String, String> valueLabel = new LinkedHashMap<>();
        valueLabel.put(VALUE, value);
        valueLabel.put("label", label);
        return valueLabel;
    }

    private static String firstText(JsonNode... nodes)
    {
        for (JsonNode node : nodes) {
            if (node.isValueNode() && !node.isNull() && !node.asText().isEmpty()) {
                return node.asText();
            }
        }
        return "";
    }

    private static Object getCurrentValue(JsonNode payloadNode, String attribute, boolean isLink)
    {
        return isLink ? getCurrentLinkValue(payloadNode, attribute) : getCurrentPlainValue(payloadNode, attribute);
    }

    private static Object getCurrentLinkValue(JsonNode payloadNode, String attribute)
    {
        JsonNode link = payloadNode.path(LINKS).path(attribute);
        if (link.isArray()) {
            List<String> hrefs = new ArrayList<>();
            link.forEach(item -> hrefs.add(item.path(HREF).asText()));
            return hrefs;
        }
        return link.path(HREF).isTextual() ? link.path(HREF).asText() : null;
    }

    private static Object getCurrentPlainValue(JsonNode payloadNode, String attribute)
    {
        JsonNode value = payloadNode.path(attribute);
        if (value.isObject()) {
            value = value.path(RAW);
        }
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.numberValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return value.asText();
    }
}
