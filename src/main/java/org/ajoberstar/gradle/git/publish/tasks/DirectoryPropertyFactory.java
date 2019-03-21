package org.ajoberstar.gradle.git.publish.tasks;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;

/**
 * For backward compatibility with Gradle 4.x
 */
public class DirectoryPropertyFactory {

    private DirectoryPropertyFactory() {
    }

    public static DirectoryProperty create(Project project) {
        return create(project.getLayout(), project.getObjects());
    }

    public static DirectoryProperty create(ProjectLayout layout, ObjectFactory objectFactory) {
        try {
            return objectFactory.directoryProperty();
        } catch (NoSuchMethodError e) {
            return layout.directoryProperty();
        }
    }

}
