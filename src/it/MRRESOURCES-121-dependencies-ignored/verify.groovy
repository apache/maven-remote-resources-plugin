
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
def dependencies = new File( basedir, 'target/maven-shared-archive-resources/META-INF/DEPENDENCIES' )
assert dependencies.exists()
content = dependencies.text

assert 2 == content.count( 'License: The Apache Software License, Version 2.0  (http://www.apache.org/licenses/LICENSE-2.0.txt)' )
assert 1 == content.count( 'License: Apache License, Version 2.0  (http://www.apache.org/licenses/LICENSE-2.0.txt)' )
assert 1 == content.count( 'License: Apache License, Version 2.0  (https://www.apache.org/licenses/LICENSE-2.0.txt)' )

assert 1 == content.count( 'From: \'Apache Software Foundation\' (http://www.apache.org/)' )
assert 1 == content.count( 'Maven Plugin API (http://maven.apache.org/maven2/maven-plugin-api/) org.apache.maven:maven-plugin-api:jar:2.0' )

assert 1 == content.count( 'From: \'Codehaus Plexus\' (https://codehaus-plexus.github.io/)' )
assert 1 == content.count( 'Plexus Cipher: encryption/decryption Component (https://codehaus-plexus.github.io/plexus-cipher/) org.codehaus.plexus:plexus-cipher:jar:2.0' )

assert 1 == content.count( 'From: \'The Apache Software Foundation\' (https://www.apache.org/)' )
assert 1 == content.count( 'Maven Artifact Resolver API (https://maven.apache.org/resolver/maven-resolver-api/) org.apache.maven.resolver:maven-resolver-api:jar:1.6.3' )
