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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.projectmanagement.exception.ProjectManagementException;
import com.xwiki.projectmanagement.model.Linkable;
import com.xwiki.projectmanagement.model.PaginatedResult;
import com.xwiki.projectmanagement.openproject.OpenProjectApiClient;
import com.xwiki.projectmanagement.openproject.config.OpenProjectConfiguration;
import com.xwiki.projectmanagement.openproject.model.CreateWorkPackage;
import com.xwiki.projectmanagement.openproject.model.Project;

import utils.OpenProjectTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class HandleWorkPackagesTest
{
    @MockComponent
    private OpenProjectConfiguration openProjectConfiguration;

    @MockComponent
    private OpenProjectApiClient openProjectApiClient;

    @InjectMockComponents
    private HandleWorkPackages handleWorkPackages;

    private static final Integer OFFSET = 1;

    private static final Integer PAGE_SIZE = 10;

    private static final String INSTANCE = "instance";

    private static final String WIKI = "wiki";

    final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setUp()
    {
        when(this.openProjectConfiguration.getOpenProjectApiClient(INSTANCE)).thenReturn(this.openProjectApiClient);
    }
    // TODO: add test for default values

    @Test
    public void getAvailableProjectsReturnsConflictWhenTokenIsNullTest() throws ProjectManagementException
    {
        when(this.openProjectConfiguration.getOpenProjectApiClient(INSTANCE)).thenReturn(null);

        Response response = this.handleWorkPackages.getAvailableProjects(WIKI, INSTANCE, "", OFFSET, PAGE_SIZE, null);
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
    }

    @Test
    public void getAvailableProjectsTest() throws ProjectManagementException, IOException
    {
        List<Project> projects = generateProjects();

        PaginatedResult<Project> paginatedProjects =
            new PaginatedResult<>(projects, OFFSET, PAGE_SIZE, projects.size());

        String jsonResponse = OpenProjectTestUtils.getCreateWorkPackageProjectsFormResponse();
        JsonNode node = this.mapper.readTree(jsonResponse);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(node);
        when(this.openProjectApiClient.getAvailableProjects(anyString(), anyInt(), anyInt(), anyString())).thenReturn(
            paginatedProjects);

        Response response = this.handleWorkPackages.getAvailableProjects(WIKI, INSTANCE, "", OFFSET, PAGE_SIZE, null);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<Map<String, String>> expected = List.of(
            Map.of("value", "/api/v3/projects/1", "label", "Project 1"),
            Map.of("value", "/api/v3/projects/2", "label", "Project 2")
        );
        assertEquals(expected, response.getEntity());
    }

    @Test
    public void getAvailableProjectsThrowsProjectManagementExceptionTest() throws ProjectManagementException,
        JsonProcessingException
    {
        String jsonResponse = "{\"_type\":\"Error\"}";
        JsonNode node = this.mapper.readTree(jsonResponse);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(node);

        assertThrows(
            ProjectManagementException.class,
            () -> this.handleWorkPackages.getAvailableProjects(WIKI, INSTANCE, "", OFFSET, PAGE_SIZE, null)
        );
    }

    @Test
    public void createWorkPackageValidationTest() throws ProjectManagementException, IOException
    {
        CreateWorkPackage createWorkPackage = generateCreateWorkPackage();

        createWorkPackage.setFormOnly(true);

        JsonNode workPackagesNode = mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageValidationFailsApiResponse()
        );
        when(openProjectApiClient.getWorkPackagesFormResponse(anyString()))
            .thenReturn(workPackagesNode);

        Response response = handleWorkPackages.createWorkPackage(WIKI, INSTANCE, createWorkPackage);

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(openProjectApiClient).getWorkPackagesFormResponse(requestCaptor.capture());

        JsonNode expectedRequest = mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageRequestExample()
        );
        JsonNode actualRequest = mapper.readTree(requestCaptor.getValue());
        assertEquals(expectedRequest, actualRequest, "Request JSON should match expected");

        JsonNode expectedResponse = mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageValidationFailsResponse()
        );
        JsonNode actualResponse = mapper.valueToTree(response.getEntity());
        assertEquals(expectedResponse, actualResponse, "Response JSON should match expected");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void createWorkPackageTest() throws ProjectManagementException, IOException
    {
        String validationSuccessResponseJsonString =
            OpenProjectTestUtils.getCreateWorkPackageValidationSuccessResponse();

        JsonNode validationSuccessResponseNode = this.mapper.readTree(validationSuccessResponseJsonString);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(
            validationSuccessResponseNode);
        when(this.openProjectApiClient.createWorkPackage(anyString(), anyString())).thenReturn(any(JsonNode.class));

        Response response = this.handleWorkPackages.createWorkPackage(WIKI, INSTANCE, new CreateWorkPackage());

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void createWorkPackageFails() throws ProjectManagementException, IOException
    {
        String validationSuccessResponseJsonString =
            OpenProjectTestUtils.getCreateWorkPackageValidationSuccessResponse();

        JsonNode validationSuccessResponseNode = this.mapper.readTree(validationSuccessResponseJsonString);

        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(
            validationSuccessResponseNode);
        when(this.openProjectApiClient.createWorkPackage(anyString(), anyString())).thenThrow(
            new ProjectManagementException("Error creating work package"));

        assertThrows(
            ProjectManagementException.class,
            () -> this.handleWorkPackages.createWorkPackage(WIKI, INSTANCE, new CreateWorkPackage())
        );
    }

    @Test
    public void createWorkPackageWithCustomFieldsTest() throws ProjectManagementException, IOException
    {
        JsonNode formResponse = this.mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageCustomFieldsFormResponse());
        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(formResponse);

        Map<String, Object> customFields = new LinkedHashMap<>();
        customFields.put("customField1", "ACME");
        customFields.put("notes", "Some *long* text");
        customFields.put("Severity", "/api/v3/custom_options/7");
        customFields.put("customField4", List.of("/api/v3/users/1", "/api/v3/users/2"));
        customFields.put("customField5", 42);
        customFields.put("Unknown field", "ignored");
        CreateWorkPackage workPackage = new CreateWorkPackage();
        workPackage.setProject("/api/v3/projects/1");
        workPackage.setSubject("subject");
        workPackage.setType("/api/v3/types/2");
        workPackage.setCustomFields(customFields);

        Response response = this.handleWorkPackages.createWorkPackage(WIKI, INSTANCE, workPackage);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.openProjectApiClient, times(2)).getWorkPackagesFormResponse(requestCaptor.capture());
        verify(this.openProjectApiClient).createWorkPackage(anyString(), anyString());

        JsonNode firstRequest = this.mapper.readTree(requestCaptor.getAllValues().get(0));
        assertEquals(false, firstRequest.has("customField1"));

        JsonNode expectedRequest = this.mapper.readTree("{"
            + "\"subject\":\"subject\","
            + "\"customField1\":\"ACME\","
            + "\"customField2\":{\"raw\":\"Some *long* text\"},"
            + "\"customField5\":42,"
            + "\"_links\":{"
            + "\"project\":{\"href\":\"/api/v3/projects/1\"},"
            + "\"type\":{\"href\":\"/api/v3/types/2\"},"
            + "\"customField3\":{\"href\":\"/api/v3/custom_options/7\"},"
            + "\"customField4\":[{\"href\":\"/api/v3/users/1\"},{\"href\":\"/api/v3/users/2\"}]"
            + "}}");
        assertEquals(expectedRequest, this.mapper.readTree(requestCaptor.getAllValues().get(1)));
    }

    @Test
    public void createWorkPackageWithCustomFieldsSendsTheDefaultTypeTest()
        throws ProjectManagementException, IOException
    {
        JsonNode formResponse = this.mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageCustomFieldsFormResponse());
        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(formResponse);

        CreateWorkPackage workPackage = new CreateWorkPackage();
        workPackage.setProject("/api/v3/projects/1");
        workPackage.setCustomFields(Map.of("customField1", "ACME"));

        this.handleWorkPackages.createWorkPackage(WIKI, INSTANCE, workPackage);

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.openProjectApiClient, times(2)).getWorkPackagesFormResponse(requestCaptor.capture());
        JsonNode secondRequest = this.mapper.readTree(requestCaptor.getAllValues().get(1));
        assertEquals("/api/v3/types/1", secondRequest.path("_links").path("type").path("href").asText());
    }

    @Test
    public void createWorkPackageWithOnlyUnknownCustomFieldsRequestsTheFormOnceTest()
        throws ProjectManagementException, IOException
    {
        JsonNode formResponse = this.mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageCustomFieldsFormResponse());
        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(formResponse);

        CreateWorkPackage workPackage = new CreateWorkPackage();
        workPackage.setCustomFields(Map.of("Unknown field", "ignored"));

        Response response = this.handleWorkPackages.createWorkPackage(WIKI, INSTANCE, workPackage);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(this.openProjectApiClient, times(1)).getWorkPackagesFormResponse(anyString());
    }

    @Test
    public void createWorkPackageFormOnlyDescribesCustomFieldsTest() throws ProjectManagementException, IOException
    {
        JsonNode formResponse = this.mapper.readTree(
            OpenProjectTestUtils.getCreateWorkPackageCustomFieldsFormResponse());
        when(this.openProjectApiClient.getWorkPackagesFormResponse(anyString())).thenReturn(formResponse);

        CreateWorkPackage workPackage = new CreateWorkPackage();
        workPackage.setFormOnly(true);

        Response response = this.handleWorkPackages.createWorkPackage(WIKI, INSTANCE, workPackage);

        verify(this.openProjectApiClient, never()).createWorkPackage(anyString(), anyString());
        JsonNode customFields = this.mapper.valueToTree(response.getEntity()).path("customFields");
        JsonNode expected = this.mapper.readTree("{"
            + "\"customField1\":{\"name\":\"Customer\",\"type\":\"String\",\"required\":true,"
            + "\"writable\":true,\"multiValue\":false,\"defaultValue\":null},"
            + "\"customField2\":{\"name\":\"Notes\",\"type\":\"Formattable\",\"required\":false,"
            + "\"writable\":true,\"multiValue\":false,\"defaultValue\":null},"
            + "\"customField3\":{\"name\":\"Severity\",\"type\":\"CustomOption\",\"required\":false,"
            + "\"writable\":true,\"multiValue\":false,\"allowedValues\":["
            + "{\"value\":\"/api/v3/custom_options/7\",\"label\":\"High\"},"
            + "{\"value\":\"/api/v3/custom_options/8\",\"label\":\"Low\"}],\"defaultValue\":null},"
            + "\"customField4\":{\"name\":\"Reviewers\",\"type\":\"[]User\",\"required\":false,"
            + "\"writable\":true,\"multiValue\":true,"
            + "\"allowedValuesHref\":\"/api/v3/projects/1/available_assignees\",\"defaultValue\":null},"
            + "\"customField5\":{\"name\":\"Ticket number\",\"type\":\"Integer\",\"required\":false,"
            + "\"writable\":true,\"multiValue\":false,\"defaultValue\":null}"
            + "}");
        assertEquals(expected, customFields);
    }

    private List<Project> generateProjects()
    {
        Project firstProject = new Project();
        firstProject.setId(1);
        firstProject.setName("Project 1");
        firstProject.setSelf(new Linkable("Project 1", "/api/v3/projects/1"));
        Project secondProject = new Project();
        secondProject.setId(2);
        secondProject.setName("Project 2");
        secondProject.setSelf(new Linkable("Project 2", "/api/v3/projects/2"));
        return List.of(firstProject, secondProject);
    }

    private CreateWorkPackage generateCreateWorkPackage()
    {
        CreateWorkPackage createWorkPackage = new CreateWorkPackage();
        createWorkPackage.setDescription("description");
        createWorkPackage.setSubject("subject");
        createWorkPackage.setAssignee("/api/v3/users/1");
        createWorkPackage.setProject("/api/v3/projects/1");
        createWorkPackage.setType("/api/v3/types/1");

        return createWorkPackage;
    }

}
