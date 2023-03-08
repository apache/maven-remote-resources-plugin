package org.apache.maven.plugin.resources.remote.it.support;

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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;

public class TestUtils
{
    public static File getTestDir( final String name )
        throws IOException, URISyntaxException
    {
        ClassLoader cloader = Thread.currentThread().getContextClassLoader();
        URL resource = cloader.getResource( name );

        if ( resource == null )
        {
            throw new IOException( "Cannot find test directory: " + name );
        }

        return new File( new URI( resource.toExternalForm() ).normalize().getPath() );
    }

    public static File getBaseDir()
    {
        File result = new File( System.getProperty( "basedir", "." ) );
        try
        {
            return result.getCanonicalFile();
        }
        catch ( IOException e )
        {
            return result.getAbsoluteFile();
        }
    }

    public static Verifier newVerifier( File dir )
        throws VerificationException
    {
        Verifier verifier = new Verifier( dir.getAbsolutePath() );
        verifier.setLocalRepo( System.getProperty( "localRepositoryPath" ) );
        verifier.getSystemProperties().setProperty( "https.protocols", System.getProperty( "https.protocols", "TLSv1.2" ) );

        int javaVersion = Integer.getInteger( "java.specification.version", 7 );
        if ( javaVersion >= 12 )
        {
            verifier.setSystemProperty( "maven.compiler.source", "7" );
            verifier.setSystemProperty( "maven.compiler.target", "7" );
        }            
        return verifier;
    }

}
