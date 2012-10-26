/**
 * 
 */
package org.arachna.netweaver.cobertura;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.velocity.app.VelocityEngine;
import org.arachna.ant.AntHelper;
import org.arachna.netweaver.cobertura.BuildFileGenerator.IBuildFileWriterFactory;
import org.arachna.netweaver.dc.types.Compartment;
import org.arachna.netweaver.dc.types.CompartmentState;
import org.arachna.netweaver.dc.types.DevelopmentComponent;
import org.arachna.netweaver.dc.types.DevelopmentComponentFactory;
import org.arachna.netweaver.dc.types.DevelopmentConfiguration;
import org.arachna.netweaver.dc.types.PublicPart;
import org.arachna.netweaver.dc.types.PublicPartReference;
import org.arachna.netweaver.dc.types.PublicPartType;
import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * Unittests for {@link BuildFileGenerator}.
 * 
 * @author Dirk Weigenand
 */
public class BuildFileGeneratorTest extends XMLTestCase {
    /**
     * path to workspace folder
     */
    private static final String WORKSPACE = "/opt/jenkins/jobs/example-track/workspace";

    /**
     * Folder where development components are located.
     */
    private static final String DCS_FOLDER = WORKSPACE + "/.dtc/DCs";

    /**
     * A sample dc name.
     */
    private static final String SAMPLE_DC1 = "lib/dc1";

    /**
     * Output folder of a DC.
     */
    private static final String CLASSES_DIR = String.format("%s/.dtc/t/1234/classes", WORKSPACE);

    /**
     * An example vendor.
     */
    private static final String VENDOR = "example.org";

    /**
     * Instance under test.
     */
    private BuildFileGenerator generator;

    /**
     * registry for development components.
     */
    private DevelopmentComponentFactory dcFactory;

    /**
     * Helper class for things related to Ant.
     */
    private AntHelper antHelper;

    /**
     * a writer factory that records the content written into the last produced
     * writer.
     */
    private RecordingBuildFileWriterFactory writerFactory;

    /**
     * {@inheritDoc}
     */
    @Override
    @Before
    protected void setUp() throws Exception {
        super.setUp();
        dcFactory = new DevelopmentComponentFactory();
        antHelper = new AntHelper(WORKSPACE, dcFactory) {
            @Override
            public Collection<String> createSourceFileSets(final DevelopmentComponent component) {
                return Arrays.asList("src/packages");
            }

            @Override
            public Set<String> createClassPath(final DevelopmentComponent component) {
                return new HashSet<String>() {
                    {
                        add(getExpectedClassPath());
                    }
                };
            }
        };

        final PublicPart apiPublicPart = new PublicPart("api", "", "", PublicPartType.COMPILE);
        dcFactory.create("sap.com", "sap.com.security.api.sda", new PublicPart[] { apiPublicPart },
            new PublicPartReference[] {});
        final DevelopmentComponent component =
            dcFactory.create(VENDOR, SAMPLE_DC1, new PublicPart[] { apiPublicPart },
                new PublicPartReference[] { new PublicPartReference("sap.com", "sap.com.security.api.sda", "api") });
        component.setOutputFolder(CLASSES_DIR);
        component.addSourceFolder(antHelper.getBaseLocation(component) + "/src/packages");
        final DevelopmentConfiguration config = new DevelopmentConfiguration("DI1_Example_D");
        final Compartment compartment = Compartment.create(VENDOR, "SC1", CompartmentState.Source, "");
        config.add(compartment);
        compartment.add(component);
        createBuildFileGenerator(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @After
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public void testDefaultTarget() {
        assertXPathResult("run-tests-example.org~lib~dc1", "/project/@default");
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public void testPropertyInstrumentedDir() {
        assertXPathResult(DCS_FOLDER + "/example.org/lib/dc1/_comp/gen/instrumented-classes",
            "/project/property[@name='instrumented.dir']/@value");
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public void testClasspathId() {
        assertXPathResult("classpath-example.org~lib~dc1", "/project/path[2]/@id");
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public void testClasspath() {
        final String expected = getExpectedClassPath();
        assertXPathResult(expected, "/project/path[2]/fileset[1]/@dir");
    }

    /**
     * @return
     */
    protected String getExpectedClassPath() {
        final DevelopmentComponent sapComSecurityApi = dcFactory.get("sap.com", "sap.com.security.api.sda");

        return new File(String.format("%s/%s/%s/_comp/gen/default/public/%s/lib/java", DCS_FOLDER,
            sapComSecurityApi.getVendor(), sapComSecurityApi.getName(), sapComSecurityApi.getPublicParts().iterator()
                .next().getPublicPart())).getAbsolutePath();
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public void testPropertyClassesDir() {
        assertXPathResult(CLASSES_DIR, "/project/property[@name='classes.dir']/@value");
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public void testJunitTimeout0WontGenerateJunitTimeoutAttribute() {
        assertXPathResult("0", "count(/project/target[4]/junit[@timeout])");
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public void testJunitTimeoutGreaterThan0GeneratesJunitTimeoutAttribute() {
        createBuildFileGenerator(1);
        assertXPathResult("1", "count(/project/target[4]/junit[@timeout='1'])");
    }

    @Test
    public void testExecute() {
        final DevelopmentComponent component = dcFactory.get(VENDOR, SAMPLE_DC1);
        final Map<DevelopmentComponent, String> buildFiles = generator.execute(Arrays.asList(component));

        assertThat(String.format("%s/cobertura-build.xml", antHelper.getBaseLocation(component)),
            equalTo(buildFiles.get(component)));
    }

    /**
     * 
     */
    protected void createBuildFileGenerator(final int timeout) {
        generator = new BuildFileGenerator(antHelper, new VelocityEngine(), "UTF-8", "", timeout);
        writerFactory = new RecordingBuildFileWriterFactory();
        generator.setWriterFactory(writerFactory);
    }

    /**
     * Assert that the expected string can be selected using the given XPath
     * expression.
     * 
     * @param expected
     *            expected result.
     * @param xPath
     *            XPath to select the real value.
     */
    private void assertXPathResult(final String expected, final String xPath) {
        try {
            final String buildFile = createBuildFile();
            this.assertXpathEvaluatesTo(expected, xPath, buildFile);
        }
        catch (final IOException ioe) {
            fail(ioe.getMessage());
        }
        catch (final XpathException xe) {
            fail(xe.getMessage());
        }
        catch (final SAXException se) {
            fail(se.getMessage());
        }
    }

    /**
     * @return
     * @throws IOException
     */
    private String createBuildFile() throws IOException {
        generator.createBuildFile(dcFactory.get(VENDOR, SAMPLE_DC1), Arrays.asList("src/packages"));

        return writerFactory.getContent();
    }

    private static final class RecordingBuildFileWriterFactory implements IBuildFileWriterFactory {
        private StringWriter buildFileContent;

        /**
         * {@inheritDoc}
         */
        public Writer create(final String buildFileName) throws IOException {
            buildFileContent = new StringWriter();
            return buildFileContent;
        }

        String getContent() {
            return buildFileContent == null ? "" : buildFileContent.toString();
        }
    }
}
