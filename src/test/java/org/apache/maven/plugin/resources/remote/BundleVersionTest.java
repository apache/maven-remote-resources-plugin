/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugin.resources.remote;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleVersionTest {

    @Test
    void versionComesFromDependency() throws Exception {
        Model model = new Model();
        model.addDependency(dependency("org.example", "bundle", "1.2"));
        assertEquals(
                Arrays.asList("org.example:bundle:1.2"),
                mojo(model, "org.example:bundle").versionedBundles());
    }

    @Test
    void versionComesFromDependencyManagement() throws Exception {
        Model model = new Model();
        DependencyManagement management = new DependencyManagement();
        management.addDependency(dependency("org.example", "bundle", "2.0"));
        model.setDependencyManagement(management);
        assertEquals(
                Arrays.asList("org.example:bundle:2.0"),
                mojo(model, "org.example:bundle").versionedBundles());
    }

    @Test
    void versionedDescriptorsAreKept() throws Exception {
        assertEquals(
                Arrays.asList("org.example:bundle:1.0:jar"),
                mojo(new Model(), "org.example:bundle:1.0:jar").versionedBundles());
    }

    @Test
    void missingVersionFails() throws Exception {
        AbstractProcessRemoteResourcesMojo mojo = mojo(new Model(), "org.example:bundle");
        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::versionedBundles);
        assertTrue(e.getMessage().contains("has no version"), e.getMessage());
    }

    private static Dependency dependency(String groupId, String artifactId, String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        return dependency;
    }

    private static AbstractProcessRemoteResourcesMojo mojo(Model model, String... bundles) throws Exception {
        AbstractProcessRemoteResourcesMojo mojo = new ProcessRemoteResourcesMojo(null, null, null, null, null);
        set(mojo, "project", new MavenProject(model));
        set(mojo, "resourceBundles", Arrays.asList(bundles));
        return mojo;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = AbstractProcessRemoteResourcesMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
