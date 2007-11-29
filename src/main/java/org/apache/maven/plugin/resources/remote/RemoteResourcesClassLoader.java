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

import java.io.IOException;
import java.net.URLClassLoader;
import java.net.URL;
import java.util.Enumeration;

/**
 * @author Jason van Zyl
 */
public class RemoteResourcesClassLoader
    extends URLClassLoader
{
    public RemoteResourcesClassLoader( ClassLoader parent )
    {
        super( new URL[]{} , parent );
    }

    public void addURL( URL url )
    {
        super.addURL( url );
    }
    
    // don't check the parents for the resources.   Just check the jars
    // that we have provided.   Thus, we don't pull junk from the
    // system classpath jars and stuff instead of the jars
    // we specifically provided
    public URL getResource(String name) {
        return findResource(name);
    }
    public Enumeration getResources(String name) throws IOException {
        return findResources(name);
    }

}
