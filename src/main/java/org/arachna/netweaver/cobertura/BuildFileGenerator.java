/**
 * 
 */
package org.arachna.netweaver.cobertura;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.arachna.ant.AntHelper;
import org.arachna.netweaver.dc.config.DevelopmentConfigurationReader;
import org.arachna.netweaver.dc.types.Compartment;
import org.arachna.netweaver.dc.types.CompartmentState;
import org.arachna.netweaver.dc.types.DevelopmentComponent;
import org.arachna.netweaver.dc.types.DevelopmentComponentFactory;
import org.arachna.netweaver.hudson.nwdi.DCWithJavaSourceAcceptingFilter;
import org.arachna.netweaver.hudson.nwdi.IDevelopmentComponentFilter;
import org.arachna.velocity.VelocityHelper;
import org.arachna.xml.XmlReaderHelper;
import org.xml.sax.SAXException;

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
     * timeout for JUnit tests.
     */
    private final int junitTimeOut;

    /**
     * Path to location of cobertura archives
     */
    private final String coberturaDir;

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
     * @param coberturaDir
     *            Path to location of cobertura archives
     * @param junitTimeOut
     *            timeout for JUnit tests
     */
    BuildFileGenerator(final AntHelper antHelper, final VelocityEngine engine, final PrintStream logger,
        String coberturaDir, int junitTimeOut) {
        this.antHelper = antHelper;
        this.engine = engine;
        this.logger = logger;
        this.coberturaDir = coberturaDir;
        this.junitTimeOut = junitTimeOut;
    }

    /**
     * Create the build files for the given development components.
     * 
     * @param components
     *            development components to create build files for.
     * @return a collection of paths to the created build files
     */
    public Map<DevelopmentComponent, String> execute(Collection<DevelopmentComponent> components) {
        Map<DevelopmentComponent, String> buildFileNames = new HashMap<DevelopmentComponent, String>();

        // FIXME: iterate once over components and filter those with source
        // folders actually containing java sources (helper method in AntHelper
        // maybe?)
        for (DevelopmentComponent component : components) {
            try {
                Collection<String> sources = antHelper.createSourceFileSets(component);

                if (!sources.isEmpty()) {
                    buildFileNames.put(component, createBuildFile(component, sources));
                }
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
     * @param sources
     *            source folders
     * @throws IOException
     *             when writing the build file fails
     */
    private String createBuildFile(DevelopmentComponent component, Collection<String> sources) throws IOException {
        Writer writer = null;
        String buildFileName = null;

        try {
            String name = String.format("%s/cobertura-build.xml", this.antHelper.getBaseLocation(component));
            writer = new FileWriter(name);
            evaluateContext(component, writer, sources);
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
     * @param sources
     *            source folders
     * @throws IOException
     *             when writing the build file fails
     */
    void evaluateContext(DevelopmentComponent component, Writer writer, Collection<String> sources) throws IOException {
        this.engine.evaluate(this.createContext(component, sources), writer, "", getTemplate());
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
    Context createContext(DevelopmentComponent component, Collection<String> sources) {
        Context context = new VelocityContext();

        context.put("vendor", component.getVendor());
        context.put("component", component.getName().replace('/', '~'));
        context.put("componentBase", this.antHelper.getBaseLocation(component));
        context.put("classpaths", antHelper.createClassPath(component));
        context.put("classesDir", component.getOutputFolder());
        context.put("sources", sources);
        context.put("junitTimeout", junitTimeOut);
        context.put("targetVersion", component.getCompartment().getDevelopmentConfiguration().getSourceVersion());
        context.put("coberturaDir", this.coberturaDir);

        return context;
    }

    public static void main(String[] args) throws IOException, SAXException {
        DevelopmentComponentFactory dcFactory = new DevelopmentComponentFactory();
        DevelopmentConfigurationReader reader = new DevelopmentConfigurationReader(dcFactory);
        final String workspace = "/tmp/jenkins/jobs/Libraries70-Projekttrack/workspace";
        new XmlReaderHelper(reader).parse(new FileReader(workspace + "/DevelopmentConfiguration.xml"));
        Collection<DevelopmentComponent> components = new ArrayList<DevelopmentComponent>();
        IDevelopmentComponentFilter filter = new DCWithJavaSourceAcceptingFilter();

        for (Compartment compartment : reader.getDevelopmentConfiguration().getCompartments(CompartmentState.Source)) {
            for (DevelopmentComponent component : compartment.getDevelopmentComponents()) {
                if (filter.accept(component)) {
                    components.add(component);
                }
            }
        }

        BuildFileGenerator generator =
            new BuildFileGenerator(new AntHelper(workspace, dcFactory),
                new VelocityHelper(System.out).getVelocityEngine(), System.out,
                "/workspace/NWDI-Cobertura-Plugin/target/NWDI-Cobertura-Plugin/WEB-INF/lib", 10000);
        generator.execute(components);
    }
}
