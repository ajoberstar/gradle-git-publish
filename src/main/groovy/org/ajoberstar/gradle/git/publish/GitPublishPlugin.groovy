package org.ajoberstar.gradle.git.publish

import org.ajoberstar.gradle.git.publish.tasks.GitPublishCommit
import org.ajoberstar.gradle.git.publish.tasks.GitPublishPush
import org.ajoberstar.gradle.git.publish.tasks.GitPublishReset
import org.gradle.api.provider.Provider

import java.nio.file.Files

import groovy.transform.PackageScope

import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.operation.FetchOp
import org.ajoberstar.grgit.operation.ResetOp
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.file.FileTree
import org.eclipse.jgit.transport.URIish

class GitPublishPlugin implements Plugin<Project> {
  @PackageScope static final String RESET_TASK = 'gitPublishReset'
  @PackageScope static final String COPY_TASK = 'gitPublishCopy'
  @PackageScope static final String COMMIT_TASK = 'gitPublishCommit'
  @PackageScope static final String PUSH_TASK = 'gitPublishPush'

  @Override
  void apply(Project project) {
    GitPublishExtension extension = project.extensions.create('gitPublish', GitPublishExtension, project)

    // if using the grgit plugin, default to the repo's origin
    project.pluginManager.withPlugin('org.ajoberstar.grgit') {
      // TODO should this be based on tracking branch instead of assuming origin?
      extension.repoUri = project.grgit?.remote?.list()?.find { it.name == 'origin' }?.url
    }

    extension.repoDir = project.file("${project.buildDir}/gitPublish")

    Provider<Grgit> grgitProvider = project.provider {
      findExistingRepo(project, extension).orElseGet { freshRepo(extension) }
    }

    Task reset = createResetTask(project, extension, grgitProvider)
    Task copy = createCopyTask(project, extension)
    Task commit = createCommitTask(project, extension, grgitProvider)
    Task push = createPushTask(project, extension, grgitProvider)
    push.dependsOn commit
    commit.dependsOn copy
    copy.dependsOn reset

    // always close the repo at the end of the build
    project.gradle.buildFinished {
      if (extension.ext.has('repo')) {
        project.logger.info('Closing Git publish repo: {}', extension.repo.repository.rootDir)
        extension.repo.close()
      }
    }
  }

  private Task createResetTask(Project project, GitPublishExtension extension, Provider<Grgit> grgitProvider) {
    GitPublishReset task = project.tasks.create(RESET_TASK, GitPublishReset)
    task.with {
      group = 'publishing'
      description = 'Prepares a git repo for new content to be generated.'
      grgit = grgitProvider
      branch = project.provider { extension.branch }
      preserve = extension.preserve
    }
    return task
  }

  private Task createCopyTask(Project project, GitPublishExtension extension) {
    Copy task = project.tasks.create(COPY_TASK, Copy)
    task.with {
      group = 'publishing'
      description = 'Copy contents to be published to git.'
      with(extension.contents)
      into { extension.repoDir }
    }
    return task
  }

  private Task createCommitTask(Project project, GitPublishExtension extension, Provider<Grgit> grgitProvider) {
    GitPublishCommit task = project.tasks.create(COMMIT_TASK, GitPublishCommit)
    task.with {
      group = 'publishing'
      description = 'Commits changes to be published to git.'
      grgit = grgitProvider
      message = project.provider { extension.commitMessage }
    }
    return task
  }

  private Task createPushTask(Project project, GitPublishExtension extension, Provider<Grgit> grgitProvider) {
    GitPublishPush task = project.tasks.create(PUSH_TASK, GitPublishPush)
    task.with {
      group = 'publishing'
      description = 'Pushes changes to git.'
      grgit = grgitProvider
      branch = project.provider { extension.branch }
    }
    return task
  }

  private Optional<Grgit> findExistingRepo(Project project, GitPublishExtension extension) {
    try {
      Optional.of(Grgit.open(dir: extension.repoDir))
        .filter { repo ->
          String originUri = repo.remote.list().find { it.name == 'origin' }?.url
          // need to use the URIish to normalize them and ensure we support all Git compatible URI-ishs (URL is too limiting)
          boolean valid = new URIish(extension.repoUri) == new URIish(originUri) && extension.branch == repo.branch.current.name
          if (!valid) { repo.close() }
          return valid
        }
    } catch (Exception e) {
      // missing, invalid, or corrupt repo
      project.logger.debug('Failed to find existing Git publish repository.', e)
      return Optional.empty()
    }
  }

  private Grgit freshRepo(GitPublishExtension extension) {
    if (!extension.repoDir.deleteDir()) {
      throw new GradleException("Failed to clean up repo dir: ${extension.repoDir}")
    }
    Grgit repo = Grgit.init(dir: extension.repoDir)
    repo.remote.add(name: 'origin', url: extension.repoUri)
    return repo
  }
}
