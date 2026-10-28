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

import org.apache.maven.plugin.resources.remote.it.support.MavenRunner;
import org.apache.maven.plugin.resources.remote.it.support.TestUtils;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ITSpdxLicenseId extends AbstractIT {
    @Test
    public void test() throws IOException, URISyntaxException {
        File dir = TestUtils.getTestDir("spdx-license-id");

        MavenRunner verifier = TestUtils.newVerifier(dir);
        verifier.addCliArgument("deploy");
        verifier.execute();
        verifier.verifyErrorFreeLog();

        File projectDir = new File(dir, "project");
        verifier = TestUtils.newVerifier(projectDir);
        verifier.deleteArtifacts("org.apache.maven.plugin.rresource.it.spdx");
        verifier.addCliArgument("generate-resources");
        verifier.execute();
        verifier.verifyErrorFreeLog();

        File output = new File(projectDir, "target/maven-shared-archive-resources/DEPENDENCIES");
        String content = FileUtils.fileRead(output).replace("\r\n", "\n");

        assertTrue(content.contains("a-name: Apache-2.0\n"), content);
        assertTrue(content.contains("b-url: Apache-2.0\n"), content);
        assertTrue(content.contains("c-expression: (Apache-2.0 OR EPL-2.0)\n"), content);
        assertTrue(content.contains("d-unknown: Public Domain\n"), content);
        assertTrue(content.contains("d-unknown expression: \n"), content);
        assertTrue(content.contains("e-dual: CDDL-1.1\n"), content);
        assertTrue(content.contains("e-dual: GPL-2.0-only WITH Classpath-exception-2.0\n"), content);
        assertTrue(
                content.contains("e-dual expression: CDDL-1.1 OR (GPL-2.0-only WITH Classpath-exception-2.0)\n"),
                content);
    }
}
