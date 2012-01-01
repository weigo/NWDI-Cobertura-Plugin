/**
 * 
 */
package org.arachna.netweaver.cobertura;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

import org.apache.velocity.app.VelocityEngine;
import org.arachna.ant.AntHelper;
import org.arachna.netweaver.dc.types.DevelopmentComponent;
import org.arachna.netweaver.dc.types.DevelopmentComponentFactory;
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
     * 
     */
    private static final String WORKSPACE = System.getProperty("java.io.tmpdir") + System.currentTimeMillis()
        + "/jenkins/jobs/example.org/workspace";

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

    private DevelopmentComponent sapComSecurityApi;

    private File workspace;

    /**
     * {@inheritDoc}
     */
    @Override
    @Before
    protected void setUp() throws Exception {
        super.setUp();
        workspace =
            new File(System.getProperty("java.io.tmpdir") + File.separatorChar + System.currentTimeMillis() + WORKSPACE);
        workspace.mkdirs();
        dcFactory = new DevelopmentComponentFactory();
        final AntHelper antHelper = new AntHelper(WORKSPACE, dcFactory);

        final PublicPart apiPublicPart = new PublicPart("api", "", "", PublicPartType.COMPILE);
        sapComSecurityApi =
            dcFactory.create("sap.com", "sap.com.security.api.sda", new PublicPart[] { apiPublicPart },
                new PublicPartReference[] {});
        final DevelopmentComponent component =
            dcFactory.create(VENDOR, SAMPLE_DC1, new PublicPart[] { apiPublicPart },
                new PublicPartReference[] { new PublicPartReference("sap.com", "sap.com.security.api.sda", "api") });
        component.setOutputFolder(CLASSES_DIR);
        final String sourceFolderName = antHelper.getBaseLocation(component) + "/src/packages";
        final File sourceFolder = new File(sourceFolderName);
        sourceFolder.mkdirs();
        final File source = new File(sourceFolder, "x.java");
        source.createNewFile();
        component.addSourceFolder(sourceFolderName);
        generator = new BuildFileGenerator(antHelper, new VelocityEngine(), System.err);
        new File(antHelper.getBaseLocation(sapComSecurityApi, apiPublicPart.getPublicPart())).mkdirs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @After
    protected void tearDown() throws Exception {
        super.tearDown();
        workspace.deleteOnExit();
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public final void testDefaultTarget() {
        assertXPathResult("cobertura-example.org~lib~dc1", "/project/@default");
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public final void testPropertyInstrumentedDir() {
        assertXPathResult(DCS_FOLDER + "/example.org/lib/dc1/_comp/gen/instrumented-classes",
            "/project/property[@name='instrumented.dir']/@value");
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public final void testClasspathId() {
        assertXPathResult("classpath-example.org~lib~dc1", "/project/path[2]/@id");
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public final void testClasspath() {
        final String expected =
            new File(String.format("%s/%s/%s/_comp/gen/default/public/%s/lib/java", DCS_FOLDER,
                sapComSecurityApi.getVendor(), sapComSecurityApi.getName(), sapComSecurityApi.getPublicParts()
                    .iterator().next().getPublicPart())).getAbsolutePath();
        assertXPathResult(expected, "/project/path[2]/fileset[1]/@dir");
    }

    /**
     * Test method for
     * {@link org.arachna.netweaver.cobertura.BuildFileGenerator#evaluateContext(org.arachna.netweaver.dc.types.DevelopmentComponent, java.io.Writer)}
     * .
     */
    @Test
    public final void testPropertyClassesDir() {
        assertXPathResult(CLASSES_DIR, "/project/property[@name='classes.dir']/@value");
    }

    private void assertXPathResult(final String expected, final String xPath) {
        try {
            this.assertXpathEvaluatesTo(expected, xPath, createBuildFile());
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
        final StringWriter buildFile = new StringWriter();
        generator.evaluateContext(dcFactory.get(VENDOR, SAMPLE_DC1), buildFile);
        System.err.println(buildFile);

        return buildFile.toString();
    }
}
