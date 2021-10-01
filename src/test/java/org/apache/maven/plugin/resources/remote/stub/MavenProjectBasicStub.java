package org.apache.maven.plugin.resources.remote.stub;

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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.util.FileUtils;

/**
 * Stub
 */
public class MavenProjectBasicStub
    extends MavenProject
{
    protected String identifier;

    protected String testRootDir;

    protected Properties properties;

    protected String description;

    protected ModelStub modelStub;

    protected File file;

    protected ArtifactStub artifact;

    public MavenProjectBasicStub( String id )
        throws Exception
    {
        // most values are hardcoded to have a controlled environment
        super( new ModelStub() );

        modelStub = (ModelStub) getModel();
        properties = new Properties();
        artifact = new ArtifactStub();
        identifier = id;

        // set isolated root directory
        testRootDir = PlexusTestCase.getBasedir() + "/target/test-classes/unit/test-dir/" + identifier;

        if ( !FileUtils.fileExists( testRootDir ) )
        {
            FileUtils.mkdir( testRootDir );
        }

        artifact.populate( this );

        // this is ugly but needed to ensure that the copy constructor
        // works correctly
        initializeParentFields();
    }

    @Override
    public String getName()
    {
        return "Test Project " + identifier;
    }

    @Override
    public void setDescription( String desc )
    {
        description = desc;
    }

    @Override
    public String getDescription()
    {
        if ( description == null )
        {
            return "this is a test project";
        }
        else
        {
            return description;
        }
    }

    @Override
    public File getBasedir()
    {
        // create an isolated environment
        // see setupTestEnvironment for details
        return new File( testRootDir );
    }

    @Override
    public Artifact getArtifact()
    {
        return artifact;
    }

    @Override
    public String getGroupId()
    {
        return "org.apache.maven.plugin.test";
    }

    @Override
    public String getArtifactId()
    {
        return "maven-resource-plugin-test#" + identifier;
    }

    @Override
    public String getPackaging()
    {
        return "ejb";
    }

    @Override
    public String getVersion()
    {
        return identifier;
    }

    public void addProperty( String key, String value )
    {
        properties.put( key, value );
    }

    @Override
    public Properties getProperties()
    {
        return properties;
    }

    // to prevent the MavenProject copy constructor from blowing up
    private void initializeParentFields()
    {
        // the pom should be located in the isolated dummy root
        super.setFile( new File( getBasedir(), "pom.xml" ) );
        super.setDependencyArtifacts( new HashSet<Artifact>() );
        super.setArtifacts( new HashSet<Artifact>() );
        super.setReportArtifacts( new HashSet<Artifact>() );
        super.setExtensionArtifacts( new HashSet<Artifact>() );
        super.setRemoteArtifactRepositories( new LinkedList<ArtifactRepository>() );
        super.setPluginArtifactRepositories( new LinkedList<ArtifactRepository>() );
        super.setCollectedProjects( new LinkedList<MavenProject>() );
        super.setActiveProfiles( new LinkedList<Profile>() );
        super.setOriginalModel( null );
        super.setExecutionProject( this );
        super.setArtifact( artifact );
    }
}
