package org.apache.maven.plugin.resources.remote;

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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.resources.remote.stub.MavenProjectBuildStub;
import org.apache.maven.plugin.resources.remote.stub.MavenProjectResourcesStub;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;


/**
 * RemoteResources plugin Test Case
 */
public class RemoteResourcesMojoTest
        extends AbstractMojoTestCase
{
    static final String DEFAULT_BUNDLE_POM_PATH = "target/test-classes/unit/rrmojotest/bundle-plugin-config.xml";
    static final String DEFAULT_PROCESS_POM_PATH = "target/test-classes/unit/rrmojotest/process-plugin-config.xml";

    private final String LOCAL_REPO = "target/local-repo/";

    @Override
    public void setUp()
            throws Exception
    {
        super.setUp();
    }

    @Override
    public void tearDown()
            throws Exception
    {

    }

    /**
     * check test environment
     *
     * @throws Exception if any exception occurs
     */
    public void testTestEnvironment()
            throws Exception
    {
        // Perform lookup on the Mojo to make sure everything is ok
        lookupProcessMojo();
    }


    public void testNoBundles()
            throws Exception
    {
        final MavenProjectResourcesStub project = createTestProject( "default-nobundles" );
        final ProcessRemoteResourcesMojo mojo = lookupProcessMojoWithDefaultSettings( project );

        setupDefaultProject( project );

        mojo.execute();
    }

    public void testCreateBundle()
            throws Exception
    {
        buildResourceBundle( "default-createbundle",
                null,
                new String[] {"SIMPLE.txt"},
                null );
    }

    public void testSimpleBundles()
            throws Exception
    {
        final MavenProjectResourcesStub project = createTestProject( "default-simplebundles" );
        final ProcessRemoteResourcesMojo mojo = lookupProcessMojoWithSettings( project,
                new String[] {
                        "test:test:1.0"
                } );

        setupDefaultProject( project );

        String path = pathOf( new DefaultArtifact( "test",
                "test",
                VersionRange.createFromVersion( "1.0" ),
                null,
                "jar",
                "",
                new DefaultArtifactHandler() ) );

        File file = new File( path );
        file.getParentFile().mkdirs();
        buildResourceBundle( "default-simplebundles-create",
                null,
                new String[] {"SIMPLE.txt"},
                file );


        mojo.execute();

        file = (File) getVariableValueFromObject( mojo, "outputDirectory" );
        file = new File( file, "SIMPLE.txt" );
        assertTrue( file.exists() );
    }

    public void testSimpleBundlesWithType()
            throws Exception
    {
        final MavenProjectResourcesStub project = createTestProject( "default-simplebundles" );
        final ProcessRemoteResourcesMojo mojo = lookupProcessMojoWithSettings( project,
                new String[] {
                        "test:test:1.0:war"
                } );

        setupDefaultProject( project );

        String path = pathOf( new DefaultArtifact( "test",
                "test",
                VersionRange.createFromVersion( "1.0" ),
                null,
                "war",
                "",
                new DefaultArtifactHandler() ) );

        File file = new File( path );
        file.getParentFile().mkdirs();
        buildResourceBundle( "default-simplebundles-create",
                null,
                new String[] {"SIMPLE.txt"},
                file );


        mojo.execute();

        file = (File) getVariableValueFromObject( mojo, "outputDirectory" );
        file = new File( file, "SIMPLE.txt" );
        assertTrue( file.exists() );
    }

    public void testSimpleBundlesWithClassifier()
            throws Exception
    {
        final MavenProjectResourcesStub project = createTestProject( "default-simplebundles" );
        final ProcessRemoteResourcesMojo mojo = lookupProcessMojoWithSettings( project,
                new String[] {
                        "test:test:1.0:jar:test"
                } );

        setupDefaultProject( project );

        String path = pathOf( new DefaultArtifact( "test",
                "test",
                VersionRange.createFromVersion( "1.0" ),
                null,
                "jar",
                "test",
                new DefaultArtifactHandler() ) );

        File file = new File( path );
        file.getParentFile().mkdirs();
        buildResourceBundle( "default-simplebundles-create",
                null,
                new String[] {"SIMPLE.txt"},
                file );


        mojo.execute();

        file = (File) getVariableValueFromObject( mojo, "outputDirectory" );
        file = new File( file, "SIMPLE.txt" );
        assertTrue( file.exists() );
    }

    public void testVelocityUTF8()
            throws Exception
    {
        final MavenProjectResourcesStub project = createTestProject( "default-utf8" );
        final ProcessRemoteResourcesMojo mojo = lookupProcessMojoWithSettings( project,
                new String[] {
                        "test:test:1.2"
                } );

        setupDefaultProject( project );

        String path = pathOf( new DefaultArtifact( "test",
                "test",
                VersionRange.createFromVersion( "1.2" ),
                null,
                "jar",
                "",
                new DefaultArtifactHandler() ) );

        File file = new File( path );
        file.getParentFile().mkdirs();
        buildResourceBundle( "default-utf8-create",
                "UTF-8",
                new String[] {"UTF-8.bin.vm"},
                file );

        mojo.execute();

        file = (File) getVariableValueFromObject( mojo, "outputDirectory" );
        file = new File( file, "UTF-8.bin" );
        assertTrue( file.exists() );

        try ( InputStream in = Files.newInputStream( file.toPath() ) )
        {
            byte[] data = IOUtil.toByteArray( in );
            byte[] expected = "\u00E4\u00F6\u00FC\u00C4\u00D6\u00DC\u00DF".getBytes( StandardCharsets.UTF_8 );
            assertTrue( Arrays.equals( expected, data ) );
        }
    }

    public void testVelocityISO88591()
            throws Exception
    {
        final MavenProjectResourcesStub project = createTestProject( "default-iso88591" );
        final ProcessRemoteResourcesMojo mojo = lookupProcessMojoWithSettings( project,
                new String[] {
                        "test:test:1.3"
                } );

        setupDefaultProject( project );

        String path = pathOf( new DefaultArtifact( "test",
                "test",
                VersionRange.createFromVersion( "1.3" ),
                null,
                "jar",
                "",
                new DefaultArtifactHandler() ) );

        File file = new File( path );
        file.getParentFile().mkdirs();
        buildResourceBundle( "default-iso88591-create",
                "ISO-8859-1",
                new String[] {"ISO-8859-1.bin.vm"},
                file );

        mojo.execute();

        file = (File) getVariableValueFromObject( mojo, "outputDirectory" );
        file = new File( file, "ISO-8859-1.bin" );
        assertTrue( file.exists() );

        try ( InputStream in = Files.newInputStream( file.toPath() ) )
        {
            byte[] data = IOUtil.toByteArray( in );
            byte[] expected = "\u00E4\u00F6\u00FC\u00C4\u00D6\u00DC\u00DF".getBytes( StandardCharsets.ISO_8859_1 );
            assertTrue( Arrays.equals( expected, data ) );
        }

    }

    public void testFilteredBundles()
            throws Exception
    {
        final MavenProjectResourcesStub project = createTestProject( "default-filterbundles" );
        final ProcessRemoteResourcesMojo mojo = lookupProcessMojoWithSettings( project,
                new String[] {
                        "test:test:1.1"
                } );

        setupDefaultProject( project );

        String path = pathOf( new DefaultArtifact( "test",
                "test",
                VersionRange.createFromVersion( "1.1" ),
                null,
                "jar",
                "",
                new DefaultArtifactHandler() ) );

        File file = new File( path );
        file.getParentFile().mkdirs();
        buildResourceBundle( "default-filterbundles-create",
                null,
                new String[] {"FILTER.txt.vm"},
                file );


        mojo.execute();
        // executing a second time (example: forked lifecycle) should still work
        mojo.execute();

        file = (File) getVariableValueFromObject( mojo, "outputDirectory" );
        file = new File( file, "FILTER.txt" );
        assertTrue( file.exists() );

        String data = FileUtils.fileRead( file );
        assertTrue( data.contains( "project.name: Test Project default-filterbundles" ) );
        assertTrue( data.contains( "projectTimespan: 2007-2019" ) );
        assertTrue( data.contains( "projects: [" ) );
        assertTrue( data.contains( "projectsSortedByOrganization: {" ) );
    }

    public void testFilteredBundlesWithProjectProperties()
            throws Exception
    {
        final MavenProjectResourcesStub project = createTestProject( "default-filterbundles-two" );
        final ProcessRemoteResourcesMojo mojo =
                lookupProcessMojoWithSettings( project,
                        new String[] {"test-filtered-bundles:test-filtered-bundles:2"} );

        mojo.includeProjectProperties = true;
        setupDefaultProject( project );

        project.addProperty( "testingPropertyOne", "maven" );
        project.addProperty( "testingPropertyTwo", "rules" );

        String path = pathOf( new DefaultArtifact( "test-filtered-bundles", "test-filtered-bundles",
                VersionRange.createFromVersion( "2" ), null, "jar", "",
                new DefaultArtifactHandler() ) );

        File file = new File( path );
        file.getParentFile().mkdirs();
        buildResourceBundle( "default-filterbundles-two-create", null, new String[] {"PROPERTIES.txt.vm"}, file );

        mojo.execute();
        // executing a second time (example: forked lifecycle) should still work
        mojo.execute();

        file = (File) getVariableValueFromObject( mojo, "outputDirectory" );
        file = new File( file, "PROPERTIES.txt" );

        assertTrue( file.exists() );

        String data = FileUtils.fileRead( file );
        assertTrue( data.contains( "maven" ) );
        assertTrue( data.contains( "rules" ) );
    }

    protected void buildResourceBundle( String id,
                                        String sourceEncoding,
                                        String[] resourceNames,
                                        File jarName )
            throws Exception
    {
        final MavenProjectResourcesStub project = createTestProject( id );

        final File resourceDir = new File( project.getBasedir() + "/src/main/resources" );
        final BundleRemoteResourcesMojo mojo = lookupBundleMojoWithSettings( project, resourceDir, sourceEncoding );

        setupDefaultProject( project );

        for ( String resourceName2 : resourceNames )
        {
            File resource = new File( resourceDir, resourceName2 );
            URL source = getClass().getResource( "/" + resourceName2 );

            FileUtils.copyURLToFile( source, resource );
        }

        mojo.execute();

        File xmlFile = new File( project.getBasedir() + "/target/classes/META-INF/maven/remote-resources.xml" );
        assertTrue( xmlFile.exists() );

        String data = FileUtils.fileRead( xmlFile );
        for ( String resourceName1 : resourceNames )
        {
            assertTrue( data.contains( resourceName1 ) );
        }

        if ( null != jarName )
        {
            try ( OutputStream fos = Files.newOutputStream( jarName.toPath() );
                  JarOutputStream jar = new JarOutputStream( fos ) )
            {
                jar.putNextEntry( new ZipEntry( "META-INF/maven/remote-resources.xml" ) );
                jar.write( data.getBytes() );
                jar.closeEntry();

                for ( String resourceName : resourceNames )
                {
                    File resource = new File( resourceDir, resourceName );
                    try ( InputStream in = Files.newInputStream( resource.toPath() ) )
                    {
                        jar.putNextEntry( new ZipEntry( resourceName ) );
                        IOUtil.copy( in, jar );
                        jar.closeEntry();
                    }
                }
            }
        }
    }


    protected MavenProjectResourcesStub createTestProject( final String testName )
            throws Exception
    {
        // this will automatically create the isolated
        // test environment
        return new MavenProjectResourcesStub( testName );
    }

    protected void setupDefaultProject( final MavenProjectResourcesStub project )
            throws Exception
    {
        // put this on the root dir
        project.addFile( "pom.xml", MavenProjectBuildStub.ROOT_FILE );
        project.setInceptionYear( "2007" );
        // start creating the environment
        project.setupBuildEnvironment();
    }


    protected BundleRemoteResourcesMojo lookupBundleMojo()
            throws Exception
    {
        File pomFile = new File( getBasedir(), DEFAULT_BUNDLE_POM_PATH );
        BundleRemoteResourcesMojo mojo = (BundleRemoteResourcesMojo) lookupMojo( "bundle", pomFile );

        assertNotNull( mojo );

        return mojo;
    }

    protected BundleRemoteResourcesMojo lookupBundleMojoWithDefaultSettings( final MavenProject project )
            throws Exception
    {
        File resourceDir = new File( project.getBasedir() + "/src/main/resources" );
        return lookupBundleMojoWithSettings( project, resourceDir, null );
    }

    protected BundleRemoteResourcesMojo lookupBundleMojoWithSettings( final MavenProject project,
                                                                      File resourceDir, String sourceEncoding )
            throws Exception
    {
        final BundleRemoteResourcesMojo mojo = lookupBundleMojo();

        setVariableValueToObject( mojo, "resourcesDirectory", resourceDir );
        setVariableValueToObject( mojo, "outputDirectory", new File( project.getBuild().getOutputDirectory() ) );
        setVariableValueToObject( mojo, "sourceEncoding", sourceEncoding );
        return mojo;
    }

    protected ProcessRemoteResourcesMojo lookupProcessMojo()
            throws Exception
    {
        File pomFile = new File( getBasedir(), DEFAULT_PROCESS_POM_PATH );
        ProcessRemoteResourcesMojo mojo = (ProcessRemoteResourcesMojo) lookupMojo( "process", pomFile );

        assertNotNull( mojo );

        return mojo;
    }


    protected ProcessRemoteResourcesMojo lookupProcessMojoWithSettings( final MavenProject project,
                                                                        String[] bundles )
            throws Exception
    {
        return lookupProcessMojoWithSettings( project, new ArrayList<>( Arrays.asList( bundles ) ) );
    }

    protected ProcessRemoteResourcesMojo lookupProcessMojoWithSettings( final MavenProject project,
                                                                        ArrayList<String> bundles )
            throws Exception
    {
        final ProcessRemoteResourcesMojo mojo = lookupProcessMojo();

        DefaultRepositorySystemSession reposession = MavenRepositorySystemUtils.newSession();
        reposession.setLocalRepositoryManager( new SimpleLocalRepositoryManagerFactory()
                .newInstance( reposession, new LocalRepository( new File( LOCAL_REPO ) ) ) );

        DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setUserProperties( null );
        //request.setLocalRepository( reposession.getLocalRepository() );
        request.setGoals( Collections.singletonList( "install" ) );
        request.setBaseDirectory( project.getBasedir() );
        request.setStartTime( Calendar.getInstance().getTime() );

        // TODO: get rid of this uglyness/legacy
        MavenArtifactRepository localRepository = new MavenArtifactRepository();
        localRepository.setId( "local" );
        localRepository.setUrl( new File( LOCAL_REPO ).toURI().toASCIIString() );
        localRepository.setLayout( new DefaultRepositoryLayout() );
        request.setLocalRepository( localRepository );
        MavenSession session =
                new MavenSession( getContainer(), reposession, request, new DefaultMavenExecutionResult() );
        session.setProjects( Collections.singletonList( project ) );

        setVariableValueToObject( mojo, "outputDirectory", new File( project.getBuild().getOutputDirectory() ) );
        setVariableValueToObject( mojo, "resourceBundles", bundles );
        setVariableValueToObject( mojo, "mavenSession", session );
        setVariableValueToObject( mojo, "project", project );
        return mojo;
    }

    protected ProcessRemoteResourcesMojo lookupProcessMojoWithDefaultSettings( final MavenProject project )
            throws Exception
    {
        return lookupProcessMojoWithSettings( project, new ArrayList<>() );
    }

    private String pathOf( Artifact artifact )
    {
        String path = LOCAL_REPO + artifact.getGroupId().replace( ".", "/" ) + "/"
                + artifact.getArtifactId() + "/"
                + artifact.getBaseVersion() + "/"
                + artifact.getArtifactId() + "-" + artifact.getVersion();
        if ( artifact.getClassifier() != null && !artifact.getClassifier().isEmpty() )
        {
            path = path + "-" + artifact.getClassifier();
        }
        String ext = artifact.getArtifactHandler().getExtension();
        if ( ext == null ) {
            ext = artifact.getType();
        }
        path = path + "." + ext;
        return path;
    }
}
