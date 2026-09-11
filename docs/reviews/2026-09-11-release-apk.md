# Local Release APK handoff

The user explicitly requested a formal Release APK after the signed debug
artifact was prepared. The feature implementation remains the one validated in
`50a1009b9a03451358c3e108b799e3ca4a06ec63`; this follow-up changes build metadata
and documentation only.

- Version: `r14.21.7`, versionCode 215. The code increases from debug build 214
  so it can be upgraded to the requested Release artifact.
- Build: JDK 25, `clean :app:assembleRelease`, `officialRelease=true`,
  `requireBuildRevision=true`, and the exact committed source revision.
- Release configuration: debugging disabled, code minification and resource
  shrinking enabled, signing configuration supplied from outside the repository.
- Verify package ID, SDK/ABI, version, provenance, APK alignment, signing
  certificate and SHA-256 before handoff. Compare the signing certificate with
  the previously captured installed formal APK as well as the supplied key.
- Preserve the existing local test evidence. Device visual acceptance remains
  with the user, as requested.

This request authorizes generating the local Release APK from the completed
working branch. It does not include merging main, creating tags, pushing, or
publishing GitHub/LSPosed releases. The original unversioned source directory is
preserved. APKs, signing material, mapping files and ROM samples stay outside Git.
