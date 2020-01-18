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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;


/**
 * Stub
 */
public class ArtifactStub
    implements Artifact
{

    boolean hasClassifier;

    boolean resolved;

    boolean optional;

    boolean release;

    ArtifactHandler artifactHandler;

    File file;

    String baseVersion;

    String type;

    String classifier;

    String identifier;

    String dependencyConflictId;

    String downloadUrl;

    String selectedVersion;

    String artifactId;

    String groupId;

    String resolvedVersion;

    String scope;

    String version;

    VersionRange versionRange;


    public ArtifactStub()
    {
        type = "testtype";
        scope = "testscope";
        classifier = "testclassifier";
        artifactHandler = new DefaultArtifactHandler();
    }

    public void populate( MavenProjectBasicStub project )
    {
        groupId = project.getGroupId();
        artifactId = project.getArtifactId();
        version = project.getVersion();
        versionRange = VersionRange.createFromVersion( version );
    }

    @Override
    public boolean hasClassifier()
    {
        return true;
    }

    @Override
    public String getBaseVersion()
    {
        return "Test Version";
    }

    @Override
    public void setBaseVersion( String version )
    {
        baseVersion = version;
    }

    @Override
    public void setFile( File _file )
    {
        file = _file;
    }

    @Override
    public File getFile()
    {
        return new File( "testfile" );
    }

    @Override
    public String getGroupId()
    {
        return groupId;
    }

    @Override
    public String getArtifactId()
    {
        return artifactId;
    }

    @Override
    public String getVersion()
    {
        return version;
    }

    @Override
    public void setVersion( String _version )
    {
        version = _version;
    }

    @Override
    public String getScope()
    {
        return scope;
    }

    @Override
    public String getType()
    {
        return type;
    }

    @Override
    public String getClassifier()
    {
        return classifier;
    }

    @Override
    public String getId()
    {
        return identifier;
    }

    @Override
    public String getDependencyConflictId()
    {
        return dependencyConflictId;
    }

    @Override
    public void addMetadata( ArtifactMetadata metadata )
    {

    }

    @Override
    public Collection<ArtifactMetadata> getMetadataList()
    {
        return new LinkedList<>();
    }

    @Override
    public void setRepository( ArtifactRepository remoteRepository )
    {

    }

    @Override
    public ArtifactRepository getRepository()
    {
        return null;
    }

    @Override
    public void updateVersion( String version, ArtifactRepository localRepository )
    {

    }

    @Override
    public String getDownloadUrl()
    {
        return downloadUrl;
    }

    @Override
    public void setDownloadUrl( String _downloadUrl )
    {
        downloadUrl = _downloadUrl;
    }

    @Override
    public ArtifactFilter getDependencyFilter()
    {
        return null;
    }

    @Override
    public void setDependencyFilter( ArtifactFilter artifactFilter )
    {

    }

    @Override
    public ArtifactHandler getArtifactHandler()
    {
        return artifactHandler;
    }

    @Override
    public List<String> getDependencyTrail()
    {
        return new LinkedList<>();
    }

    @Override
    public void setDependencyTrail(List<String> dependencyTrail)
    {

    }

    @Override
    public void setScope( String _scope )
    {
        scope = _scope;
    }

    @Override
    public VersionRange getVersionRange()
    {
        return versionRange;
    }

    @Override
    public void setVersionRange( VersionRange newRange )
    {

    }

    @Override
    public void selectVersion( String version )
    {
        selectedVersion = version;
    }

    @Override
    public void setGroupId( String _groupId )
    {
        groupId = _groupId;
    }

    @Override
    public void setArtifactId( String _artifactId )
    {
        artifactId = _artifactId;
    }

    @Override
    public boolean isSnapshot()
    {
        return true;
    }

    @Override
    public void setResolved( boolean _resolved )
    {
        resolved = _resolved;
    }

    @Override
    public boolean isResolved()
    {
        return true;
    }

    @Override
    public void setResolvedVersion( String version )
    {
        resolvedVersion = version;
    }


    @Override
    public void setArtifactHandler( ArtifactHandler handler )
    {

    }

    @Override
    public boolean isRelease()
    {
        return true;
    }

    @Override
    public void setRelease( boolean _release )
    {
        release = _release;
    }

    @Override
    public List<ArtifactVersion> getAvailableVersions()
    {
        return new LinkedList<>();
    }

    @Override
    public void setAvailableVersions(List<ArtifactVersion> versions)
    {

    }

    @Override
    public boolean isOptional()
    {
        return true;
    }

    @Override
    public void setOptional( boolean _optional )
    {
        optional = _optional;
    }

    @Override
    public ArtifactVersion getSelectedVersion()
        throws OverConstrainedVersionException
    {
        return null;
    }

    @Override
    public boolean isSelectedVersionKnown()
        throws OverConstrainedVersionException
    {
        return true;
    }

    @Override
    public int compareTo( Artifact o )
    {
        return 0;
    }

    @Override
    public ArtifactMetadata getMetadata( Class<?> metadataClass )
    {
        return null;
    }

}
