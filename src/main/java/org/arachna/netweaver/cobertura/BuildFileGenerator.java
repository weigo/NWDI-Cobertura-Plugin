/**
 * 
 */
package org.arachna.netweaver.cobertura;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.HashSet;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.arachna.ant.AntHelper;
import org.arachna.netweaver.dc.types.DevelopmentComponent;

/**
 * Ant build file generator for junit/cobertura combo for unit testing
 * development components.
 * 
 * @author Dirk Weigenand
 */
public final class BuildFileGenerator {
    /**
     * Helper class for setting up an ant task with class path, source file sets
     * etc.
     */
    private final AntHelper antHelper;

    /**
     * The template engine to use for build file generation.
     */
    private final VelocityEngine engine;

    /**
     * logger for logging errors.
     */
    private final PrintStream logger;

    /**
     * Create a new instance of the ant build file generate using the given
     * {@link AntHelper} and {@link VelocityEngine}.
     * 
     * @param antHelper
     *            helper object for ant build file creation
     * @param engine
     *            {@link VelocityEngine} to transform the velocity template into
     *            an ant build file.
     * @param logger
     *            a logger for logging errors
     */
    BuildFileGenerator(final AntHelper antHelper, final VelocityEngine engine, final PrintStream logger) {
        this.antHelper = antHelper;
        this.engine = engine;
        this.logger = logger;
    }

    /**
     * Create the build files for the given development components.
     * 
     * @param components
     *            development components to create build files for.
     * @return a collection of paths to the created build files
     */
    public Collection<String> execute(Collection<DevelopmentComponent> components) {
        Collection<String> buildFileNames = new HashSet<String>();

        for (DevelopmentComponent component : components) {
            try {
                buildFileNames.add(createBuildFile(component));
            }
            catch (IOException e) {
                e.printStackTrace(this.logger);
            }
        }

        return buildFileNames;
    }

    /**
     * Creates the build file for running junit tests with cobertura.
     * 
     * @param component
     *            development component to create build file for
     * @throws IOException
     *             when writing the build file fails
     */
    private String createBuildFile(DevelopmentComponent component) throws IOException {
        Writer writer = null;
        String buildFileName = null;

        try {
            String name = String.format("%s/cobertura-build.xml", this.antHelper.getBaseLocation(component));
            writer = new FileWriter(name);
            evaluateContext(component, writer);
            buildFileName = name;
        }
        catch (ParseErrorException e) {
            throw new RuntimeException(e);
        }
        catch (MethodInvocationException e) {
            throw new RuntimeException(e);
        }
        catch (ResourceNotFoundException e) {
            throw new RuntimeException(e);
        }
        finally {
            if (writer != null) {
                writer.close();
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
     * @throws IOException
     *             when writing the build file fails
     */
    void evaluateContext(DevelopmentComponent component, Writer writer) throws IOException {
        this.engine.evaluate(this.createContext(component), writer, "xXx", getTemplate());
    }

    /**
     * Return a {@link Reader} object for the velocity template used to generate
     * the build file.
     * 
     * @return <code>Reader</code> object for the velocity template used to
     *         generate the build file.
     */
    private Reader getTemplate() {
        return new InputStreamReader(this.getClass().getResourceAsStream("cobertura-build.vtl"));
    }

    /**
     * Set up the velocity context for transforming the template into an ant
     * build file.
     * 
     * @param component
     *            the development component the build file should be created
     *            for.
     * @return velocity context object
     */
    Context createContext(DevelopmentComponent component) {
        Context context = new VelocityContext();

        context.put("vendor", component.getVendor());
        context.put("component", component.getName().replace('/', '~'));
        context.put("componentBase", this.antHelper.getBaseLocation(component));
        context.put("classpaths", antHelper.createClassPath(component));
        context.put("classesDir", component.getOutputFolder());
        context.put("sources", antHelper.createSourceFileSets(component));

        return context;
    }
}
