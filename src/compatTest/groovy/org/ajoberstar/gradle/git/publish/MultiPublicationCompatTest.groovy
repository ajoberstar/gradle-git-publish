package org.ajoberstar.gradle.git.publish

import org.ajoberstar.grgit.Grgit
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir

class MultiPublicationCompatTest extends Specification {
  @TempDir File tempDir
  File projectDir
  File buildFile
  Grgit remote1
  Grgit remote2

  def setup() {
    projectDir = new File(tempDir, 'project')
    buildFile = projectFile('build.gradle')

    def remoteDir = new File(tempDir, 'remote')
    remote1 = Grgit.init(dir: remoteDir)

    remote1File('master.txt') << 'contents here'
    remote1.add(patterns: ['.'])
    remote1.commit(message: 'first commit')

    // handle different init branches to keep existing tests the same
    if (remote1.branch.current().name != 'master') {
      remote1.checkout(branch: 'master', createBranch: true)
    }

    remote1.checkout(branch: 'gh-pages', orphan: true)
    remote1.remove(patterns: ['master.txt'])
    remote1File('index.md') << '# This Page is Awesome!'
    remote1File('1.0.0/index.md') << '# Version 1.0.0 is the Best!'
    remote1.add(patterns: ['.'])
    remote1.commit(message: 'first pages commit')

    remote1.checkout(branch: 'master')

    def remote2Dir = new File(tempDir, 'remote2')
    remote2 = Grgit.init(dir: remote2Dir)

    remote2File('master.txt') << 'contents here'
    remote2.add(patterns: ['.'])
    remote2.commit(message: 'first commit')

    // handle different init branches to keep existing tests the same
    if (remote2.branch.current().name != 'master') {
      remote2.checkout(branch: 'master', createBranch: true)
    }

    remote2.checkout(branch: 'gh-pages', orphan: true)
    remote2.remove(patterns: ['master.txt'])
    remote2File('index.md') << '# This Page is Awesomest!'
    remote2File('1.0.0/index.md') << '# Version 1.0.0 is the Best!'
    remote2.add(patterns: ['.'])
    remote2.commit(message: 'first pages commit')

    remote2.checkout(branch: 'master')
  }

  def 'publish multiple publications'() {
    given:
    projectFile('src/content.txt') << 'published content here'
    projectFile('src2/content.txt') << 'second published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  // can configure main at top-level
  repoUri = '${remote1.repository.rootDir.toURI()}'
  contents.from 'src'

  publications {
    // can configure main under publication
    main {
      branch.set('my-pages')
    }

    second {
      repoUri.set('${remote2.repository.rootDir.toURI()}')
      branch.set('gh-pages')
      contents.from 'src2'
    }
  }
}
"""
    when:
    def result = build()
    and:
    remote1.checkout(branch: 'my-pages')
    remote2.checkout(branch: 'gh-pages')
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    remote1.log().size() == 1
    remote1File('content.txt').text == 'published content here'
    and:
    result.task(':gitPublishSecondPush').outcome == TaskOutcome.SUCCESS
    remote2.log().size() == 2
    remote2File('content.txt').text == 'second published content here'
  }

  private BuildResult build(String... args = ['gitPublishPushAll', '--stacktrace', '--info', '--configuration-cache']) {
    return runner(args).build()
  }

  private BuildResult buildAndFail(String... args = ['gitPublishPushAll', '--stacktrace', '--info', '--configuration-cache']) {
    return runner(args).buildAndFail()
  }

  private GradleRunner runner(String... args) {
    return GradleRunner.create()
      .withGradleVersion(System.properties['compat.gradle.version'])
      .withPluginClasspath()
      .withProjectDir(projectDir)
      .forwardOutput()
      .withArguments(args)
  }

  private File remote1File(String path) {
    File file = new File(remote1.repository.rootDir, path)
    file.parentFile.mkdirs()
    return file
  }

  private File remote2File(String path) {
    File file = new File(remote2.repository.rootDir, path)
    file.parentFile.mkdirs()
    return file
  }

  private File projectFile(String path) {
    File file = new File(projectDir, path)
    file.parentFile.mkdirs()
    return file
  }
}
