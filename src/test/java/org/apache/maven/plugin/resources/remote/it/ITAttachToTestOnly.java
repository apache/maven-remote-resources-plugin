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
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

import org.apache.maven.plugin.resources.remote.it.support.MavenRunner;
import org.apache.maven.plugin.resources.remote.it.support.TestUtils;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With <code>attachToMain=false</code>, a same-named file in the main resources must neither replace the bundled
 * one for tests nor go missing from the main jar (#183).
 */
public class ITAttachToTestOnly extends AbstractIT {
    @Test
    public void test() throws IOException, URISyntaxException {
        File dir = TestUtils.getTestDir("attach-to-test-only");

        MavenRunner verifier = TestUtils.newVerifier(new File(dir, "bundle"));
        verifier.addCliArgument("deploy");
        verifier.execute();
        verifier.verifyErrorFreeLog();

        File projectDir = new File(dir, "project");
        verifier = TestUtils.newVerifier(projectDir);
        verifier.deleteArtifacts("org.apache.maven.plugin.rresource.it.atto");
        verifier.addCliArgument("package");
        verifier.execute();
        verifier.verifyErrorFreeLog();

        String testResource = FileUtils.fileRead(new File(projectDir, "target/test-classes/META-INF/beans.xml"));
        assertTrue(testResource.contains("from bundle"), testResource);

        File[] jars = new File(projectDir, "target").listFiles((d, name) -> name.endsWith(".jar"));
        assertNotNull(jars);
        assertTrue(jars.length == 1, "expected one jar in target");
        try (JarFile jar = new JarFile(jars[0])) {
            assertNotNull(jar.getEntry("META-INF/beans.xml"), "the main jar lost its own META-INF/beans.xml");
            try (InputStream in = jar.getInputStream(jar.getEntry("META-INF/beans.xml"))) {
                String mainResource = IOUtil.toString(in, StandardCharsets.UTF_8.name());
                assertTrue(mainResource.contains("from project"), mainResource);
            }
        }
    }
}
