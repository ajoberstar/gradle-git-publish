package org.ajoberstar.gradle.git.publish

import groovy.transform.PackageScope

import org.ajoberstar.grgit.Grgit
import org.gradle.api.Action
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

class GitPublishExtension {
    String repoUri

    String branch

    File repoDir

    final PatternFilterable preserve = new PatternSet()

    public GitPublishExtension() {
        preserve.include('.git')
    }

    void preserve(Action<? super PatternFilterable> action) {
        action.execute(preserve)
    }
}
