# jenkins-chef-shared-library

> ## ⚠️ DEPRECATED — No Longer Maintained
>
> This repository is **deprecated** and is no longer maintained. The last
> meaningful update was in October 2020.
>
> **Do not use this library for new pipelines.** It is preserved here for
> historical reference only.

## Why this is deprecated

This Jenkins Shared Library was written against the Chef and Jenkins ecosystem
as it existed in 2020. The surrounding tooling has changed substantially since
then, and the library has not kept pace. Specifically:

- **Chef Software ownership and licensing changes.** Chef was acquired by
  Progress and the licensing/distribution model for Chef Infra changed in
  2020–2021. None of this is reflected here.
- **Hardcoded Chef Workstation paths** (e.g. `/opt/chef-workstation/bin/...`)
  that assume a specific host install layout. Modern pipelines run Chef in
  containers with pinned versions.
- **Dependency on the legacy `ChefIdentityBuildWrapper` Jenkins plugin**, which
  has uncertain compatibility with current Jenkins LTS releases.
- **Knife-based data bag workflows** (`dataBagOps`) which are considered legacy
  in favor of Policyfile attributes and Chef Infra patterns.
- **Hardcoded `PR-*` branch conventions** baked into pipeline `when {}` blocks,
  which do not match modern Git/PR workflows.
- **No version pinning** for Chef, no container-based execution, no support for
  modern auth (OIDC, short-lived credentials).

## What was here

A Jenkins Shared Library providing pipeline steps for Chef CI/CD:

- `cookbookOps` — cookbook linting and testing
- `policyOpsGitHub` / `policyOpsADO` — Chef Policyfile lifecycle management
  with uploads to S3, GCS, and Azure
- `dataBagOps` — Chef data bag versioning via `knife`
- `cookbookOpsCompareVersions` — cookbook version validation

## Recommended alternatives

If you are building Chef CI/CD today, consider:

- Running Chef tooling (`chef`, `cookstyle`, `kitchen`, etc.) inside pinned
  container images rather than against host-installed Chef Workstation.
- Using standard Jenkins **Credentials Binding** instead of
  `ChefIdentityBuildWrapper`.
- Migrating data bag workflows to Policyfile attributes where possible.
- Evaluating GitHub Actions, GitLab CI, or other modern CI systems if you are
  not otherwise committed to Jenkins.

## Status

This repository is archived in spirit. Issues and pull requests will not be
reviewed.
