package org.ajoberstar.gradle.git.publish;

import java.net.URISyntaxException;
import java.util.Optional;

import org.ajoberstar.gradle.git.publish.tasks.GitPublishCommit;
import org.ajoberstar.gradle.git.publish.tasks.GitPublishPush;
import org.ajoberstar.gradle.git.publish.tasks.GitPublishReset;
import org.ajoberstar.grgit.Grgit;
import org.eclipse.jgit.transport.URIish;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;

public class GitPublishPlugin implements Plugin<Project> {
  static final String RESET_TASK = "gitPublishReset";
  static final String COPY_TASK = "gitPublishCopy";
  static final String COMMIT_TASK = "gitPublishCommit";
  static final String PUSH_TASK = "gitPublishPush";

  @Override
  public void apply(Project project) {
    GitPublishExtension extension = project.getExtensions().create("gitPublish", GitPublishExtension.class, project);

    extension.getCommitMessage().set("Publish of Github pages from Gradle.");

    // if using the grgit plugin, default to the repo's origin
    project.getPluginManager().withPlugin("org.ajoberstar.grgit", plugin -> {
      // TODO should this be based on tracking branch instead of assuming origin?
      String repoUri = Optional.ofNullable((Grgit) project.findProperty("grgit"))
          .map(this::getOriginUri)
          .orElse(null);
      extension.getRepoUri().set(repoUri);
    });

    extension.getRepoDir().set(project.getLayout().getBuildDirectory().dir("gitPublish"));

    Provider<Grgit> grgitProvider = project.provider(() -> {
      return findExistingRepo(project, extension).orElseGet(() -> freshRepo(project, extension));
    });

    Task reset = createResetTask(project, extension, grgitProvider);
    Task copy = createCopyTask(project, extension);
    Task commit = createCommitTask(project, extension, grgitProvider);
    Task push = createPushTask(project, extension, grgitProvider);
    push.dependsOn(commit);
    commit.dependsOn(copy);
    copy.dependsOn(reset);

    // always close the repo at the end of the build
    project.getGradle().buildFinished(result -> {
      project.getLogger().info("Closing Git publish repo: {}", extension.getRepoDir().get());
      grgitProvider.get().close();
    });
  }

  private Task createResetTask(Project project, GitPublishExtension extension, Provider<Grgit> grgitProvider) {
    return project.getTasks().create(RESET_TASK, GitPublishReset.class, task -> {
      task.setGroup("publishing");
      task.setDescription("Prepares a git repo for new content to be generated.");
      task.getGrgit().set(grgitProvider);
      task.getBranch().set(extension.getBranch());
      task.setPreserve(extension.getPreserve());
    });
  }

  private Task createCopyTask(Project project, GitPublishExtension extension) {
    return project.getTasks().create(COPY_TASK, Copy.class, task -> {
      task.setGroup("publishing");
      task.setDescription("Copy contents to be published to git.");
      task.with(extension.getContents());
      task.into(extension.getRepoDir());
    });
  }

  private Task createCommitTask(Project project, GitPublishExtension extension, Provider<Grgit> grgitProvider) {
    return project.getTasks().create(COMMIT_TASK, GitPublishCommit.class, task -> {
      task.setGroup("publishing");
      task.setDescription("Commits changes to be published to git.");
      task.getGrgit().set(grgitProvider);
      task.getMessage().set(extension.getCommitMessage());
    });
  }

  private Task createPushTask(Project project, GitPublishExtension extension, Provider<Grgit> grgitProvider) {
    return project.getTasks().create(PUSH_TASK, GitPublishPush.class, task -> {
      task.setGroup("publishing");
      task.setDescription("Pushes changes to git.");
      task.getGrgit().set(grgitProvider);
      task.getBranch().set(extension.getBranch());
    });
  }

  private Optional<Grgit> findExistingRepo(Project project, GitPublishExtension extension) {
    try {
      return Optional.of(Grgit.open(op -> op.setDir(extension.getRepoDir().get().getAsFile())))
          .filter(repo -> {
            try {
              String originUri = getOriginUri(repo);
              // need to use the URIish to normalize them and ensure we support all Git compatible URI-ishs (URL
              // is too limiting)
              boolean valid = new URIish(extension.getRepoUri().get()).equals(new URIish(originUri)) && extension.getBranch().get().equals(repo.getBranch().getCurrent().getName());
              if (!valid) {
                repo.close();
              }
              return valid;
            } catch (URISyntaxException e) {
              throw new RuntimeException("Invalid URI.", e);
            }
          });
    } catch (Exception e) {
      // missing, invalid, or corrupt repo
      project.getLogger().debug("Failed to find existing Git publish repository.", e);
      return Optional.empty();
    }
  }

  private Grgit freshRepo(Project project, GitPublishExtension extension) {
    project.delete(extension.getRepoDir().get().getAsFile());

    Grgit repo = Grgit.init(op -> {
      op.setDir(extension.getRepoDir().get().getAsFile());
    });
    repo.getRemote().add(op -> {
      op.setName("origin");
      op.setUrl(extension.getRepoUri().get());
    });
    return repo;
  }

  private String getOriginUri(Grgit grgit) {
    return grgit.getRemote().list().stream()
        .filter(remote -> remote.getName().equals("origin"))
        .map(remote -> remote.getUrl())
        .findAny()
        .orElse(null);
  }
}
