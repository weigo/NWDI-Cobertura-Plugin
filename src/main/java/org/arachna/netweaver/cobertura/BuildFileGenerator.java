/**
 * 
 */
package org.arachna.netweaver.cobertura;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.arachna.ant.AntHelper;
import org.arachna.netweaver.dc.types.DevelopmentComponent;
import org.arachna.util.io.FileFinder;

/**
 * Ant build file generator for junit/cobertura combo for unit testing development components.
 * 
 * @author Dirk Weigenand
 */
public class BuildFileGenerator {
    /**
     * regular expression for matching class path entries against to find a JUnit java archive.
     */
    private static final String JUNIT_JAR_REGEXP = "junit.*\\.jar";

    /**
     * Helper class for setting up an ant task with class path, source file sets etc.
     */
    private final AntHelper antHelper;

    /**
     * The template engine to use for build file generation.
     */
    private final VelocityEngine engine;

    /**
     * timeout for JUnit tests.
     */
    private final int junitTimeOut;

    /**
     * Path to location of cobertura archives.
     */
    private final String coberturaDir;

    /**
     * Encoding of source files.
     */
    private final String encoding;

    /**
     * Producer for build file writers.
     */
    private IBuildFileWriterFactory writerFactory;

    /**
     * Create a new instance of the ant build file generate using the given {@link AntHelper} and {@link VelocityEngine} .
     * 
     * @param antHelper
     *            helper object for ant build file creation
     * @param engine
     *            {@link VelocityEngine} to transform the velocity template into an ant build file.
     * @param encoding
     *            encoding of source files.
     * @param coberturaDir
     *            Path to location of cobertura archives
     * @param junitTimeOut
     *            timeout for JUnit tests
     */
    BuildFileGenerator(final AntHelper antHelper, final VelocityEngine engine, final String encoding, final String coberturaDir,
        final int junitTimeOut) {
        this.antHelper = antHelper;
        this.engine = engine;
        this.encoding = encoding;
        this.coberturaDir = coberturaDir;
        this.junitTimeOut = junitTimeOut;
        setWriterFactory(new BuildFileWriterFactory());
    }

    /**
     * Create the build files for the given development components.
     * 
     * @param components
     *            development components to create build files for.
     * @return a collection of paths to the created build files
     */
    public final Map<DevelopmentComponent, String> execute(final Collection<DevelopmentComponent> components) {
        final Map<DevelopmentComponent, String> buildFileNames = new HashMap<DevelopmentComponent, String>();
        for (final DevelopmentComponent component : components) {
            final Collection<String> sources = antHelper.createSourceFileSets(component);
            sources.addAll(component.getTestSourceFolders());

            if (!sources.isEmpty() && hasJunitInClassPath(component)) {
                buildFileNames.put(component, createBuildFile(component, sources));
            }
        }

        return buildFileNames;
    }

    /**
     * Checks that the given development component has a JUnit archive in the classpath.
     * 
     * @param component
     *            development component to check for existence of JUnit in classpath.
     * @return <code>true</code> when JUnit is contained in the class path of this DC, <code>false</code> otherwise.
     */
    protected boolean hasJunitInClassPath(final DevelopmentComponent component) {
        final Set<String> classPath = antHelper.createClassPath(component);
        boolean hasJunitInClassPath = false;

        for (final String path : classPath) {
            final FileFinder finder = new FileFinder(new File(path), JUNIT_JAR_REGEXP);

            if (!finder.find().isEmpty()) {
                hasJunitInClassPath = true;
                break;
            }

            Logger.getLogger(getClass().getName()).fine(String.format("Could not find a JUnit jar in %s.", path));
        }

        return hasJunitInClassPath;
    }

    /**
     * Creates the build file for running junit tests with cobertura.
     * 
     * @param component
     *            development component to create build file for
     * @param sources
     *            source folders
     * @return the absolute path to the generated build file.
     */
    protected final String createBuildFile(final DevelopmentComponent component, final Collection<String> sources) {
        final String buildFileName = String.format("%s/cobertura-build.xml", antHelper.getBaseLocation(component));
        Writer writer = null;

        try {
            writer = writerFactory.create(buildFileName);
            evaluateContext(component, writer, sources);
        }
        catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        return buildFileName;
    }

    /**
     * Evaluates the template and writes it into the given writer.
     * 
     * @param component
     *            development component to create build file for
     * @param writer
     *            <code>Writer</code> to write the build file into
     * @param sources
     *            source folders
     * @throws IOException
     *             when writing the build file fails
     */
    final void evaluateContext(final DevelopmentComponent component, final Writer writer, final Collection<String> sources)
        throws IOException {
        engine.evaluate(createContext(component, sources), writer, "", getTemplate());
    }

    /**
     * Return a {@link Reader} object for the velocity template used to generate the build file.
     * 
     * @return <code>Reader</code> object for the velocity template used to generate the build file.
     */
    private Reader getTemplate() {
        return new InputStreamReader(this.getClass().getResourceAsStream("cobertura-build.vm"));
    }

    /**
     * Set up the velocity context for transforming the template into an ant build file.
     * 
     * @param component
     *            the development component the build file should be created for.
     * @param sources
     *            collection of folders containing java sources.
     * @return velocity context object
     */
    final Context createContext(final DevelopmentComponent component, final Collection<String> sources) {
        final Context context = new VelocityContext();

        context.put("normalizedComponentName", component.getNormalizedName("~"));
        context.put("componentBase", antHelper.getBaseLocation(component));
        context.put("classpaths", antHelper.createClassPath(component));
        context.put("classesDir", component.getOutputFolder());
        context.put("sources", sources);
        context.put("junitTimeout", junitTimeOut);
        context.put("targetVersion", component.getCompartment().getDevelopmentConfiguration().getSourceVersion());
        context.put("coberturaDir", coberturaDir);
        context.put("encoding", encoding);

        return context;
    }

    /**
     * Set factory for build file writer instances for testing.
     * 
     * @param writerFactory
     *            the writerFactory to set
     */
    final void setWriterFactory(final IBuildFileWriterFactory writerFactory) {
        this.writerFactory = writerFactory;
    }

    /**
     * Factory for writers of build file content.
     * 
     * @author Dirk Weigenand
     */
    interface IBuildFileWriterFactory {
        /**
         * Create a writer for the build file contents.
         * 
         * @param buildFileName
         *            name of build file to write contents to.
         * @return Writer for build file contents.
         * @throws IOException
         *             when creating the writer failed.
         */
        Writer create(String buildFileName) throws IOException;
    }

    /**
     * Factory for build file writers.
     * 
     * @author Dirk Weigenand
     */
    private static final class BuildFileWriterFactory implements IBuildFileWriterFactory {
        /**
         * {@inheritDoc}
         * 
         * @throws IOException
         */
        @Override
        public Writer create(final String buildFileName) throws IOException {
            return new FileWriter(buildFileName);
        }
    }
}
