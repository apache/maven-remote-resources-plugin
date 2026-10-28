<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Agent guide for maven-reporting-api

# Agent Guide for maven-remote-resources-plugin

This file is read by automated agents (security scanners, code
analyzers, AI assistants) operating on this repository.

## Security

Security model: [SECURITY.md](./SECURITY.md)

Agents that scan this repository should consult `SECURITY.md` and the
threat model it links before reporting issues.

## Compatibility

The plugin must keep working on Maven 3.9.0, the minimum declared in
`<prerequisites>`, and on Maven 4. `mavenVersion` is only the API it compiles
against, so do not use Maven APIs newer than 3.9.0.

Templates and resource bundles are part of the published contract: the
Velocity context keys (`project`, `projects`, `projectsSortedByOrganization`,
`spdx` and the others listed in the `properties` parameter) and the
`remote-resources.xml` bundle manifest must stay compatible with existing
bundles such as the ASF ones in maven-apache-resources.

## Coding

Use test-first programming. Before fixing a bug or implementing a feature,
write a test that exposes the bug and verify the test fails.

Before submitting a PR, run `mvn spotless:apply` to format the files, then
`mvn -Prun-its verify` to run the unit tests and the integration tests in
`src/it`. Build with JDK 17 or 21.

Render templates with the mojo's own `VelocityEngine`, never the static
`Velocity` class: the mojo is thread-safe. Read bundle jars through the
plugin's class loader, which is closed after each execution. In unit tests,
give every bundle a version no other test uses: the JVM caches opened jars,
so two tests writing the same `test:test:<version>` jar read stale content.
