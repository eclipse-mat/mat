# Memory Analyzer API Policy

This document provides the current API Policy for Memory Analyzer.

## Purpose

Eclipse MAT is built on top of the Eclipse IDE framework, and the components can
be embedded inside other deployments. As an OSGI framework-capable runtime,
the components export known consistent APIs that allow other developers and
components to work together within the IDE.

For example, the [Eclipse MAT Calcite Plugin](https://github.com/vlsi/mat-calcite-plugin)
plugs directly into the Eclipse IDE and interacts with the Eclipse MAT
components to enhance its functionality.

## Declared API

The declared APIs in Memory Analyzer are provided as public and documented. The
API compatibility between different versions of Memory Analyzer should be
reflected by the version numbers, following the [Eclipse versioning policy](https://github.com/eclipse-platform/eclipse.platform/blob/master/docs/VersionNumbering.md).

## Some examples

- Changes to the API - adding new APIs or deprecating APIs should be documented
and communicated to the community (e.g. via GH issue and/or mailing list).

- Deprecated API should be available for at lease one major release.

## Provisional and internal API

Provisional APIs should be used while development is occurring. If successfully
adopted, they might become declared APIs. If not, they can be removed. In any
situation, the community should be notified.

## Tooling

See the [Contributor Reference](Contributor_Reference.md) for some notes on API
tooling and configuration.
