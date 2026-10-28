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

import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupplementMergeTest {

    @Test
    void supplementKeepsTheDependencyUrls() {
        Model pom = new Model();
        pom.setGroupId("org.foo");
        pom.setArtifactId("widget");
        pom.setUrl("https://widget.example.org/site");
        Scm scm = new Scm();
        scm.setUrl("https://github.com/example/widget");
        pom.setScm(scm);

        Model supplement = new Model();
        supplement.setGroupId("org.foo");
        supplement.setArtifactId("widget");
        License license = new License();
        license.setName("MIT License");
        supplement.addLicense(license);

        Model merged = new ProcessRemoteResourcesMojo(null, null, null, null, null).mergeModels(pom, supplement);

        assertEquals("https://widget.example.org/site", merged.getUrl());
        assertEquals("https://github.com/example/widget", merged.getScm().getUrl());
        assertEquals("MIT License", merged.getLicenses().get(0).getName());
    }
}
