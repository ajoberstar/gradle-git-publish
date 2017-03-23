/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ajoberstar.gradle.git.publish

import spock.lang.Specification
import spock.lang.Unroll

import org.ajoberstar.grgit.Grgit
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BaseCompatTest extends Specification {
  @Rule TemporaryFolder tempDir = new TemporaryFolder()
  File projectDir
  File buildFile
  Grgit remote

  def setup() {
    projectDir = tempDir.newFolder('project')
    buildFile = projectFile('build.gradle')

    def remoteDir = tempDir.newFolder('remote')
    remote = Grgit.init(dir: remoteDir)

    remoteFile('master.txt') << 'contents here'
    remote.add(patterns: ['.'])
    remote.commit(message: 'first commit')

    remote.checkout(branch: 'gh-pages', orphan: true)
    remote.remove(patterns: ['master.txt'])
    remoteFile('index.md') << '# This Page is Awesome!'
    remoteFile('1.0.0/index.md') << '# Version 1.0.0 is the Best!'
    remote.add(patterns: ['.'])
    remote.commit(message: 'first pages commit')

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
  repoUri = '${remote.repository.rootDir.toURI()}'
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
  repoUri = '${remote.repository.rootDir.toURI()}'
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
    result.task(':gitPublishClose').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    remoteFile('content.txt').text == 'published content here'
  }

  def 'can preserve specific files'() {
    given:
    projectFile('src/content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${remote.repository.rootDir.toURI()}'
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
    result.task(':gitPublishClose').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    remoteFile('content.txt').text == 'published content here'
    remoteFile('1.0.0/index.md').text == '# Version 1.0.0 is the Best!'
    !remoteFile('index.md').exists()
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
  repoUri = '${remote.repository.rootDir.toURI()}'
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
    result.task(':gitPublishPush').outcome == TaskOutcome.SKIPPED
    result.task(':gitPublishClose').outcome == TaskOutcome.SUCCESS
  }

  def 'existing working repo is reused if valid'() {
    given:
    def working = Grgit.clone(dir: "${projectDir}/build/gitPublish", uri: remote.repository.rootDir.toURI())
    working.checkout(branch: 'master')
    new File(projectDir, 'build/gitPublish/master.txt') << 'working repo was here'
    working.add(patterns: ['.'])
    working.commit(message: 'working repo was here')
    working.checkout(branch: 'gh-pages', startPoint: 'origin/gh-pages', createBranch: 'true')
    working.close()

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${remote.repository.rootDir.toURI()}'
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
    result.task(':gitPublishClose').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    working.head().fullMessage == 'working repo was here'
  }

  def 'existing working repo is scrapped if different remote'() {
    given:
    def badRemoteDir = tempDir.newFolder('badRemote')
    def badRemote = Grgit.init(dir: badRemoteDir)

    new File(badRemoteDir, 'master.txt') << 'bad contents here'
    badRemote.add(patterns: ['.'])
    badRemote.commit(message: 'bad first commit')

    def working = Grgit.clone(dir: "${projectDir}/build/gitPublish", uri: badRemote.repository.rootDir.toURI())
    working.checkout(branch: 'gh-pages', startPoint: 'origin/master', createBranch: true)
    working.close()

    new File(projectDir, 'content.txt') << 'published content here'

    buildFile << """
plugins {
  id 'org.ajoberstar.git-publish'
}

gitPublish {
  repoUri = '${remote.repository.rootDir.toURI()}'
  branch = 'gh-pages'
  contents.from 'src'
}
"""

    when:
    def result = build()
    and:
    remote.checkout(branch: 'gh-pages')
    working = Grgit.open(dir: "${projectDir}/build/gitPublish")
    then:
    result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
    result.task(':gitPublishClose').outcome == TaskOutcome.SUCCESS
    remote.log().size() == 2
    working.branch.list()*.name == ['gh-pages']
  }


  private BuildResult build() {
    return GradleRunner.create()
      .withGradleVersion(System.properties['compat.gradle.version'])
      .withPluginClasspath()
      .withProjectDir(projectDir)
      .forwardOutput()
      .withArguments('gitPublishPush', '--stacktrace')
      .build()
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
}
