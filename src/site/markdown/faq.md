---
title: Frequently Asked Questions
---

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

<a id="top"></a>

# Frequently Asked Questions

1. [Why do I need to use this plugin?](#question1)
2. [The generated files have a lot of missing information. Looking at the POMs from the dependencies, the information isn't there either. What can I do?](#question2)

<a id="question1"></a>

### Why do I need to use this plugin?

This plugin greatly reduces the pain associated with consistent packaging concerns across a large set of
projects, or an entire organization. Any project can specify the use of a remote resource bundle and have the
resources incorporated into their packaging. This means that you can create standard settings in a parent POM
somewhere in the project hierarchy and have all projects use packaged common resources in a standard way like
licenses, other legal notices and disclaimers, or anything else that may be common.

<a id="question2"></a>

### The generated files have a lot of missing information. Looking at the POMs from the dependencies, the information isn't there either. What can I do?

There are two solutions:

1. File bugs with the projects that produced those artifacts to get them to fix them.
2. Use a supplemental data file. You can create a file that contains the missing metadata. For example:

    ```xml
    <supplementalDataModels>
      <supplement>
        <project>
          <groupId>com.sun.xml.bind</groupId>
          <artifactId>jaxb-impl</artifactId>
          <name>Sun JAXB Reference Implementation Runtime</name>
          <organization>
            <name>Sun Microsystems</name>
            <url>http://www.sun.com/</url>
          </organization>
          <licenses>
            <license>
              <name>COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL) Version 1.0</name>
              <url>https://oss.oracle.com/licenses/CDDL</url>
            </license>
          </licenses>
        </project>
      </supplement>
    </supplementalDataModels>
    ```

    That location of that file can then be configured with the `supplementalModels` configuration element for
    the `process` goal. The supplemental information is merged with the information provided from the
    repository.
