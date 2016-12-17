# gradle-git-publish

[![Bintray](https://img.shields.io/bintray/v/ajoberstar/gradle-plugins/org.ajoberstar%3Agradle-git-publish.svg?style=flat-square)](https://bintray.com/ajoberstar/gradle-plugins/org.ajoberstar%3Agradle-git-publish/_latestVersion)
[![Travis](https://img.shields.io/travis/ajoberstar/gradle-git-publish.svg?style=flat-square)](https://travis-ci.org/ajoberstar/gradle-git-publish)
[![Quality Gate](https://sonarqube.ajoberstar.com/api/badges/gate?key=org.ajoberstar:gradle-git-publish)](https://sonarqube.ajoberstar.com/dashboard/index/org.ajoberstar:gradle-git-publish)
[![GitHub license](https://img.shields.io/github/license/ajoberstar/gradle-git-publish.svg?style=flat-square)](https://github.com/ajoberstar/gradle-git-publish/blob/master/LICENSE)

## Why do you care?

Git is immensely popular and being able to publish to it as part of a build process can be very valuable, for example to publish a blog or project documentation to GitHub Pages.

## What is it?

`gradle-git-publish` is a [Gradle](http://gradle.org) plugin, `org.ajoberstar.git-publish`, which publishes files to a
remote Git repository's branch.

See [Grgit](https://github.com/ajoberstar/grgit) for details on the Git library used underneath, including
configuration for authentication.

## Usage

See the [Release Notes](https://github.com/ajoberstar/gradle-git-publish/releases) for updates on
changes and compatibility with Java and Gradle versions.

### Applying the Plugin

**Plugins DSL**

```groovy
plugins {
    id 'org.ajoberstar.git-publish' version '<version>'
}
```

**Classic**

```groovy
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'org.ajoberstar:gradle-git-publish:<version>'
    }
}

apply plugin: 'org.ajoberstar.git-publish'
```

### Configuration

**NOTE:** In general, there are no default values here. The one exception is that the `repoUri` will be automatically set if you use the `org.ajoberstar.grgit` plugin to your project's origin repo URI.

```groovy
gitPublish {
    // where to publish to
    repoUri = 'git@github.com/ajoberstar/test-repo.git'
    branch = 'gh-pages'

    // generally, you don't need to touch this
    repoDir = file("$buildDir/somewhereelse") // defaults to $buildDir/gitPublish

    // what to publish, this is a standard CopySpec
    contents {
        from 'src/pages'
        from(javadoc) {
            into 'api'
        }
    }

    // what to keep in the existing branch (include=keep)
    preserve {
        include '1.0.0/**'
        exclude '1.0.0/temp.txt'
    }
}
```

### Tasks and Execution

Generally, you'll just run `gitPublishPush`, but there is a series of four tasks that happen in order.

* `gitPublishReset` - Clones/updates the working repo to the latest commit on the `repoUri` `branch` head. All files not included by the `preserve` filters will be deleted and staged.
* `gitPublishCopy` - Copies any files defined in the `contents` CopySpec into the working repo.
* `gitPublishCommit` - Commits all changes to the working repo.
* `gitPublishPush` - If changes were committed, pushed them to the `repoUri`.

### Avoiding Extra Copy

If you are generating a large site, you may want to directly generate it into the working repo to save an extra copy step. You can do this with task dependencies and referring to the `repoDir`.

```groovy
jbakeTask {
    outputDir gitPublish.repoDir
    dependsOn gitPublishReset
}

gitPublishCommit.dependsOn jbakeTask
```

## Questions, Bugs, and Features

Please use the repo's [issues](https://github.com/ajoberstar/gradle-git-publish/issues)
for all questions, bug reports, and feature requests.

## Contributing

Contributions are very welcome and are accepted through pull requests.

Smaller changes can come directly as a PR, but larger or more complex
ones should be discussed in an issue first to flesh out the approach.

If you're interested in implementing a feature on the
[issues backlog](https://github.com/ajoberstar/gradle-git-publish/issues), add a comment
to make sure it's not already in progress and for any needed discussion.

## Acknowledgements

Thanks to all of the [contributors](https://github.com/ajoberstar/gradle-git-publish/graphs/contributors).

I also want to acknowledge [Peter Ledbrook](https://github.com/pledbrook) for the initial
idea and code for the plugin.
