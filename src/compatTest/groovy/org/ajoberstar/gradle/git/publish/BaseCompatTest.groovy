package org.ajoberstar.gradle.git.publish

import spock.lang.Specification
import spock.lang.Unroll

import org.ajoberstar.grgit.Grgit
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BaseCompatTest extends Specification {
    @Rule TemporaryFolder tempDir = new TemporaryFolder()
    File projectDir
    File buildFile

    def setup() {
        projectDir = tempDir.newFolder('project')
        buildFile = new File(projectDir, 'build.gradle')
    }

    def 'publish from clean slate results in orphaned branch'() {
        given:
        def remoteDir = tempDir.newFolder('remote')
        def remote = Grgit.init(dir: remoteDir)
        def masterFile = new File(remoteDir, 'master.txt')
        masterFile << 'contents here'
        remote.add(patterns: ['.'])
        remote.commit(message: 'first commit')

        def srcDir = new File(projectDir, 'src')
        srcDir.mkdir()
        def contentFile = new File(srcDir, 'content.txt')
        contentFile << 'published content here'

        buildFile << """
plugins {
    id 'org.ajoberstar.git-publish'
}

gitPublish {
    repoUri = '${remoteDir.toURI()}'
    branch = 'gh-pages'
}

gitPublishCopy.from 'src'
"""

        when:
        def result = create()
            .withArguments('gitPublishPush', '--stacktrace')
            .build()
        and:
        remote.checkout(branch: 'gh-pages')
        then:
        result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
        remote.log().size() == 1
        new File(remoteDir, 'content.txt').text == 'published content here'
    }

    private GradleRunner create() {
        return GradleRunner.create()
            .withGradleVersion(System.properties['compat.gradle.version'])
            .withPluginClasspath()
            .withProjectDir(projectDir)
    }
}
