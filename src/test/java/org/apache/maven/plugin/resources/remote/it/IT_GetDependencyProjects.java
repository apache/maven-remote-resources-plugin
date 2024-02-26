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
package org.apache.maven.plugin.resources.remote.it;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.maven.plugin.resources.remote.it.support.TestUtils;
import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;
import org.codehaus.plexus.util.FileUtils;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author Benjamin Bentmann
 */
public class IT_GetDependencyProjects extends AbstractIT {
    @Test
    public void test() throws IOException, URISyntaxException, VerificationException {
        File dir = TestUtils.getTestDir("get-dependency-projects");

        Verifier verifier;

        verifier = TestUtils.newVerifier(dir);
        verifier.addCliArgument("deploy");
        verifier.execute();
        verifier.verifyErrorFreeLog();

        File projectDir = new File(dir, "project");
        verifier = TestUtils.newVerifier(projectDir);

        verifier.deleteArtifacts("org.apache.maven.plugin.rresource.it.gdp");

        verifier.addCliArgument("generate-resources");
        verifier.execute();
        verifier.verifyErrorFreeLog();

        File output = new File(projectDir, "target/maven-shared-archive-resources/DEPENDENCIES");
        String content = FileUtils.fileRead(output);

        assertTrue(content.contains("Dependency Id: org.apache.maven.plugin.rresource.it.gdp:release:1.0"));
        assertTrue(content.contains("Dependency Id: org.apache.maven.plugin.rresource.it.gdp:snapshot:1.0-SNAPSHOT"));
    }
}
