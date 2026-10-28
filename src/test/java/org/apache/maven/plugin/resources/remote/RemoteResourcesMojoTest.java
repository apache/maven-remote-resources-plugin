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

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.resources.remote.stub.ArtifactStub;
import org.apache.maven.plugin.resources.remote.stub.MavenProjectBuildStub;
import org.apache.maven.plugin.resources.remote.stub.MavenProjectResourcesStub;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RemoteResources plugin Test Case
 */
@MojoTest(realRepositorySession = true)
class RemoteResourcesMojoTest {
    static final String DEFAULT_BUNDLE_POM_PATH = "target/test-classes/unit/rrmojotest/bundle-plugin-config.xml";
    static final String DEFAULT_PROCESS_POM_PATH = "target/test-classes/unit/rrmojotest/process-plugin-config.xml";

    // NEVER: on Windows the mojo leaves the bundle jars open, so deleting them fails
    @TempDir(cleanup = CleanupMode.NEVER)
    private File localRepo;

    @Inject
    private MavenSession session;

    @BeforeEach
    void setUp() {
        session.getRequest().setLocalRepositoryPath(localRepo);
    }

    /**
     * check test environment
     *
     * @throws Exception if any exception occurs
     */
    @Test
    void testTestEnvironment(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    void testNoBundles(@InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-nobundles");
        configureProcessMojo(mojo, project, new ArrayList<>());

        setupDefaultProject(project);

        mojo.execute();
    }

    @Test
    void testConfigureLocatorRequiresProjectFile(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-null-pom");
        configureProcessMojo(mojo, project, new ArrayList<>());

        setupDefaultProject(project);
        project.setFile(null);

        try {
            mojo.execute();
            fail("expected MojoExecutionException when the project has no POM file");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage().contains("no POM file"));
        }
    }

    @Test
    void testRemoteResourceCannotEscapeOutputDirectory() throws Exception {
        File outputDirectory = new File("target/remote-resource-output");
        File canonicalOutputDirectory = outputDirectory.getCanonicalFile();

        try {
            AbstractProcessRemoteResourcesMojo.resolveOutputFile(
                    outputDirectory, "../outside.txt", canonicalOutputDirectory);
            fail("expected a resource outside the output directory to be rejected");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage().contains("must remain within the output directory"));
        }

        try {
            AbstractProcessRemoteResourcesMojo.resolveOutputFile(
                    outputDirectory,
                    new File(outputDirectory.getParentFile(), "absolute.txt").getAbsolutePath(),
                    canonicalOutputDirectory);
            fail("expected an absolute resource path outside the output directory to be rejected");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage().contains("Absolute remote resource paths are not allowed"));
        }

        File outputFile = AbstractProcessRemoteResourcesMojo.resolveOutputFile(
                outputDirectory, "nested/resource.txt", canonicalOutputDirectory);
        assertEquals(new File(outputDirectory, "nested/resource.txt").getCanonicalFile(), outputFile);
    }

    @Test
    void testRemoteResourceCannotEscapeOutputDirectoryThroughSymlink() throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("remote-resource-path");
        Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("output"));
        Path outsideDirectory = Files.createDirectory(temporaryDirectory.resolve("outside"));
        try {
            Files.createSymbolicLink(outputDirectory.resolve("external"), outsideDirectory);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "not supported on this platform: " + e);
        }

        File output = outputDirectory.toFile();
        File canonicalOutputDirectory = output.getCanonicalFile();
        try {
            AbstractProcessRemoteResourcesMojo.resolveOutputFile(
                    output, "external/escaped.txt", canonicalOutputDirectory);
            fail("expected a symlink target outside the output directory to be rejected");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage().contains("must remain within the output directory"));
        }
    }

    @Test
    void testRemoteResourceResolvesNestedSymlinksBeforeCheckingContainment() throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("remote-resource-nested-path");
        Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("output"));
        Path insideDirectory = Files.createDirectory(outputDirectory.resolve("inside"));
        Path outsideDirectory = Files.createDirectory(temporaryDirectory.resolve("outside"));
        try {
            Files.createSymbolicLink(outputDirectory.resolve("inside-link"), insideDirectory);
            Files.createSymbolicLink(insideDirectory.resolve("outside-link"), outsideDirectory);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "not supported on this platform: " + e);
        }

        File output = outputDirectory.toFile();
        File canonicalOutputDirectory = output.getCanonicalFile();
        File safeOutputFile = AbstractProcessRemoteResourcesMojo.resolveOutputFile(
                output, "inside-link/not-created/resource.txt", canonicalOutputDirectory);
        assertEquals(
                insideDirectory.toRealPath().resolve("not-created/resource.txt").toFile(), safeOutputFile);

        try {
            AbstractProcessRemoteResourcesMojo.resolveOutputFile(
                    output, "inside-link/outside-link/not-created/resource.txt", canonicalOutputDirectory);
            fail("expected a nested symlink target outside the output directory to be rejected");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage().contains("must remain within the output directory"));
        }
    }

    @Test
    void testRemoteResourceFailsClosedWhenPathCannotBeInspected() throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("remote-resource-inaccessible-path");
        Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("output"));
        Path restrictedDirectory = Files.createDirectory(outputDirectory.resolve("restricted"));
        Path outsideDirectory = Files.createDirectory(temporaryDirectory.resolve("outside"));

        EnumSet<PosixFilePermission> originalPermissions;
        try {
            originalPermissions = EnumSet.copyOf(Files.getPosixFilePermissions(restrictedDirectory));
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "not supported on this platform: " + e);
            return;
        }

        try {
            Files.createSymbolicLink(restrictedDirectory.resolve("external"), outsideDirectory);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "not supported on this platform: " + e);
        }

        try {
            Files.setPosixFilePermissions(
                    restrictedDirectory, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "not supported on this platform: " + e);
        }

        try {
            AbstractProcessRemoteResourcesMojo.resolveOutputFile(
                    outputDirectory.toFile(),
                    "restricted/external/escaped.txt",
                    outputDirectory.toFile().getCanonicalFile());
            fail("expected unreadable path components to be rejected");
        } catch (IOException expected) {
            // An inaccessible component must not be treated as a missing path element.
        } finally {
            Files.setPosixFilePermissions(restrictedDirectory, originalPermissions);
        }
    }

    @Test
    void testMalformedSupplementalModelFailsWithMojoExecutionException(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-malformedsupplement");
        configureProcessMojo(mojo, project, new ArrayList<>());

        setupDefaultProject(project);

        File supplementalModelsFile = new File(project.getBasedir(), "supplemental-models.xml");
        FileUtils.fileWrite(
                supplementalModelsFile.getAbsolutePath(),
                "<supplementalDataModels>"
                        + "<supplement>"
                        + "<project>"
                        + "<groupId>test</groupId>"
                        + "<artifactId>test</artifactId>"
                        + "<version>1.0</version>"
                        + "<notAValidElement>whatever</notAValidElement>"
                        + "</project>"
                        + "</supplement>"
                        + "</supplementalDataModels>");

        setVariableValueToObject(mojo, "supplementalModels", new String[] {supplementalModelsFile.getAbsolutePath()});

        try {
            mojo.execute();
            fail("Expected a MojoExecutionException for a malformed supplemental model entry");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage().contains("Unable to parse supplemental XML"), e.getMessage());
        }
    }

    @Test
    void testCreateBundle(@InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo mojo)
            throws Exception {
        List<String> resources =
                Arrays.asList("FILTER.txt.vm", "ISO-8859-1.bin.vm", "PROPERTIES.txt.vm", "SIMPLE.txt", "UTF-8.bin.vm");
        buildResourceBundle(mojo, "default-createbundle", null, resources.toArray(new String[0]), null);
    }

    @Test
    void testSimpleBundles(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-simplebundles");
        configureProcessMojo(mojo, project, new ArrayList<>(Arrays.asList("test:test:1.0")));

        setupDefaultProject(project);

        String path = pathOf(new DefaultArtifact(
                "test", "test", VersionRange.createFromVersion("1.0"), null, "jar", "", new DefaultArtifactHandler()));

        File file = new File(path);
        file.getParentFile().mkdirs();
        buildResourceBundle(bundleMojo, "default-simplebundles-create", null, new String[] {"SIMPLE.txt"}, file);

        mojo.execute();

        file = getVariableValueFromObject(mojo, "outputDirectory");
        file = new File(file, "SIMPLE.txt");
        assertTrue(file.exists());
    }

    @Test
    void testSimpleBundlesWithType(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-simplebundles");
        configureProcessMojo(mojo, project, new ArrayList<>(Arrays.asList("test:test:1.0:war")));

        setupDefaultProject(project);

        String path = pathOf(new DefaultArtifact(
                "test", "test", VersionRange.createFromVersion("1.0"), null, "war", "", new DefaultArtifactHandler()));

        File file = new File(path);
        file.getParentFile().mkdirs();
        buildResourceBundle(bundleMojo, "default-simplebundles-create", null, new String[] {"SIMPLE.txt"}, file);

        mojo.execute();

        file = getVariableValueFromObject(mojo, "outputDirectory");
        file = new File(file, "SIMPLE.txt");
        assertTrue(file.exists());
    }

    @Test
    void testSimpleBundlesWithClassifier(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-simplebundles");
        configureProcessMojo(mojo, project, new ArrayList<>(Arrays.asList("test:test:1.0:jar:test")));

        setupDefaultProject(project);

        String path = pathOf(new DefaultArtifact(
                "test",
                "test",
                VersionRange.createFromVersion("1.0"),
                null,
                "jar",
                "test",
                new DefaultArtifactHandler()));

        File file = new File(path);
        file.getParentFile().mkdirs();
        buildResourceBundle(bundleMojo, "default-simplebundles-create", null, new String[] {"SIMPLE.txt"}, file);

        mojo.execute();

        file = getVariableValueFromObject(mojo, "outputDirectory");
        file = new File(file, "SIMPLE.txt");
        assertTrue(file.exists());
    }

    @Test
    void testVelocityUTF8(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-utf8");
        configureProcessMojo(mojo, project, new ArrayList<>(Arrays.asList("test:test:1.2")));

        setupDefaultProject(project);

        String path = pathOf(new DefaultArtifact(
                "test", "test", VersionRange.createFromVersion("1.2"), null, "jar", "", new DefaultArtifactHandler()));

        File file = new File(path);
        file.getParentFile().mkdirs();
        buildResourceBundle(bundleMojo, "default-utf8-create", "UTF-8", new String[] {"UTF-8.bin.vm"}, file);

        mojo.execute();

        file = getVariableValueFromObject(mojo, "outputDirectory");
        file = new File(file, "UTF-8.bin");
        assertTrue(file.exists());

        byte[] data = Files.readAllBytes(file.toPath());
        byte[] expected = "\u00E4\u00F6\u00FC\u00C4\u00D6\u00DC\u00DF".getBytes(StandardCharsets.UTF_8);
        assertTrue(Arrays.equals(expected, data));
    }

    @Test
    void testClassLoaderIsClosedAfterExecution(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-closeclassloader");
        configureProcessMojo(mojo, project, new ArrayList<>(Arrays.asList("test:test:1.0")));

        setupDefaultProject(project);

        String path = pathOf(new DefaultArtifact(
                "test", "test", VersionRange.createFromVersion("1.0"), null, "jar", "", new DefaultArtifactHandler()));

        File file = new File(path);
        file.getParentFile().mkdirs();
        buildResourceBundle(bundleMojo, "default-closeclassloader-create", null, new String[] {"SIMPLE.txt"}, file);

        mojo.execute();

        RemoteResourcesClassLoader classLoader =
                (RemoteResourcesClassLoader) getVariableValueFromObject(mojo, "classLoader");
        assertNotNull(classLoader);
        // resources from the bundle jars must no longer be served by the closed classloader
        assertNull(classLoader.getResource("SIMPLE.txt"));
    }

    @Test
    void testVelocityISO88591(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-iso88591");
        configureProcessMojo(mojo, project, new ArrayList<>(Arrays.asList("test:test:1.3")));

        setupDefaultProject(project);

        String path = pathOf(new DefaultArtifact(
                "test", "test", VersionRange.createFromVersion("1.3"), null, "jar", "", new DefaultArtifactHandler()));

        File file = new File(path);
        file.getParentFile().mkdirs();
        buildResourceBundle(
                bundleMojo, "default-iso88591-create", "ISO-8859-1", new String[] {"ISO-8859-1.bin.vm"}, file);

        mojo.execute();

        file = getVariableValueFromObject(mojo, "outputDirectory");
        file = new File(file, "ISO-8859-1.bin");
        assertTrue(file.exists());

        byte[] data = Files.readAllBytes(file.toPath());
        byte[] expected = "\u00E4\u00F6\u00FC\u00C4\u00D6\u00DC\u00DF".getBytes(StandardCharsets.ISO_8859_1);
        assertTrue(Arrays.equals(expected, data));
    }

    @Test
    void testFilteredBundles(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-filterbundles");
        configureProcessMojo(mojo, project, new ArrayList<>(Arrays.asList("test:test:1.1")));

        setupDefaultProject(project);

        String path = pathOf(new DefaultArtifact(
                "test", "test", VersionRange.createFromVersion("1.1"), null, "jar", "", new DefaultArtifactHandler()));

        File file = new File(path);
        file.getParentFile().mkdirs();
        buildResourceBundle(bundleMojo, "default-filterbundles-create", null, new String[] {"FILTER.txt.vm"}, file);

        mojo.execute();
        // executing a second time (example: forked lifecycle) should still work
        mojo.execute();

        file = getVariableValueFromObject(mojo, "outputDirectory");
        file = new File(file, "FILTER.txt");
        assertTrue(file.exists());

        String data = FileUtils.fileRead(file);
        assertTrue(data.contains("project.name: Test Project default-filterbundles"));
        assertTrue(data.contains("projectTimespan: 2007-2019"));
        assertTrue(data.contains("projects: ["));
        assertTrue(data.contains("projectsSortedByOrganization: {"));
    }

    @Test
    void testFilteredBundlesWithProjectProperties(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-filterbundles-two");
        configureProcessMojo(
                mojo, project, new ArrayList<>(Arrays.asList("test-filtered-bundles:test-filtered-bundles:2")));

        mojo.includeProjectProperties = true;
        setupDefaultProject(project);

        project.addProperty("testingPropertyOne", "maven");
        project.addProperty("testingPropertyTwo", "rules");

        String path = pathOf(new DefaultArtifact(
                "test-filtered-bundles",
                "test-filtered-bundles",
                VersionRange.createFromVersion("2"),
                null,
                "jar",
                "",
                new DefaultArtifactHandler()));

        File file = new File(path);
        file.getParentFile().mkdirs();
        buildResourceBundle(
                bundleMojo, "default-filterbundles-two-create", null, new String[] {"PROPERTIES.txt.vm"}, file);

        mojo.execute();
        // executing a second time (example: forked lifecycle) should still work
        mojo.execute();

        file = getVariableValueFromObject(mojo, "outputDirectory");
        file = new File(file, "PROPERTIES.txt");

        assertTrue(file.exists());

        String data = FileUtils.fileRead(file);
        assertTrue(data.contains("maven"));
        assertTrue(data.contains("rules"));
    }

    @Test
    void testUseProjectFilesRendersVelocityTemplate(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-useprojectfiles");
        configureProcessMojo(mojo, project, Arrays.asList("test:test:1.4"));

        setupDefaultProject(project);

        FileUtils.fileWrite(
                new File(project.getBasedir(), "FILTER.txt.vm").getAbsolutePath(), "local override: $project.name");

        String path = pathOf(new DefaultArtifact(
                "test", "test", VersionRange.createFromVersion("1.4"), null, "jar", "", new DefaultArtifactHandler()));

        File file = new File(path);
        file.getParentFile().mkdirs();
        buildResourceBundle(bundleMojo, "default-useprojectfiles-create", null, new String[] {"FILTER.txt.vm"}, file);

        setVariableValueToObject(mojo, "useProjectFiles", true);

        mojo.execute();

        File outputFile = new File((File) getVariableValueFromObject(mojo, "outputDirectory"), "FILTER.txt");
        String data = FileUtils.fileRead(outputFile);
        assertTrue(data.contains("local override: Test Project default-useprojectfiles"));
    }

    @Test
    void testProjectsDoesNotMutateProjectArtifactVersions(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-nomutate");
        configureProcessMojo(mojo, project, Collections.emptyList());

        setupDefaultProject(project);

        setVariableValueToObject(mojo, "supplementModels", new HashMap<>());

        ArtifactStub snapshotArtifact = new ArtifactStub();
        snapshotArtifact.setGroupId("org.example");
        snapshotArtifact.setArtifactId("snapshot-dep");
        snapshotArtifact.setVersion("1.0-SNAPSHOT");

        project.setArtifacts(Collections.singleton(snapshotArtifact));

        mojo.getProjects();

        assertEquals("1.0-SNAPSHOT", snapshotArtifact.getVersion());
        assertSame(snapshotArtifact, project.getArtifacts().iterator().next());
    }

    @Test
    void testValidateRejectsDescriptorWithEmptySegment(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-validate");
        configureProcessMojo(mojo, project, Arrays.asList("org.example:dep::jar"));

        try {
            mojo.validate();
            fail("Expected validate() to reject a resource bundle descriptor with a missing version");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage().contains("must specify a groupId, artifactId"));
        }
    }

    @Test
    void testFindReactorProjectMatchesTimestampedSnapshot() throws Exception {
        MavenProject reactorProject = new MavenProject(new org.apache.maven.model.Model());
        reactorProject.setGroupId("com.example");
        reactorProject.setArtifactId("reactor-project");
        reactorProject.setVersion("1.0-SNAPSHOT");

        Artifact artifact = new DefaultArtifact(
                "com.example",
                "reactor-project",
                VersionRange.createFromVersion("1.0-20260926.120000-1"),
                null,
                "jar",
                "",
                new DefaultArtifactHandler());

        assertSame(
                reactorProject,
                AbstractProcessRemoteResourcesMojo.findReactorProject(
                        artifact, Collections.singletonList(reactorProject)));

        Artifact externalArtifact = new DefaultArtifact(
                "com.example",
                "external-project",
                VersionRange.createFromVersion("1.0"),
                null,
                "jar",
                "",
                new DefaultArtifactHandler());
        assertNull(AbstractProcessRemoteResourcesMojo.findReactorProject(
                externalArtifact, Collections.singletonList(reactorProject)));
    }

    @Test
    void testGetProjectsBuildsOnlyExternalArtifacts(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo)
            throws Exception {
        MavenProjectResourcesStub project = createTestProject("reactor-projects");
        configureProcessMojo(mojo, project, Collections.emptyList());
        setupDefaultProject(project);

        MavenProject reactorProject = new MavenProject(new org.apache.maven.model.Model());
        reactorProject.setGroupId("com.example");
        reactorProject.setArtifactId("reactor-project");
        reactorProject.setVersion("1.0-SNAPSHOT");

        Artifact reactorArtifact = new DefaultArtifact(
                "com.example",
                "reactor-project",
                VersionRange.createFromVersion("1.0-20260926.120000-1"),
                "compile",
                "jar",
                "",
                new DefaultArtifactHandler());
        Artifact externalArtifact = new DefaultArtifact(
                "com.example",
                "external-project",
                VersionRange.createFromVersion("1.0"),
                "compile",
                "jar",
                "",
                new DefaultArtifactHandler());
        project.setArtifacts(new LinkedHashSet<>(Arrays.asList(reactorArtifact, externalArtifact)));
        reactorProject.setArtifact(reactorArtifact);

        // the injected session is a mock, so the mojo gets a real one that holds the reactor
        MavenSession reactorSession = new MavenSession(
                null,
                session.getRepositorySession(),
                new DefaultMavenExecutionRequest(),
                new DefaultMavenExecutionResult());
        reactorSession.setProjects(Arrays.asList(project, reactorProject));
        setVariableValueToObject(mojo, "mavenSession", reactorSession);

        MavenProject externalProject = new MavenProject(new org.apache.maven.model.Model());
        externalProject.setGroupId("com.example");
        externalProject.setArtifactId("external-project");
        externalProject.setVersion("1.0");
        externalProject.setArtifact(externalArtifact);

        ProjectBuildingResult buildingResult = (ProjectBuildingResult) Proxy.newProxyInstance(
                ProjectBuildingResult.class.getClassLoader(),
                new Class<?>[] {ProjectBuildingResult.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getProject".equals(method.getName())) {
                            return externalProject;
                        }
                        return null;
                    }
                });
        List<Artifact> builtArtifacts = new ArrayList<>();
        ProjectBuilder projectBuilder = (ProjectBuilder) Proxy.newProxyInstance(
                ProjectBuilder.class.getClassLoader(), new Class<?>[] {ProjectBuilder.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("build".equals(method.getName())
                                && args != null
                                && args.length == 2
                                && args[0] instanceof Artifact) {
                            builtArtifacts.add((Artifact) args[0]);
                            return buildingResult;
                        }
                        return null;
                    }
                });
        setVariableValueToObject(mojo, "projectBuilder", projectBuilder);
        setVariableValueToObject(mojo, "supplementModels", new HashMap<>());

        List<MavenProject> projects = mojo.getProjects();

        assertEquals(1, builtArtifacts.size());
        assertSame(externalArtifact, builtArtifacts.get(0));
        assertTrue(projects.contains(reactorProject));
        assertTrue(projects.contains(externalProject));
    }

    @Test
    void testFilteredAppendedResource(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-appended-filtered-resource");
        configureProcessMojo(mojo, project, Arrays.asList("test:test:1.6"));

        setupDefaultProject(project);

        String path = pathOf(new DefaultArtifact(
                "test", "test", VersionRange.createFromVersion("1.6"), null, "jar", "", new DefaultArtifactHandler()));
        File bundleJar = new File(path);
        bundleJar.getParentFile().mkdirs();
        buildResourceBundle(
                bundleMojo,
                "default-appended-filtered-resource-create",
                null,
                new String[] {"BUNDLE_FILTER.txt.vm", "APPENDED_FILTER.txt"},
                bundleJar);

        File appendedResourcesDirectory = new File(project.getBasedir(), "src/main/appended-resources");
        appendedResourcesDirectory.mkdirs();
        FileUtils.fileWrite(
                new File(appendedResourcesDirectory, "APPENDED_FILTER.txt.vm").getAbsolutePath(),
                "appended: $project.name\n#parse('BUNDLE_FILTER.txt.vm')");
        setVariableValueToObject(mojo, "appendedResourcesDirectory", appendedResourcesDirectory);

        mojo.execute();

        File outputDirectory = (File) getVariableValueFromObject(mojo, "outputDirectory");
        String appendedData = FileUtils.fileRead(new File(outputDirectory, "APPENDED_FILTER.txt"));
        assertTrue(appendedData.contains("appended: Test Project default-appended-filtered-resource"));
        assertTrue(appendedData.contains("project.name: Test Project default-appended-filtered-resource"));
        String bundleData = FileUtils.fileRead(new File(outputDirectory, "BUNDLE_FILTER.txt"));
        assertTrue(bundleData.contains("project.name: Test Project default-appended-filtered-resource"));
    }

    @Test
    void testAppendedVelocityFailureIdentifiesTemplate(
            @InjectMojo(goal = "process", pom = DEFAULT_PROCESS_POM_PATH) ProcessRemoteResourcesMojo mojo,
            @InjectMojo(goal = "bundle", pom = DEFAULT_BUNDLE_POM_PATH) BundleRemoteResourcesMojo bundleMojo)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject("default-appended-velocity-failure");
        configureProcessMojo(mojo, project, Arrays.asList("test:test:1.5"));

        setupDefaultProject(project);

        String path = pathOf(new DefaultArtifact(
                "test", "test", VersionRange.createFromVersion("1.5"), null, "jar", "", new DefaultArtifactHandler()));
        File bundleJar = new File(path);
        bundleJar.getParentFile().mkdirs();
        buildResourceBundle(
                bundleMojo,
                "default-appended-velocity-failure-create",
                null,
                new String[] {"APPENDED_FILTER.txt"},
                bundleJar);

        File appendedResourcesDirectory = new File(project.getBasedir(), "src/main/appended-resources");
        appendedResourcesDirectory.mkdirs();
        FileUtils.fileWrite(
                new File(appendedResourcesDirectory, "APPENDED_FILTER.txt.vm").getAbsolutePath(),
                "#parse('missing-appended-template.vm')");
        setVariableValueToObject(mojo, "appendedResourcesDirectory", appendedResourcesDirectory);

        try {
            mojo.execute();
            fail("Expected the invalid appended template to fail rendering");
        } catch (MojoExecutionException expected) {
            assertTrue(expected.getMessage().contains("APPENDED_FILTER.txt.vm"), expected.getMessage());
        }
    }

    private void buildResourceBundle(
            BundleRemoteResourcesMojo mojo, String id, String sourceEncoding, String[] resourceNames, File jarName)
            throws Exception {
        final MavenProjectResourcesStub project = createTestProject(id);

        final File resourceDir = new File(project.getBasedir(), "src/main/resources");
        configureBundleMojo(mojo, project, resourceDir, sourceEncoding);

        setupDefaultProject(project);

        for (String resourceName2 : resourceNames) {
            File resource = new File(resourceDir, resourceName2);
            URL source = getClass().getResource("/" + resourceName2);

            FileUtils.copyURLToFile(source, resource);
        }

        mojo.execute();

        File xmlFile = new File(project.getBasedir(), "target/classes/META-INF/maven/remote-resources.xml");
        assertTrue(xmlFile.exists());

        String data = FileUtils.fileRead(xmlFile);

        List<String> expectedOrder = new ArrayList<>(Arrays.asList(resourceNames));
        Collections.sort(expectedOrder);
        assertContainsInOrder(data, expectedOrder);

        if (null != jarName) {
            try (OutputStream fos = Files.newOutputStream(jarName.toPath());
                    JarOutputStream jar = new JarOutputStream(fos)) {
                jar.putNextEntry(new ZipEntry("META-INF/maven/remote-resources.xml"));
                jar.write(data.getBytes());
                jar.closeEntry();

                for (String resourceName : resourceNames) {
                    File resource = new File(resourceDir, resourceName);
                    try (InputStream in = Files.newInputStream(resource.toPath())) {
                        jar.putNextEntry(new ZipEntry(resourceName));
                        IOUtil.copy(in, jar);
                        jar.closeEntry();
                    }
                }
            }
        }
    }

    /**
     * Asserts that {@code actual} contains every string in {@code expected}, in order, allowing
     * unrelated text in between (equivalent to Hamcrest's {@code stringContainsInOrder}).
     */
    private static void assertContainsInOrder(String actual, List<String> expected) {
        int fromIndex = 0;
        for (String s : expected) {
            int idx = actual.indexOf(s, fromIndex);
            assertTrue(idx >= 0, "expected \"" + s + "\" to occur in order in: " + actual);
            fromIndex = idx + s.length();
        }
    }

    private MavenProjectResourcesStub createTestProject(final String testName) throws Exception {
        // this will automatically create the isolated
        // test environment
        return new MavenProjectResourcesStub(testName);
    }

    private void setupDefaultProject(final MavenProjectResourcesStub project) throws Exception {
        // put this on the root dir
        project.addFile("pom.xml", MavenProjectBuildStub.ROOT_FILE);
        project.setInceptionYear("2007");
        // start creating the environment
        project.setupBuildEnvironment();
    }

    private void configureBundleMojo(
            BundleRemoteResourcesMojo mojo, MavenProject project, File resourceDir, String sourceEncoding)
            throws Exception {
        setVariableValueToObject(mojo, "resourcesDirectory", resourceDir);
        setVariableValueToObject(
                mojo, "outputDirectory", new File(project.getBuild().getOutputDirectory()));
        setVariableValueToObject(mojo, "sourceEncoding", sourceEncoding);
    }

    private void configureProcessMojo(ProcessRemoteResourcesMojo mojo, MavenProject project, List<String> bundles)
            throws Exception {
        setVariableValueToObject(
                mojo, "outputDirectory", new File(project.getBuild().getOutputDirectory()));
        setVariableValueToObject(mojo, "resourceBundles", bundles);
        setVariableValueToObject(mojo, "mavenSession", session);
        setVariableValueToObject(mojo, "project", project);
    }

    private String pathOf(Artifact artifact) {
        String path = localRepo.getPath() + File.separator
                + artifact.getGroupId().replace(".", File.separator) + File.separator
                + artifact.getArtifactId() + File.separator
                + artifact.getBaseVersion() + File.separator
                + artifact.getArtifactId() + "-" + artifact.getVersion();
        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
            path = path + "-" + artifact.getClassifier();
        }
        String ext = artifact.getArtifactHandler().getExtension();
        if (ext == null) {
            ext = artifact.getType();
        }
        path = path + "." + ext;
        return path;
    }
}
