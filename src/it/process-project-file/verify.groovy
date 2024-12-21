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

dir = new File( basedir, 'target/maven-shared-archive-resources' )

assert new File( dir, 'a/a.txt' ).text.contains( 'mrrp-bundle-a' )

assert new File( dir, 'common.txt' ).text.contains( 'mrrp-bundle-a' ) // bundle a is first in classpath
assert new File( dir, 'common-vm.txt' ).text.contains( 'mrrp-bundle-a org.apache.maven.its:mrrp-process' )

// bundle content overridden by local project's workspace file
assert new File( dir, 'common2.txt' ).text.contains( 'using project\'s workspace file' )
