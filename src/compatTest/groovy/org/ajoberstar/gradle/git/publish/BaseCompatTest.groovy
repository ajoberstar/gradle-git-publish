package org.ajoberstar.gradle.git.publish

import org.ajoberstar.grgit.Grgit
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Specification
import spock.lang.TempDir

import java.rmi.UnexpectedException

class BaseCompatTest extends Specification {
  @TempDir File tempDir
  File projectDir
  File buildFile
  Grgit remote

  def setup() {
    projectDir = new File(tempDir, 'project')
    buildFile = projectFile('build.gradle')

    def remoteDir = new File(tempDir, 'remote')
    remote = Grgit.init(dir: remoteDir)

    remoteFile('master.txt') << 'contents here'
    remote.add(patterns: ['.'])
    remote.commit(message: 'first commit', sign: false)

    // handle different init branches to keep existing tests the same
    if (remote.branch.current().name != 'master') {
      remote.checkout(branch: 'master', createBranch: true)
    }

    remote.checkout(branch: 'gh-pages', orphan: true)
    remote.remove(patterns: ['master.txt'])
    remoteFile('index.md') << '# This Page is Awesome!'
    remoteFile('1.0.0/index.md') << '# Version 1.0.0 is the Best!'
    remote.add(patterns: ['.'])
    remote.commit(message: 'first pages commit', sign: false)

    remote.checkout(branch: 'master')
  }

  def 'publish from clean slate results in orphaned branch'() {
    given:
    projectFile('src/content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'my-pages'
  contents.from 'src'
}
"""
    when:
    def result = build()
    and:
    remote.checkout(branch: 'my-pages')
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 1
    remoteFile('content.txt').text == 'published content here'
  }

  def 'publish adds to history if branch already exists'() {
    given:
    projectFile('src/content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'gh-pages'
  contents.from 'src'
}
"""
    when:
    def result = build()
    and:
    remote.checkout(branch: 'gh-pages')
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    remoteFile('content.txt').text == 'published content here'
  }

  def 'publish with fetchDepth 1 adds to history if branch already exists'() {
    given:
    remote.checkout(branch: 'gh-pages')
    remoteFile('index.md') << 'And has great content'
    remote.add(patterns: ['.'])
    remote.commit(message: 'second pages commit', sign: false)
    remote.checkout(branch: 'master')

    projectFile('src/content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'gh-pages'
  fetchDepth = 1
  contents.from 'src'
}
"""
    when:
    def result = build()
    and:
    remote.checkout(branch: 'gh-pages')
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 3
    remoteFile('content.txt').text == 'published content here'
    def working = Grgit.open(dir: "${projectDir}/build/gitPublish/main")
    working.head().parentIds.collect { working.resolve.toCommit(it).parentIds == [] }
    working.close()
  }

  def 'reset uses reference repo objects if available before pulling from remote'() {
    given:
    def referenceDir = new File(tempDir, 'reference')
    def reference = Grgit.clone(dir: referenceDir, uri: repoPath(remote))
    reference.checkout(branch: 'gh-pages', createBranch: true)
    // add a file that will get fetched but not pushed
    def refFile = new File(reference.repository.rootDir, 'src/newFile.txt')
    refFile.parentFile.mkdirs()
    refFile.text = 'Some content'
    reference.add(patterns: ['.'])
    reference.commit(message: 'This wont get pushed', sign: false)

    projectFile('src/content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  referenceRepoUri = '${repoPath(reference)}'
  branch = 'gh-pages'
  contents.from 'src'
}
"""
    when:
    def result = build()
    and:
    remote.checkout(branch: 'gh-pages')
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    remoteFile('content.txt').text == 'published content here'
    !remoteFile('newFile.txt').exists()
  }

  def 'can customize working directory'() {
    given:
    projectFile('src/content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  repoDir = file('build/this-is-custom')
  branch = 'gh-pages'
  contents.from 'src'
}
"""
    when:
    def result = build()
    then:
    projectFile('build/this-is-custom/content.txt').exists()
  }

  def 'can preserve specific files'() {
    given:
    projectFile('src/content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'gh-pages'
  contents.from 'src'

  preserve {
    include '1.0.0/**/*'
  }
}
"""
    when:
    def result = build()
    and:
    remote.checkout(branch: 'gh-pages')
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    remoteFile('content.txt').text == 'published content here'
    remoteFile('1.0.0/index.md').text == '# Version 1.0.0 is the Best!'
    !remoteFile('index.md').exists()
  }

  def 'can publish to multiple subdirectories'() {
    given:
    projectFile('src1/content1.txt') << 'published content1 here'
    projectFile('src2/content2.txt') << 'published content2 here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'gh-pages'
  contents {
    from('src1') {
      into 'dest1'
    }
    from('src2') {
      into 'dest2'
    }
  }
}
"""
    when:
    def result = build()
    and:
    remote.checkout(branch: 'gh-pages')
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    remoteFile('dest1/content1.txt').text == 'published content1 here'
    remoteFile('dest2/content2.txt').text == 'published content2 here'
  }

  def 'skips push and commit if no changes'() {
    given:
    projectFile('src/index.md') << '# This Page is Awesome!'
    projectFile('src/1.0.0/index.md') << '# Version 1.0.0 is the Best!'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'gh-pages'
  contents.from 'src'
}
"""
    when:
    def result = build()
    and:
    remote.checkout(branch: 'gh-pages')
    then:
    result.task(':gitPublishCommit').outcome == TaskOutcome.UP_TO_DATE
    result.task(':gitPublishPush').outcome == TaskOutcome.UP_TO_DATE
  }

  def 'existing working repo is reused if valid'() {
    given:
    def working = Grgit.clone(dir: "${projectDir}/build/gitPublish", uri: repoPath(remote))
    working.checkout(branch: 'master')
    new File(projectDir, 'build/gitPublish/master.txt') << 'working repo was here'
    working.add(patterns: ['.'])
    working.commit(message: 'working repo was here', sign: false)
    working.checkout(branch: 'gh-pages', startPoint: 'origin/gh-pages', createBranch: 'true')
    working.close()

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'gh-pages'
  contents {
    from 'src'
  }
}
"""

    when:
    def result = build()
    and:
    remote.checkout(branch: 'gh-pages')
    working = Grgit.open(dir: "${projectDir}/build/gitPublish")
    working.checkout(branch: 'master')
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    working.head().fullMessage == 'working repo was here'
  }

  def 'existing working repo is scrapped if different remote'() {
    given:
    def badRemoteDir = new File(tempDir, 'badRemote')
    def badRemote = Grgit.init(dir: badRemoteDir)

    new File(badRemoteDir, 'master.txt') << 'bad contents here'
    badRemote.add(patterns: ['.'])
    badRemote.commit(message: 'bad first commit', sign: false)

    // handle different init branches to keep existing tests the same
    if (badRemote.branch.current().name != 'master') {
      badRemote.checkout(branch: 'master', createBranch: true)
    }

    def working = Grgit.clone(dir: "${projectDir}/build/gitPublish", uri: badRemote.repository.rootDir.toURI())
    working.close()

    new File(projectDir, 'content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'gh-pages'
  contents.from 'src'
}
"""

    when:
    def result = build()
    and:
    remote.checkout(branch: 'gh-pages')
    working = Grgit.open(dir: "${projectDir}/build/gitPublish")
    working.checkout(branch: 'master')
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    working.head().fullMessage == 'bad first commit'
  }

  def 'when no git publish tasks are run, build completes successfully'() {
    given:
    buildFile << '''\
plugins {
  id 'org.ajoberstar.git-publish'
}

task hello {
  doLast {
    println 'Hello!'
  }
}
'''
    when:
    build('hello')
    then:
    notThrown(UnexpectedBuildFailure)
  }

  def 'commit message can be changed'() {
     given:
    projectFile('src/content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'gh-pages'
  contents.from 'src'
  commitMessage = "Deploy docs to gh-pages (\${project.name})"
}
"""
    when:
    def result = build()
    and:
    remote.checkout(branch: 'gh-pages')
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    remoteFile('content.txt').text == 'published content here'
    remote.head().fullMessage == "Deploy docs to gh-pages (${projectFile('.').canonicalFile.name})\n"
  }

  def 'can activate signing'() {
    given:
    def config = remote.repository.jgit.repo.config
    config.setBoolean('commit', null, 'gpgSign', false)
    config.save()

    projectFile('src/content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'gh-pages'
  contents.from 'src'
  sign = true
}
"""
    when:
    def result = buildOrFail()

    then:
    def newCommit = remote.resolve.toCommit("gh-pages").id
    def remoteGitDir = "${remote.repository.rootDir}/.git"
    def proc = "git --git-dir ${remoteGitDir} cat-file -p ${newCommit}".execute()
    def latestCommit = proc.in.text
    // either it will work and sign or the key will be missing and it won't be able to
    latestCommit.contains("gpgsig") || result.output.contains("gpg: signing failed: No secret key")
  }

  def 'can deactivate signing'() {
    given:
    def config = remote.repository.jgit.repo.config
    config.setBoolean('commit', null, 'gpgSign', true)
    config.save()

    projectFile('src/content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${repoPath(remote)}'
  branch = 'gh-pages'
  contents.from 'src'
  sign = false
}
"""
    when:
    def result = build()

    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS

    def newCommit = remote.resolve.toCommit("gh-pages").id
    def remoteGitDir = "${remote.repository.rootDir}/.git"
    def proc = "git --git-dir ${remoteGitDir} cat-file -p ${newCommit}".execute()
    def latestCommit = proc.in.text
    !latestCommit.contains('gpgsign')
  }

  private BuildResult build(String... args = ['gitPublishPush', '--stacktrace', '--configuration-cache']) {
    return runner(args).build()
  }

  private BuildResult buildAndFail(String... args = ['gitPublishPush', '--stacktrace', '--configuration-cache']) {
    return runner(args).buildAndFail()
  }

  private BuildResult buildOrFail(String... args = ['gitPublishPush', '--stacktrace', '--configuration-cache']) {
    try {
      return runner(args).build()
    } catch (UnexpectedBuildFailure e) {
      return e.buildResult
    }
  }

  private GradleRunner runner(String... args) {
    return GradleRunner.create()
      .withGradleVersion(System.properties['compat.gradle.version'])
      .withPluginClasspath()
      .withProjectDir(projectDir)
      .forwardOutput()
      .withArguments(args)
  }

  private File remoteFile(String path) {
    File file = new File(remote.repository.rootDir, path)
    file.parentFile.mkdirs()
    return file
  }

  private File projectFile(String path) {
    File file = new File(projectDir, path)
    file.parentFile.mkdirs()
    return file
  }

  private String repoPath(Grgit repo) {
    return repo.repository.rootDir.toPath().toAbsolutePath().toString().replace('\\', '\\\\')
  }
}
