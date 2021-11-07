package com.github.marschall.dependencyagemavenplugin;

import static org.apache.maven.plugins.annotations.LifecyclePhase.VERIFY;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.sonatype.plexus.build.incremental.BuildContext;

@Mojo(
        name = "list-dependencies-age",
        threadSafe = true,
        defaultPhase = VERIFY,
        requiresDependencyResolution = RUNTIME
      )
public class ListDependenciesAgeMojo extends AbstractMojo {

  @Component
  private ProjectDependenciesResolver resolver;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${repositorySystemSession}")
  private RepositorySystemSession repositorySession;

  @Component
  private BuildContext buildContext;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!this.buildContext.hasDelta("pom.xml")) {
      return;
    }

    List<Dependency> dependencies = this.resolveDependencies();
    for (Dependency dependency : dependencies) {
      FileTime creationTime = this.determineAge(dependency);
      if (creationTime != null) {
        LocalDate creationDate = creationTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        this.getLog().info("The creation date of: " + dependency.getArtifact() + " is: " + creationDate);
      }
    }

  }

  private FileTime determineAge(Dependency dependency) throws MojoExecutionException {

    Artifact artifact = dependency.getArtifact();
    if (artifact.getExtension().equals("jar")) {
      try (JarFile jarFile = new JarFile(artifact.getFile())) {
        JarEntry manifest = (JarEntry) jarFile.getEntry("META-INF/MANIFEST.MF");
        if (manifest != null) {
          FileTime creationTime = manifest.getCreationTime();
          if (creationTime != null) {
            return creationTime;
          }
        }
      } catch (IOException e) {
        throw new MojoExecutionException("could not open jar of: " + artifact, e);
      }
    }
    String uriString = "jar:" + artifact.getFile().toURI();
    URI jarUri;
    try {
      jarUri = new URI(uriString);
    } catch (URISyntaxException e) {
      throw new MojoExecutionException("invalid uri: " + uriString, e);
    }
    try (FileSystem jarFileSystem = FileSystems.newFileSystem(jarUri , Collections.emptyMap())) {
      Path manifestPath = jarFileSystem.getPath("META-INF", "MANIFEST.MF");
      if (Files.exists(manifestPath)) {
        return (FileTime) Files.getAttribute(manifestPath, "creationTime");
//        return Files.readAttributes(manifestPath, BasicFileAttributes.class).creationTime();
      }
    } catch (IOException e) {
      throw new MojoExecutionException("could not open jar file system of: " + artifact, e);
    }
    this.getLog().warn("Could not determine age of: " + dependency.getArtifact());
    return null;
  }

  private List<Dependency> resolveDependencies() throws MojoExecutionException {
    DependencyResolutionRequest request = this.newDependencyResolutionRequest();

    DependencyResolutionResult result;
    try {
      result = this.resolver.resolve(request);
    } catch (DependencyResolutionException e) {
      throw new MojoExecutionException("could not resolve dependencies", e);
    }

    return result.getDependencies();
  }

  private DependencyResolutionRequest newDependencyResolutionRequest() {
    DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
    request.setMavenProject(this.project);
    request.setRepositorySession(this.repositorySession);
    return request;
  }

}
