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
        new File(projectDir, 'src').mkdirs()

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
        new File(projectDir, 'src/content.txt') << 'published content here'

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
        new File(projectDir, 'src/content.txt') << 'published content here'

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

    def 'can preserve specific files'() {
        given:
        new File(projectDir, 'src/content.txt') << 'published content here'

        buildFile << """
plugins {
    id 'org.ajoberstar.git-publish'
}

gitPublish {
    repoUri = '${remote.repository.rootDir.toURI()}'
    branch = 'gh-pages'

    preserve {
        include '1.0.0/**/*'
    }
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
        remoteFile('1.0.0/index.md').text == '# Version 1.0.0 is the Best!'
        !remoteFile('index.md').exists()
    }

    def 'skips push and commit if no changes'() {
        given:
        new File(projectDir, 'src/index.md') << '# This Page is Awesome!'
        new File(projectDir, 'src/1.0.0').mkdirs()
        new File(projectDir, 'src/1.0.0/index.md') << '# Version 1.0.0 is the Best!'

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
        result.task(':gitPublishCommit').outcome == TaskOutcome.UP_TO_DATE
        result.task(':gitPublishPush').outcome == TaskOutcome.SKIPPED
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


        new File(projectDir, 'src/content.txt') << 'published content here'

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
        working = Grgit.open(dir: "${projectDir}/build/gitPublish")
        working.checkout(branch: 'master')
        then:
        result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
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

        new File(projectDir, 'src/content.txt') << 'published content here'

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
        working = Grgit.open(dir: "${projectDir}/build/gitPublish")
        working.checkout(branch: 'master')
        then:
        result.task(':gitPublishPush').outcome == TaskOutcome.SUCCESS
        remote.log().size() == 2
        working.head().fullMessage != 'bad first commit'
    }


    private GradleRunner create() {
        return GradleRunner.create()
            .withGradleVersion(System.properties['compat.gradle.version'])
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .forwardOutput()
    }

    private File remoteFile(String path, boolean mkdirs = true) {
        File file = new File(remote.repository.rootDir, path)
        if (mkdirs) {
            file.parentFile.mkdirs()
        }
        return file
    }
}
