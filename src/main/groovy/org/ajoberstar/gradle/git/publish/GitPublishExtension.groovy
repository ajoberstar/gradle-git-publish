package org.ajoberstar.gradle.git.publish

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

class GitPublishExtension {
    String repoUri

    String branch

    File repoDir

    final CopySpec contents

    final PatternFilterable preserve

    public GitPublishExtension(Project project) {
        contents = project.copySpec()
        preserve = new PatternSet()
        preserve.include('.git')
    }

    void contents(Action<? super CopySpec> action) {
        action.execute(contents)
    }

    void preserve(Action<? super PatternFilterable> action) {
        action.execute(preserve)
    }
}
