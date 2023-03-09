package org.apache.maven.plugin.resources.remote.it;

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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.apache.maven.plugin.resources.remote.it.support.TestUtils;
import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.Os;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * @author Benjamin Bentmann
 */
public class IT_RunOnlyAtExecutionRoot
    extends AbstractIT
{
    @Test
    public void test()
        throws IOException, URISyntaxException, VerificationException
    {
        // Workaround for Windows + Maven-3.5.x/3.6.0 + Jenkins due to MNG-6261
        assumeTrue( !( System.getenv( "JENKINS_HOME" ) != null 
                       && Os.isFamily( Os.FAMILY_WINDOWS ) 
                       &&  ( System.getenv( "MAVEN_HOME" ).contains( "-3.5." ) || System.getenv( "MAVEN_HOME" ).contains( "-3.6.0" ) ) ) );

        File dir = TestUtils.getTestDir( "run-only-at-execution-root" );

        Verifier verifier;

        verifier = TestUtils.newVerifier( new File( dir, "resource-projects" ) );
        verifier.addCliArgument( "deploy" );
        verifier.execute();
        verifier.setLogFileName( "first.log" );
        verifier.verifyErrorFreeLog();

        verifier = TestUtils.newVerifier( dir );

        // I'm not sure what exactly the intention of the test was.
        // Based on the name i assumed to be sure the remote-resources-plugin
        // will be executed only at root level.
        // This will fail, cause if an needed artifact is not there
        // maven will fail.
        // Might reconsider how to write a better testcase.
        // verifier.deleteArtifacts( "org.apache.maven.plugin.rresource.it.mrr41" );

        // verifier.addCliArgument( "generate-resources" );
        verifier.addCliArgument( "package" );
        verifier.execute();
        verifier.verifyErrorFreeLog();

        String depResource = "target/maven-shared-archive-resources/DEPENDENCIES";
        File output = new File( dir, depResource );
        assertTrue( output.exists() );

        // aggregates in bulk, not per-child
        //assertFalse( new File( dir, "child1/" + depResource ).exists() );
        //assertFalse( new File( dir, "child2/" + depResource ).exists() );

        String content = FileUtils.fileRead( output );

        assertTrue( content.contains( "Dependency Id: org.apache.maven.plugin.rresource.it.mrr41:release:1.0" ) );
        assertTrue( content.contains( "Dependency Id: org.apache.maven.plugin.rresource.it.mrr41:snapshot:1.0-SNAPSHOT" ) );
    }

}
