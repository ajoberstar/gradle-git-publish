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
    Grgit remote

    def setup() {
        projectDir = tempDir.newFolder('project')
        buildFile = new File(projectDir, 'build.gradle')

        def remoteDir = tempDir.newFolder('remote')
        remote = Grgit.init(dir: remoteDir)

        remoteFile('master.txt') << 'contents here'
        remote.add(patterns: ['.'])
        remote.commit(message: 'first commit')

        remote.checkout(branch: 'gh-pages', orphan: true)
        remoteFile('index.md') << '# This Page is Awesome!'
        remoteFile('1.0.0/index.md') << '# Version 1.0.0 is the Best!'
        remote.add(patterns: ['.'])
        remote.commit(message: 'first pages commit')

        remote.checkout(branch: 'master')
    }

    def 'publish from clean slate results in orphaned branch'() {
        given:
        def srcDir = new File(projectDir, 'src')
        srcDir.mkdir()
        def contentFile = new File(srcDir, 'content.txt')
        contentFile << 'published content here'

        buildFile << """
plugins {
    id 'org.ajoberstar.git-publish'
}

gitPublish {
    repoUri = '${remote.repository.rootDir.toURI()}'
    branch = 'my-pages'
}

gitPublishCopy.from 'src'
"""

        when:
        def result = create()
            .withArguments('gitPublishPush', '--stacktrace')
            .build()
        and:
        remote.checkout(branch: 'my-pages')
        then:
        result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
        remote.log().size() == 1
        remoteFile('content.txt').text == 'published content here'
    }

    def 'publish adds to history if branch already exists'() {
        given:
        def srcDir = new File(projectDir, 'src')
        srcDir.mkdir()
        def contentFile = new File(srcDir, 'content.txt')
        contentFile << 'published content here'

        buildFile << """
plugins {
    id 'org.ajoberstar.git-publish'
}

gitPublish {
    repoUri = '${remote.repository.rootDir.toURI()}'
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
        remote.log().size() == 2
        remoteFile('content.txt').text == 'published content here'
    }

    private GradleRunner create() {
        return GradleRunner.create()
            .withGradleVersion(System.properties['compat.gradle.version'])
            .withPluginClasspath()
            .withProjectDir(projectDir)
    }

    private File remoteFile(String path, boolean mkdirs = true) {
        File file = new File(remote.repository.rootDir, path)
        if (mkdirs) {
            file.parentFile.mkdirs()
        }
        return file
    }
}
