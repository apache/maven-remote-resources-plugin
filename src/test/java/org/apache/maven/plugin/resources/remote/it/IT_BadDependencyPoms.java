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
public class IT_BadDependencyPoms extends AbstractIT {
    @Test
    public void test() throws IOException, URISyntaxException, VerificationException {
        File dir = TestUtils.getTestDir("bad-dependency-poms");

        Verifier verifier = TestUtils.newVerifier(dir);
        verifier.deleteArtifacts("test");

        verifier.addCliArgument("generate-resources");
        verifier.execute();

        verifier.verifyTextInLog(
                "[WARNING] Invalid project model for artifact [test:missing:0.1]. It will be ignored by the remote resources Mojo.");
        verifier.verifyTextInLog(
                "[WARNING] Invalid project model for artifact [test:invalid:0.1]. It will be ignored by the remote resources Mojo");

        File output = new File(dir, "target/maven-shared-archive-resources/DEPENDENCIES");
        String content = FileUtils.fileRead(output);

        assertTrue(content.contains("Dependency Id: test:pom:0.2"));
    }
}
