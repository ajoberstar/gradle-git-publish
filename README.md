# gradle-git-publish

[![CI](https://github.com/ajoberstar/gradle-git-publish/actions/workflows/ci.yaml/badge.svg)](https://github.com/ajoberstar/gradle-git-publish/actions/workflows/ci.yaml)

**NOTE:** As of 3.0.1, gradle-git-publish is published to Maven Central

## Getting Help or Contributing

**IMPORANT:** I consider this plugin feature complete and don't spend a lot of time on maintenance due to other time commitments. While, I will eventually get to issues or PRs raised, **do not** expect a timely response. I'm not trying to be rude or dismissive, I only get back to this project periodically (on the order of _months_, in many cases). Please set your expectations appropriately as you file issues or open PRs.

Please use the repo's [issues](https://github.com/ajoberstar/gradle-git-publish/issues) for all questions, bug reports, and feature requests.

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

```groovy
plugins {
    id 'org.ajoberstar.git-publish' version '<version>'
}
```

### Configuration

**NOTE:** In general, there are no default values here. The main exception is that the `repoUri` and `referenceRepoUri` will be automatically set if you use the `org.ajoberstar.grgit` plugin to your project's origin repo URI.

```groovy
gitPublish {
    // where to publish to (repo must exist)
    repoUri = 'git@github.com:ajoberstar/test-repo.git'
    // (or 'https://github.com/ajoberstar/test-repo.git', depending on authentication)

    // where to fetch from prior to fetching from the remote (i.e. a local repo to save time)
    referenceRepoUri = 'file:///home/human/projects/test-repo/'

    // branch will be created if it doesn't exist
    branch = 'gh-pages'
  
    // if set, a shallow clone will be performed instead of pulling all history
    fetchDepth = null

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

    // message used when committing changes
    commitMessage = 'Publishing a new page' // defaults to 'Generated by gradle-git-publish'
    
    // for signing commits, omit to use the default from your gitconfig
    sign = false
}
```

As of v4.1.0, you can now configure multiple publications (e.g. to target different repositories or branches).

```groovy
gitPublish {
    commitMessage = 'My favorite commit message' // configures the main publication

    publications {
        // main
        main {
            branch = 'great-branch' // alternatively can configure at the top-level of the gitPublish block
            // ... any other config from the gitPublish block ...
        }

        other {
            branch = 'some-branch' // may need branch.set('some-branch')
            // ... any other config from the gitPublish block ...
        }
    }
}
```

### Tasks and Execution

Generally, you'll just run `gitPublishPush`, but there is a series of four tasks that happen in order.

- `gitPublishReset` - Clones/updates the working repo to the latest commit on the `repoUri` `branch` head. All files not included by the `preserve` filters will be deleted and staged.
- `gitPublishCopy` - Copies any files defined in the `contents` CopySpec into the working repo.
- `gitPublishCommit` - Commits all changes to the working repo.
- `gitPublishPush` - If changes were committed, pushed them to the `repoUri`.

Each publication gets its own set of tasks, with a general `gitPublishPushAll` if you want to push all publications to their respective repos/branches.

As is common in Gradle, the `main` publication is not indicated in task names (e.g. for `main` `gitPublishCommit` and for `other` `gitPublishOtherCommit`).

### Avoiding Extra Copy

If you are generating a large site, you may want to directly generate it into the working repo to save an extra copy step. You can do this with task dependencies and referring to the `repoDir`.

```groovy
jbakeTask {
    outputDir gitPublish.repoDir
    dependsOn gitPublishReset
}

gitPublishCommit.dependsOn jbakeTask
```

## Migrating from org.ajoberstar.github-pages

The following table should help translate settings you used in `org.ajoberstar.github-pages` to this plugin's format. Additionally reference the Configuration section above for more information on the current feature set.

| org.ajoberstar.github-pages | org.ajoberstar.git-publish  | Comment                                                                                              |
| --------------------------- | --------------------------- | ---------------------------------------------------------------------------------------------------- |
| `repoUri`                   | `repoUri`                   | Used to allow any Object (which would be lazily unpacked to a String). Now requires a String.        |
| `targetBranch`              | `branch`                    | The old plugin defaulted to `gh-pages`, the new one has no default. This must be a String.           |
| `workingPath`               | `repoDir`                   | Used to allow any Object and called `file()` on it for you. Now expects a File.                      |
| `pages`                     | `contents`                  | Just a name change.                                                                                  |
| `deleteExistingFiles`       | `preserve`                  | If previously `true` (the default), do nothing. If previously `false`, `preserve { include '**/*' }` |
| `commitMessage`             | `commitMessage`             | Just copy from the old value.                                                                        |
| `credentials`               | env variable or system prop | `GRGIT_USER` environment variable or `org.ajoberstar.grgit.auth.username` system property.           |

Use the `gitPublishPush` task as replacement for the `publishGhPages` task.

**NOTE:** If you are using `secrets.GITHUB_TOKEN` in GitHub Actions, it would be suggested declaring both `GRGIT_USER` and `GRGIT_PASS` like:

```yml
env:
  GRGIT_USER: ${{ github.repository_owner }}
  GRGIT_PASS: ${{ secrets.GITHUB_TOKEN }}
```

to prevent potential credential issues like [issue 109](https://github.com/ajoberstar/gradle-git-publish/issues/109).

## Finding versions

### Newest versions are on Maven Central

As of 3.0.1, gradle-git-publish is published to Maven Central.

This project is no longer directly published to the Gradle Plugin Portal, but since the portal proxies Maven Central you can still access it through the portal. The only side effect is that [the portal](https://plugins.gradle.org/plugin/org.ajoberstar.git-publish) will no longer list the latest version. Use this repo or [search.maven.org](https://search.maven.org/search?q=g:org.ajoberstar.git-publish) to find the latest version.

### Old versions from Bintray/JCenter

This project was previously uploaded to JCenter, which was deprecated in 2021.

In the event that JCenter is unavailable and acess to past versions is needed, I've made a Maven repo available in [bintray-backup](https://github.com/ajoberstar/bintray-backup). Add the following to your repositories to use it.

```groovy
maven {
  name = 'ajoberstar-backup'
  url = 'https://ajoberstar.org/bintray-backup/'
}
```

Made possible by [lacasseio/bintray-helper](https://github.com/lacasseio/bintray-helper) in case you have a similar need to pull your old Bintray artifacts.

## Acknowledgements

Thanks to all of the [contributors](https://github.com/ajoberstar/gradle-git-publish/graphs/contributors).

I also want to acknowledge [Peter Ledbrook](https://github.com/pledbrook) for the initial
idea and code for the plugin.
