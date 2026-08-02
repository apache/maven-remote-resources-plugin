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

# AGENTS.md

This file provides guidance for AI coding agents that work in this repository.

## Build and verification

- Format the code before you commit:
  - `mvn spotless:apply`
- Verify that the pull request is ready:
  - `mvn spotless:check`
  - `mvn checkstyle:check`
  - `mvn apache-rat:check`
  - `mvn verify` (runs the unit tests and the RAT license check)
- Make sure all of the commands above pass before you open or update a pull
  request.

## Test-first programming

Write a failing test before you implement a fix or a feature:

1. Write a test that reproduces the bug, or that specifies the new behavior.
2. Run the test and confirm that it fails. The failure must match the bug that
   you are fixing.
3. Implement the change.
4. Run the test again and confirm that it passes.
5. Run `mvn verify` to confirm that nothing else is broken.

## Documentation style

Use ASD-STE100 Simplified Technical English for documentation. This includes
the README, the site documentation, javadoc, and code comments:

- Write short sentences.
- Write one idea per sentence.
- Use only the approved vocabulary and the approved meanings for words.
- Use the active voice.
- Write precisely and without ambiguity.
