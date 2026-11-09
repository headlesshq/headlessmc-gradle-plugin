# Changelog

## [1.0.0](https://github.com/headlesshq/headlessmc-gradle-plugin/compare/0.3.0...1.0.0) (2026-11-09)


### ⚠ BREAKING CHANGES

* the plugin id is io.github.headlesshq.headlessmc-dev-plugin, the classes moved to io.github.headlesshq.headlessmc.gradle.dev.
* the plugin is rewritten for HeadlessMc 3, see the README.

### Features

* **dev:** generate the headlessmc-plugins index entry for a plugin release ([#10](https://github.com/headlesshq/headlessmc-gradle-plugin/issues/10)) ([204c316](https://github.com/headlesshq/headlessmc-gradle-plugin/commit/204c316f4980788a17c1d8436854ee0e3e3f9f99))
* run HeadlessMc 3 as a separate process and add the hmc-plugin-gradle-plugin ([c79bfd3](https://github.com/headlesshq/headlessmc-gradle-plugin/commit/c79bfd3a54216c47bc82744065c8b3a3c071b6ba))


### Bug Fixes

* never run a native executable without a verified hash ([0702f4a](https://github.com/headlesshq/headlessmc-gradle-plugin/commit/0702f4a142228b6b464b9eb8253b59d26df2587a))
* refuse to clean directories outside of HeadlessMc and fail clearly for servers without a version ([5a72f8e](https://github.com/headlesshq/headlessmc-gradle-plugin/commit/5a72f8e7196e011560788e39d6e898d80f17ef5b))
* retry downloads of the native executable on server errors ([ab41991](https://github.com/headlesshq/headlessmc-gradle-plugin/commit/ab419913ec35b6ecbb4ce609d05647d3cc0339bc))


### Code Refactoring

* rename hmc-plugin-gradle-plugin to headlessmc-dev-plugin ([5942807](https://github.com/headlesshq/headlessmc-gradle-plugin/commit/5942807277319b0f847ccb4e7b8bc40d743cfe7e))
