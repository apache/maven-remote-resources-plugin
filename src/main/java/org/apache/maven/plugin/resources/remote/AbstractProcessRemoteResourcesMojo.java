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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Resource;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.resources.remote.io.xpp3.RemoteResourcesBundleXpp3Reader;
import org.apache.maven.plugin.resources.remote.io.xpp3.SupplementalDataModelXpp3Reader;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.GroupIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFileFilterRequest;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.codehaus.plexus.resource.ResourceManager;
import org.codehaus.plexus.resource.loader.FileResourceLoader;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * <p>
 * Pull down resourceBundles containing remote resources and process the resources contained inside. When that is done,
 * the resources are injected into the current (in-memory) Maven project, making them available to the process-resources
 * phase.
 * </p>
 * <p>
 * Resources that end in ".vm" are treated as Velocity templates. For those, the ".vm" is stripped off for the final
 * artifact name and it's fed through Velocity to have properties expanded, conditions processed, etc...
 * </p>
 * Resources that don't end in ".vm" are copied "as is".
 * <p>
 * This is a support abstract class, with two non-aggregating and aggregating implementations.
 * </p>
 */
public abstract class AbstractProcessRemoteResourcesMojo extends AbstractMojo {
    private static final String TEMPLATE_SUFFIX = ".vm";

    /**
     * <p>
     * In cases where a local resource overrides one from a remote resource bundle, that resource should be filtered if
     * the resource set specifies it. In those cases, this parameter defines the list of delimiters for filterable
     * expressions. These delimiters are specified in the form 'beginToken*endToken'. If no '*' is given, the delimiter
     * is assumed to be the same for start and end.
     * </p>
     * <p>
     * So, the default filtering delimiters might be specified as:
     * </p>
     *
     * <pre>
     * &lt;delimiters&gt;
     *   &lt;delimiter&gt;${*}&lt;/delimiter&gt;
     *   &lt;delimiter&gt;@&lt;/delimiter&gt;
     * &lt;/delimiters&gt;
     * </pre>
     * Since the '@' delimiter is the same on both ends, we don't need to specify '@*@' (though we can).
     *
     * @since 1.1
     */
    @Parameter
    protected List<String> filterDelimiters;

    /**
     * @since 1.1
     */
    @Parameter(defaultValue = "true")
    protected boolean useDefaultFilterDelimiters;

    /**
     * The character encoding scheme to be applied when filtering resources.
     */
    @Parameter(property = "encoding", defaultValue = "${project.build.sourceEncoding}")
    protected String encoding;

    /**
     * The directory where processed resources will be placed for packaging.
     */
    @Parameter(defaultValue = "${project.build.directory}/maven-shared-archive-resources")
    private File outputDirectory;

    /**
     * The directory containing extra information appended to the generated resources.
     */
    @Parameter(defaultValue = "${basedir}/src/main/appended-resources")
    private File appendedResourcesDirectory;

    /**
     * Supplemental model data. Useful when processing
     * artifacts with incomplete POM metadata.
     * <p/>
     * By default, this Mojo looks for supplemental model data in the file
     * "<code>${appendedResourcesDirectory}/supplemental-models.xml</code>".
     *
     * @since 1.0-alpha-5
     */
    @Parameter
    private String[] supplementalModels;

    /**
     * List of artifacts that are added to the search path when looking
     * for supplementalModels, expressed with <code>groupId:artifactId:version[:type[:classifier]]</code> format.
     *
     * @since 1.1
     */
    @Parameter
    private List<String> supplementalModelArtifacts;

    /**
     * The resource bundles that will be retrieved and processed,
     * expressed with <code>groupId:artifactId:version[:type[:classifier]]</code> format.
     */
    @Parameter(required = true)
    private List<String> resourceBundles;

    /**
     * Skip remote-resource processing
     *
     * @since 1.0-alpha-5
     */
    @Parameter(property = "remoteresources.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Attaches the resources to the main build of the project as a resource directory.
     *
     * @since 1.5
     */
    @Parameter(defaultValue = "true", property = "attachToMain")
    private boolean attachToMain;

    /**
     * Attaches the resources to the test build of the project as a resource directory.
     *
     * @since 1.5
     */
    @Parameter(defaultValue = "true", property = "attachToTest")
    private boolean attachToTest;

    /**
     * Additional properties to be passed to Velocity.
     * Several properties are automatically added:<ul>
     * <li><code>project</code> - the current MavenProject </li>
     * <li><code>projects</code> - the list of dependency projects</li>
     * <li><code>projectsSortedByOrganization</code> - the list of dependency projects sorted by organization</li>
     * <li><code>projectTimespan</code> - the timespan of the current project (requires inceptionYear in pom)</li>
     * <li><code>locator</code> - the ResourceManager that can be used to retrieve additional resources</li>
     * </ul>
     * See <a
     * href="https://maven.apache.org/ref/current/maven-project/apidocs/org/apache/maven/project/MavenProject.html"> the
     * javadoc for MavenProject</a> for information about the properties on the MavenProject.
     */
    @Parameter
    protected Map<String, Object> properties = new HashMap<>();

    /**
     * Whether to include properties defined in the project when filtering resources.
     *
     * @since 1.2
     */
    @Parameter(defaultValue = "false")
    protected boolean includeProjectProperties = false;

    /**
     * When the result of velocity transformation fits in memory, it is compared with the actual contents on disk
     * to eliminate unnecessary destination file overwrite. This improves build times since further build steps
     * typically rely on the modification date.
     *
     * @since 1.6
     */
    @Parameter(defaultValue = "5242880")
    protected int velocityFilterInMemoryThreshold = 5 * 1024 * 1024;

    /**
     * The Maven session.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession mavenSession;

    /**
     * The current project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * Scope to include. An Empty string indicates all scopes (default is "runtime").
     *
     * @since 1.0
     */
    @Parameter(property = "includeScope", defaultValue = "runtime")
    protected String includeScope;

    /**
     * Scope to exclude. An Empty string indicates no scopes (default).
     *
     * @since 1.0
     */
    @Parameter(property = "excludeScope", defaultValue = "")
    protected String excludeScope;

    /**
     * When resolving project dependencies, specify the scopes to include.
     * The default is the same as "includeScope" if there are no exclude scopes set.
     * Otherwise, it defaults to "test" to grab all the dependencies so the
     * exclude filters can filter out what is not needed.
     *
     * @since 1.5
     */
    @Parameter
    protected String[] resolveScopes;

    /**
     * Comma separated list of Artifact names to exclude.
     *
     * @since 1.0
     */
    @Parameter(property = "excludeArtifactIds", defaultValue = "")
    protected String excludeArtifactIds;

    /**
     * Comma separated list of Artifact names to include.
     *
     * @since 1.0
     */
    @Parameter(property = "includeArtifactIds", defaultValue = "")
    protected String includeArtifactIds;

    /**
     * Comma separated list of GroupId Names to exclude.
     *
     * @since 1.0
     */
    @Parameter(property = "excludeGroupIds", defaultValue = "")
    protected String excludeGroupIds;

    /**
     * Comma separated list of GroupIds to include.
     *
     * @since 1.0
     */
    @Parameter(property = "includeGroupIds", defaultValue = "")
    protected String includeGroupIds;

    /**
     * If we should exclude transitive dependencies
     *
     * @since 1.0
     */
    @Parameter(property = "excludeTransitive", defaultValue = "false")
    protected boolean excludeTransitive;

    /**
     * Timestamp for reproducible output archive entries, either formatted as ISO 8601
     * <code>yyyy-MM-dd'T'HH:mm:ssXXX</code> or as an int representing seconds since the epoch (like
     * <a href="https://reproducible-builds.org/docs/source-date-epoch/">SOURCE_DATE_EPOCH</a>).
     */
    @Parameter(defaultValue = "${project.build.outputTimestamp}")
    private String outputTimestamp;

    @Component
    protected RepositorySystem repoSystem;

    /**
     * Filtering support, for local resources that override those in the remote bundle.
     */
    @Component
    private MavenFileFilter fileFilter;

    @Component
    private ResourceManager locator;

    @Component
    private ProjectBuilder projectBuilder;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Map of artifacts to supplemental project object models.
     */
    private Map<String, Model> supplementModels;

    /**
     * Merges supplemental data model with artifact metadata. Useful when processing artifacts with
     * incomplete POM metadata.
     */
    private final ModelInheritanceAssembler inheritanceAssembler = new ModelInheritanceAssembler();

    private VelocityEngine velocity;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping remote resources execution.");
            return;
        }

        if (encoding == null || encoding.isEmpty()) {
            getLog().warn("File encoding has not been set, using platform encoding " + ReaderFactory.FILE_ENCODING
                    + ", i.e. build is platform dependent!");
        }

        if (resolveScopes == null) {
            resolveScopes = new String[] {
                (this.includeScope == null || this.includeScope.isEmpty()) ? JavaScopes.TEST : this.includeScope
            };
        }

        if (supplementalModels == null) {
            File sups = new File(appendedResourcesDirectory, "supplemental-models.xml");
            if (sups.exists()) {
                try {
                    supplementalModels = new String[] {sups.toURI().toURL().toString()};
                } catch (MalformedURLException e) {
                    // ignore
                    getLog().debug("URL issue with supplemental-models.xml: " + e);
                }
            }
        }

        configureLocator();

        if (includeProjectProperties) {
            final Properties projectProperties = project.getProperties();
            for (Object key : projectProperties.keySet()) {
                properties.put(key.toString(), projectProperties.get(key).toString());
            }
        }

        ClassLoader origLoader = Thread.currentThread().getContextClassLoader();
        try {
            validate();

            List<File> resourceBundleArtifacts = downloadBundles(resourceBundles);
            supplementModels = loadSupplements(supplementalModels);

            ClassLoader classLoader = initalizeClassloader(resourceBundleArtifacts);

            Thread.currentThread().setContextClassLoader(classLoader);

            velocity = new VelocityEngine();
            velocity.setProperty("resource.loaders", "classpath");
            velocity.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
            velocity.init();

            VelocityContext context = buildVelocityContext(properties);

            processResourceBundles(classLoader, context);

            if (outputDirectory.exists()) {
                // ----------------------------------------------------------------------------
                // Push our newly generated resources directory into the MavenProject so that
                // these resources can be picked up by the process-resources phase.
                // ----------------------------------------------------------------------------
                Resource resource = new Resource();
                resource.setDirectory(outputDirectory.getAbsolutePath());
                // MRRESOURCES-61 handle main and test resources separately
                if (attachToMain) {
                    project.getResources().add(resource);
                }
                if (attachToTest) {
                    project.getTestResources().add(resource);
                }

                // ----------------------------------------------------------------------------
                // Write out archiver dot file
                // ----------------------------------------------------------------------------
                try {
                    File dotFile = new File(project.getBuild().getDirectory(), ".plxarc");
                    FileUtils.mkdir(dotFile.getParentFile().getAbsolutePath());
                    FileUtils.fileWrite(dotFile.getAbsolutePath(), outputDirectory.getName());
                } catch (IOException e) {
                    throw new MojoExecutionException("Error creating dot file for archiving instructions.", e);
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(origLoader);
        }
    }

    private void configureLocator() throws MojoExecutionException {
        if (supplementalModelArtifacts != null && !supplementalModelArtifacts.isEmpty()) {
            List<File> artifacts = downloadBundles(supplementalModelArtifacts);

            for (File artifact : artifacts) {
                if (artifact.isDirectory()) {
                    locator.addSearchPath(FileResourceLoader.ID, artifact.getAbsolutePath());
                } else {
                    try {
                        locator.addSearchPath(
                                "jar", "jar:" + artifact.toURI().toURL().toExternalForm());
                    } catch (MalformedURLException e) {
                        throw new MojoExecutionException("Could not use jar " + artifact.getAbsolutePath(), e);
                    }
                }
            }
        }

        locator.addSearchPath(
                FileResourceLoader.ID, project.getFile().getParentFile().getAbsolutePath());
        if (appendedResourcesDirectory != null) {
            locator.addSearchPath(FileResourceLoader.ID, appendedResourcesDirectory.getAbsolutePath());
        }
        locator.addSearchPath("url", "");
        locator.setOutputDirectory(new File(project.getBuild().getDirectory()));
    }

    protected List<MavenProject> getProjects() {
        List<MavenProject> projects = new ArrayList<>();

        // add filters in well known order, least specific to most specific
        FilterArtifacts filter = new FilterArtifacts();

        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.addAll(getAllDependencies());
        if (this.excludeTransitive) {
            filter.addFilter(new ProjectTransitivityFilter(getDirectDependencies(), true));
        }

        filter.addFilter(new ScopeFilter(this.includeScope, this.excludeScope));
        filter.addFilter(new GroupIdFilter(this.includeGroupIds, this.excludeGroupIds));
        filter.addFilter(new ArtifactIdFilter(this.includeArtifactIds, this.excludeArtifactIds));

        // perform filtering
        try {
            artifacts = filter.filter(artifacts);
        } catch (ArtifactFilterException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

        getLog().debug("PROJECTS: " + artifacts);

        for (Artifact artifact : artifacts) {
            if (artifact.isSnapshot()) {
                artifact.setVersion(artifact.getBaseVersion());
            }

            getLog().debug("Building project for " + artifact);
            MavenProject p;
            try {
                ProjectBuildingRequest req = new DefaultProjectBuildingRequest()
                        .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                        .setProcessPlugins(false)
                        .setRepositorySession(mavenSession.getRepositorySession())
                        .setSystemProperties(mavenSession.getSystemProperties())
                        .setUserProperties(mavenSession.getUserProperties())
                        .setLocalRepository(mavenSession.getLocalRepository())
                        .setRemoteRepositories(project.getRemoteArtifactRepositories());
                ProjectBuildingResult res = projectBuilder.build(artifact, req);
                p = res.getProject();
            } catch (ProjectBuildingException e) {
                getLog().warn("Invalid project model for artifact [" + artifact.getGroupId() + ":"
                        + artifact.getArtifactId() + ":" + artifact.getVersion() + "]. "
                        + "It will be ignored by the remote resources Mojo.");
                continue;
            }

            String supplementKey = generateSupplementMapKey(
                    p.getModel().getGroupId(), p.getModel().getArtifactId());

            if (supplementModels.containsKey(supplementKey)) {
                Model mergedModel = mergeModels(p.getModel(), supplementModels.get(supplementKey));
                MavenProject mergedProject = new MavenProject(mergedModel);
                projects.add(mergedProject);
                mergedProject.setArtifact(artifact);
                mergedProject.setVersion(artifact.getVersion());
                getLog().debug("Adding project with groupId [" + mergedProject.getGroupId() + "] (supplemented)");
            } else {
                projects.add(p);
                getLog().debug("Adding project with groupId [" + p.getGroupId() + "]");
            }
        }
        projects.sort(new ProjectComparator());
        return projects;
    }

    /**
     * Returns all the transitive hull of all the involved maven projects.
     */
    protected abstract Set<Artifact> getAllDependencies();

    /**
     * Returns all the direct dependencies of all the involved maven projects.
     */
    protected abstract Set<Artifact> getDirectDependencies();

    protected Map<Organization, List<MavenProject>> getProjectsSortedByOrganization(List<MavenProject> projects) {
        Map<Organization, List<MavenProject>> organizations = new TreeMap<>(new OrganizationComparator());
        List<MavenProject> unknownOrganization = new ArrayList<>();

        for (MavenProject p : projects) {
            if (p.getOrganization() != null
                    && StringUtils.isNotEmpty(p.getOrganization().getName())) {
                List<MavenProject> sortedProjects = organizations.get(p.getOrganization());
                if (sortedProjects == null) {
                    sortedProjects = new ArrayList<>();
                }
                sortedProjects.add(p);

                organizations.put(p.getOrganization(), sortedProjects);
            } else {
                unknownOrganization.add(p);
            }
        }
        if (!unknownOrganization.isEmpty()) {
            Organization unknownOrg = new Organization();
            unknownOrg.setName("an unknown organization");
            organizations.put(unknownOrg, unknownOrganization);
        }

        return organizations;
    }

    protected boolean copyResourceIfExists(File file, String relFileName, VelocityContext context)
            throws IOException, MojoExecutionException {
        for (Resource resource : project.getResources()) {
            File resourceDirectory = new File(resource.getDirectory());

            if (!resourceDirectory.exists()) {
                continue;
            }

            // TODO - really should use the resource includes/excludes and name mapping
            File source = new File(resourceDirectory, relFileName);
            File templateSource = new File(resourceDirectory, relFileName + TEMPLATE_SUFFIX);

            if (!source.exists() && templateSource.exists()) {
                source = templateSource;
            }

            if (source.exists() && !source.equals(file)) {
                if (source == templateSource) {
                    try (DeferredFileOutputStream os = DeferredFileOutputStream.builder()
                            .setThreshold(velocityFilterInMemoryThreshold)
                            .setOutputFile(file)
                            .get()) {
                        try (Reader reader = getReader(source);
                                Writer writer = getWriter(os)) {
                            velocity.evaluate(context, writer, "", reader);
                        } catch (ParseErrorException | MethodInvocationException | ResourceNotFoundException e) {
                            throw new MojoExecutionException("Error rendering velocity resource: " + source, e);
                        }
                        fileWriteIfDiffers(os);
                    }
                } else if (resource.isFiltering()) {

                    MavenFileFilterRequest req = setupRequest(resource, source, file);

                    try {
                        fileFilter.copyFile(req);
                    } catch (MavenFilteringException e) {
                        throw new MojoExecutionException("Error filtering resource: " + source, e);
                    }
                } else {
                    FileUtils.copyFile(source, file);
                }

                // exclude the original (so eclipse doesn't complain about duplicate resources)
                resource.addExclude(relFileName);

                return true;
            }
        }
        return false;
    }

    private Reader getReader(File source) throws IOException {
        if (encoding != null) {
            return new InputStreamReader(Files.newInputStream(source.toPath()), encoding);
        } else {
            return ReaderFactory.newPlatformReader(source);
        }
    }

    private Writer getWriter(OutputStream os) throws IOException {
        if (encoding != null) {
            return new OutputStreamWriter(os, encoding);
        } else {
            return WriterFactory.newPlatformWriter(os);
        }
    }

    /**
     * If the transformation result fits in memory and the destination file already exists
     * then both are compared.
     * <p>If destination file is byte-by-byte equal, then it is not overwritten.
     * This improves subsequent compilation times since upstream plugins property see that
     * the resource was not modified.
     * <p>Note: the method should be called after {@link DeferredFileOutputStream#close}
     *
     * @param outStream Deferred stream
     * @throws IOException On IO error.
     */
    private void fileWriteIfDiffers(DeferredFileOutputStream outStream) throws IOException {
        File file = outStream.getFile();
        if (outStream.isThresholdExceeded()) {
            getLog().info("File " + file + " was overwritten due to content limit threshold " + outStream.getThreshold()
                    + " reached");
            return;
        }
        boolean needOverwrite = true;

        if (file.exists()) {
            try (InputStream is = Files.newInputStream(file.toPath());
                    InputStream newContents = new ByteArrayInputStream(outStream.getData())) {
                needOverwrite = !IOUtil.contentEquals(is, newContents);
                if (getLog().isDebugEnabled()) {
                    getLog().debug("File " + file + " contents " + (needOverwrite ? "differs" : "does not differ"));
                }
            }
        }

        if (!needOverwrite) {
            getLog().debug("File " + file + " is up to date");
            return;
        }
        getLog().debug("Writing " + file);

        try (OutputStream os = Files.newOutputStream(file.toPath())) {
            outStream.writeTo(os);
        }
    }

    private MavenFileFilterRequest setupRequest(Resource resource, File source, File file) {
        MavenFileFilterRequest req = new MavenFileFilterRequest();
        req.setFrom(source);
        req.setTo(file);
        req.setFiltering(resource.isFiltering());

        req.setMavenProject(project);
        req.setMavenSession(mavenSession);
        req.setInjectProjectBuildFilters(true);

        if (encoding != null) {
            req.setEncoding(encoding);
        }

        if (filterDelimiters != null && !filterDelimiters.isEmpty()) {
            LinkedHashSet<String> delims = new LinkedHashSet<>();
            if (useDefaultFilterDelimiters) {
                delims.addAll(req.getDelimiters());
            }

            for (String delim : filterDelimiters) {
                if (delim == null) {
                    delims.add("${*}");
                } else {
                    delims.add(delim);
                }
            }

            req.setDelimiters(delims);
        }

        return req;
    }

    protected void validate() throws MojoExecutionException {
        int bundleCount = 1;

        for (String artifactDescriptor : resourceBundles) {
            // groupId:artifactId:version, groupId:artifactId:version:type
            // or groupId:artifactId:version:type:classifier
            String[] s = StringUtils.split(artifactDescriptor, ":");

            if (s.length < 3 || s.length > 5) {
                String position;

                if (bundleCount == 1) {
                    position = "1st";
                } else if (bundleCount == 2) {
                    position = "2nd";
                } else if (bundleCount == 3) {
                    position = "3rd";
                } else {
                    position = bundleCount + "th";
                }

                throw new MojoExecutionException("The " + position
                        + " resource bundle configured must specify a groupId, artifactId, "
                        + " version and, optionally, type and classifier for a remote resource bundle. "
                        + "Must be of the form <resourceBundle>groupId:artifactId:version</resourceBundle>, "
                        + "<resourceBundle>groupId:artifactId:version:type</resourceBundle> or "
                        + "<resourceBundle>groupId:artifactId:version:type:classifier</resourceBundle>");
            }

            bundleCount++;
        }
    }

    private static final String KEY_PROJECTS = "projects";
    private static final String KEY_PROJECTS_ORGS = "projectsSortedByOrganization";

    protected VelocityContext buildVelocityContext(Map<String, Object> properties) {
        // the following properties are expensive to calculate, so we provide them lazily
        VelocityContext context = new VelocityContext(properties) {
            @Override
            public Object internalGet(String key) {
                Object result = super.internalGet(key);
                if (result == null && key != null && key.startsWith(KEY_PROJECTS) && containsKey(key)) {
                    // calculate and put projects* properties
                    List<MavenProject> projects = getProjects();
                    put(KEY_PROJECTS, projects);
                    put(KEY_PROJECTS_ORGS, getProjectsSortedByOrganization(projects));
                    return super.internalGet(key);
                }
                return result;
            }
        };
        // to have a consistent getKeys()/containsKey() behaviour, keys must be present from the start
        context.put(KEY_PROJECTS, null);
        context.put(KEY_PROJECTS_ORGS, null);
        // the following properties are cheap to calculate, so we provide them eagerly

        String inceptionYear = project.getInceptionYear();

        // Reproducible Builds: try to use reproducible output timestamp
        String year = MavenArchiver.parseBuildOutputTimestamp(outputTimestamp)
                .orElseGet(Instant::now)
                .atZone(ZoneId.of("UTC+10"))
                .format(DateTimeFormatter.ofPattern("yyyy"));

        if (inceptionYear == null || inceptionYear.isEmpty()) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("inceptionYear not specified, defaulting to " + year);
            }

            inceptionYear = year;
        }
        context.put("project", project);
        context.put("presentYear", year);
        context.put("locator", locator);

        if (inceptionYear.equals(year)) {
            context.put("projectTimespan", year);
        } else {
            context.put("projectTimespan", inceptionYear + "-" + year);
        }
        return context;
    }

    private List<File> downloadBundles(List<String> bundles) throws MojoExecutionException {
        List<File> bundleArtifacts = new ArrayList<>();

        for (String artifactDescriptor : bundles) {
            getLog().info("Preparing remote bundle " + artifactDescriptor);
            // groupId:artifactId:version[:type[:classifier]]
            String[] s = artifactDescriptor.split(":");

            File artifactFile = null;
            // check if the artifact is part of the reactor
            if (mavenSession != null) {
                List<MavenProject> list = mavenSession.getProjects();
                for (MavenProject p : list) {
                    if (s[0].equals(p.getGroupId()) && s[1].equals(p.getArtifactId()) && s[2].equals(p.getVersion())) {
                        if (s.length >= 4 && "test-jar".equals(s[3])) {
                            artifactFile = new File(p.getBuild().getTestOutputDirectory());
                        } else {
                            artifactFile = new File(p.getBuild().getOutputDirectory());
                        }
                    }
                }
            }
            if (artifactFile == null || !artifactFile.exists()) {
                String g = s[0];
                String a = s[1];
                String v = s[2];
                String type = s.length >= 4 ? s[3] : "jar";
                ArtifactType artifactType =
                        RepositoryUtils.newArtifactType(type, artifactHandlerManager.getArtifactHandler(type));
                String classifier = s.length == 5 ? s[4] : artifactType.getClassifier();

                DefaultArtifact artifact =
                        new DefaultArtifact(g, a, classifier, artifactType.getExtension(), v, artifactType);

                try {
                    ArtifactRequest request =
                            new ArtifactRequest(artifact, project.getRemoteProjectRepositories(), "remote-resources");
                    ArtifactResult result = repoSystem.resolveArtifact(mavenSession.getRepositorySession(), request);
                    artifactFile = result.getArtifact().getFile();
                } catch (ArtifactResolutionException e) {
                    throw new MojoExecutionException("Error processing remote resources", e);
                }
            }
            bundleArtifacts.add(artifactFile);
        }

        return bundleArtifacts;
    }

    private ClassLoader initalizeClassloader(List<File> artifacts) throws MojoExecutionException {
        RemoteResourcesClassLoader cl = new RemoteResourcesClassLoader(null);
        try {
            for (File artifact : artifacts) {
                cl.addURL(artifact.toURI().toURL());
            }
            return cl;
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Unable to configure resources classloader: " + e.getMessage(), e);
        }
    }

    protected void processResourceBundles(ClassLoader classLoader, VelocityContext context)
            throws MojoExecutionException {
        List<Map.Entry<String, RemoteResourcesBundle>> remoteResources = new ArrayList<>();
        int bundleCount = 0;
        int resourceCount = 0;

        // list remote resources form bundles
        try {
            RemoteResourcesBundleXpp3Reader bundleReader = new RemoteResourcesBundleXpp3Reader();

            for (Enumeration<URL> e = classLoader.getResources(BundleRemoteResourcesMojo.RESOURCES_MANIFEST);
                    e.hasMoreElements(); ) {
                URL url = e.nextElement();
                bundleCount++;
                getLog().debug("processResourceBundle on bundle#" + bundleCount + " " + url);

                RemoteResourcesBundle bundle;

                try (InputStream in = url.openStream()) {
                    bundle = bundleReader.read(in);
                }

                int n = 0;
                for (String bundleResource : bundle.getRemoteResources()) {
                    n++;
                    resourceCount++;
                    getLog().debug("bundle#" + bundleCount + " resource#" + n + " " + bundleResource);
                    remoteResources.add(new AbstractMap.SimpleEntry<>(bundleResource, bundle));
                }
            }
        } catch (IOException ioe) {
            throw new MojoExecutionException("Error finding remote resources manifests", ioe);
        } catch (XmlPullParserException xppe) {
            throw new MojoExecutionException("Error parsing remote resource bundle descriptor.", xppe);
        }

        getLog().info("Copying " + resourceCount + " resource" + ((resourceCount > 1) ? "s" : "") + " from "
                + bundleCount + " bundle" + ((bundleCount > 1) ? "s" : "") + ".");

        String velocityResource = null;
        try {

            for (Map.Entry<String, RemoteResourcesBundle> entry : remoteResources) {
                String bundleResource = entry.getKey();
                RemoteResourcesBundle bundle = entry.getValue();

                String projectResource = bundleResource;

                boolean doVelocity = false;
                if (projectResource.endsWith(TEMPLATE_SUFFIX)) {
                    projectResource = projectResource.substring(0, projectResource.length() - 3);
                    velocityResource = bundleResource;
                    doVelocity = true;
                }

                // Don't overwrite resource that are already being provided.

                File f = new File(outputDirectory, projectResource);

                FileUtils.mkdir(f.getParentFile().getAbsolutePath());

                if (!copyResourceIfExists(f, projectResource, context)) {
                    if (doVelocity) {
                        try (DeferredFileOutputStream os = DeferredFileOutputStream.builder()
                                .setThreshold(velocityFilterInMemoryThreshold)
                                .setOutputFile(f)
                                .get()) {
                            try (Writer writer = bundle.getSourceEncoding() == null
                                    ? new OutputStreamWriter(os)
                                    : new OutputStreamWriter(os, bundle.getSourceEncoding())) {
                                if (bundle.getSourceEncoding() == null) {
                                    // TODO: Is this correct? Shouldn't we behave like the rest of maven and fail
                                    // down to JVM default instead ISO-8859-1 ?
                                    velocity.mergeTemplate(bundleResource, "ISO-8859-1", context, writer);
                                } else {
                                    velocity.mergeTemplate(bundleResource, bundle.getSourceEncoding(), context, writer);
                                }
                            }
                            fileWriteIfDiffers(os);
                        }
                    } else {
                        URL resUrl = classLoader.getResource(bundleResource);
                        if (resUrl != null) {
                            FileUtils.copyURLToFile(resUrl, f);
                        }
                    }

                    File appendedResourceFile = new File(appendedResourcesDirectory, projectResource);
                    File appendedVmResourceFile = new File(appendedResourcesDirectory, projectResource + ".vm");

                    if (appendedResourceFile.exists()) {
                        getLog().info("Copying appended resource: " + projectResource);
                        try (InputStream in = Files.newInputStream(appendedResourceFile.toPath());
                                OutputStream out = new FileOutputStream(f, true)) {
                            IOUtil.copy(in, out);
                        }

                    } else if (appendedVmResourceFile.exists()) {
                        getLog().info("Filtering appended resource: " + projectResource + ".vm");

                        try (Reader reader = new FileReader(appendedVmResourceFile);
                                Writer writer = getWriter(bundle, f)) {
                            Velocity.init();
                            Velocity.evaluate(context, writer, "remote-resources", reader);
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            throw new MojoExecutionException("Error reading remote resource", ioe);
        } catch (VelocityException e) {
            throw new MojoExecutionException("Error rendering Velocity resource '" + velocityResource + "'", e);
        }
    }

    private Writer getWriter(RemoteResourcesBundle bundle, File f) throws IOException {
        Writer writer;
        if (bundle.getSourceEncoding() == null) {
            writer = new PrintWriter(new FileWriter(f, true));
        } else {
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, true), bundle.getSourceEncoding()));
        }
        return writer;
    }

    protected Model getSupplement(Xpp3Dom supplementModelXml) throws MojoExecutionException {
        MavenXpp3Reader modelReader = new MavenXpp3Reader();
        Model model = null;

        try {
            model = modelReader.read(new StringReader(supplementModelXml.toString()));
            String groupId = model.getGroupId();
            String artifactId = model.getArtifactId();

            if (groupId == null || groupId.trim().equals("")) {
                throw new MojoExecutionException(
                        "Supplemental project XML " + "requires that a <groupId> element be present.");
            }

            if (artifactId == null || artifactId.trim().equals("")) {
                throw new MojoExecutionException(
                        "Supplemental project XML " + "requires that a <artifactId> element be present.");
            }
        } catch (IOException e) {
            getLog().warn("Unable to read supplemental XML: " + e.getMessage(), e);
        } catch (XmlPullParserException e) {
            getLog().warn("Unable to parse supplemental XML: " + e.getMessage(), e);
        }

        return model;
    }

    protected Model mergeModels(Model parent, Model child) {
        inheritanceAssembler.assembleModelInheritance(child, parent);
        return child;
    }

    private static String generateSupplementMapKey(String groupId, String artifactId) {
        return groupId.trim() + ":" + artifactId.trim();
    }

    private Map<String, Model> loadSupplements(String[] models) throws MojoExecutionException {
        if (models == null) {
            getLog().debug("Supplemental data models won't be loaded. No models specified.");
            return Collections.emptyMap();
        }

        List<Supplement> supplements = new ArrayList<>();
        for (String set : models) {
            getLog().debug("Preparing ruleset: " + set);
            try {
                File f = locator.getResourceAsFile(set, getLocationTemp(set));

                if (null == f || !f.exists()) {
                    throw new MojoExecutionException("Cold not resolve " + set);
                }
                if (!f.canRead()) {
                    throw new MojoExecutionException("Supplemental data models won't be loaded. " + "File "
                            + f.getAbsolutePath() + " cannot be read, check permissions on the file.");
                }

                getLog().debug("Loading supplemental models from " + f.getAbsolutePath());

                SupplementalDataModelXpp3Reader reader = new SupplementalDataModelXpp3Reader();
                SupplementalDataModel supplementalModel = reader.read(new FileReader(f));
                supplements.addAll(supplementalModel.getSupplement());
            } catch (Exception e) {
                String msg = "Error loading supplemental data models: " + e.getMessage();
                getLog().error(msg, e);
                throw new MojoExecutionException(msg, e);
            }
        }

        getLog().debug("Loading supplements complete.");

        Map<String, Model> supplementMap = new HashMap<>();
        for (Supplement sd : supplements) {
            Xpp3Dom dom = (Xpp3Dom) sd.getProject();

            Model m = getSupplement(dom);
            supplementMap.put(generateSupplementMapKey(m.getGroupId(), m.getArtifactId()), m);
        }

        return supplementMap;
    }

    /**
     * Convenience method to get the location of the specified file name.
     *
     * @param name the name of the file whose location is to be resolved
     * @return a String that contains the absolute file name of the file
     */
    private String getLocationTemp(String name) {
        String loc = name;
        if (loc.indexOf('/') != -1) {
            loc = loc.substring(loc.lastIndexOf('/') + 1);
        }
        if (loc.indexOf('\\') != -1) {
            loc = loc.substring(loc.lastIndexOf('\\') + 1);
        }
        getLog().debug("Before: " + name + " After: " + loc);
        return loc;
    }

    static class OrganizationComparator implements Comparator<Organization> {
        @Override
        public int compare(Organization org1, Organization org2) {
            int i = compareStrings(org1.getName(), org2.getName());
            if (i == 0) {
                i = compareStrings(org1.getUrl(), org2.getUrl());
            }
            return i;
        }

        private int compareStrings(String s1, String s2) {
            if (s1 == null && s2 == null) {
                return 0;
            } else if (s1 == null) {
                return 1;
            } else if (s2 == null) {
                return -1;
            }

            return s1.compareToIgnoreCase(s2);
        }
    }

    static class ProjectComparator implements Comparator<MavenProject> {
        @Override
        public int compare(MavenProject p1, MavenProject p2) {
            return p1.getArtifact().compareTo(p2.getArtifact());
        }
    }
}
