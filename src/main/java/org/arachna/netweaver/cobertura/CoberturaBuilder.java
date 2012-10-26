/**
 * 
 */
package org.arachna.netweaver.cobertura;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.velocity.app.VelocityEngine;
import org.arachna.netweaver.dc.types.DevelopmentComponent;
import org.arachna.netweaver.hudson.nwdi.AntTaskBuilder;
import org.arachna.netweaver.hudson.nwdi.DCWithJavaSourceAcceptingFilter;
import org.arachna.netweaver.hudson.nwdi.NWDIBuild;
import org.arachna.netweaver.hudson.nwdi.NWDIProject;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Builder for development components using Cobertura and JUnit tasks.
 * 
 * @author Dirk Weigenand
 */
public final class CoberturaBuilder extends AntTaskBuilder {
    /**
     * descriptor for this builder.
     */
    @Extension(ordinal = 1000)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * timeout for running junit tasks.
     */
    private int junitTimeOut;

    /**
     * encoding of source files.
     */
    private String encoding = "UTF-8";

    /**
     * Create a new instance of a <code></code> using the given timeout for
     * running the junit ant task.
     * 
     * @param junitTimeOut
     *            timeout for junit ant task
     * @param encoding
     *            to use when running Cobertura.
     */
    @DataBoundConstructor
    public CoberturaBuilder(final String junitTimeOut, final String encoding) {
        try {
            if (junitTimeOut != null) {
                final int timeOut = Integer.parseInt(junitTimeOut);

                if (timeOut > 0) {
                    this.junitTimeOut = timeOut;
                }
            }
        }
        catch (final NumberFormatException nfe) {
        }

        if (encoding != null && !encoding.isEmpty()) {
            this.encoding = encoding;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
        throws InterruptedException, IOException {
        final VelocityEngine velocityEngine = getVelocityEngine(listener.getLogger());
        final String coberturaDir =
            String.format("%s/plugins/NWDI-Cobertura-Plugin/WEB-INF/lib", Hudson.getInstance().root.getAbsolutePath()
                .replace("\\", "/"));
        final BuildFileGenerator generator =
            new BuildFileGenerator(getAntHelper(), velocityEngine, getEncoding(), coberturaDir, junitTimeOut);
        final NWDIBuild nwdiBuild = (NWDIBuild)build;
        final Map<DevelopmentComponent, String> buildFiles =
            generator.execute(nwdiBuild.getAffectedDevelopmentComponents(new DCWithJavaSourceAcceptingFilter()));

        boolean result = true;

        for (final Map.Entry<DevelopmentComponent, String> entry : buildFiles.entrySet()) {
            result = execute(build, launcher, listener, "", entry.getValue(), getAntProperties());

            if (result) {
                result =
                    execute(build, launcher, listener, getTargetName(entry.getKey()), entry.getValue(),
                        getAntProperties());
            }

            if (!result) {
                break;
            }
        }

        return result;
    }

    /**
     * Generate name of cobertura report target to execute via ant.
     * 
     * @param component
     *            development component for which to execute cobertura.
     * @return the name of the ant target to execute.
     */
    private String getTargetName(final DevelopmentComponent component) {
        return String.format("cobertura-report-%s~%s", component.getVendor(), component.getName().replace('/', '~'));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getAntProperties() {
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Returns the configured timeout for running JUnit test cases.
     * 
     * @return the junitTimeOut
     */
    public int getJunitTimeOut() {
        return junitTimeOut;
    }

    /**
     * Sets the configured timeout for running JUnit test cases.
     * 
     * @param junitTimeOut
     *            the timeout to set
     */
    public void setJunitTimeOut(final int junitTimeOut) {
        this.junitTimeOut = junitTimeOut;
    }

    /**
     * @return the encoding
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Descriptor for {@link CheckstyleBuilder}.
     */
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * Create descriptor for NWDI-CheckStyle-Builder and load global
         * configuration data.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * 
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
            return NWDIProject.class.equals(aClass);
        }

        /**
         * This human readable name is used in the configuration screen.
         * 
         * @return the human readable name of this builder.
         */
        @Override
        public String getDisplayName() {
            return "NWDI Cobertura Builder";
        }

        /**
         * Return a {@link ListBoxModel} containing the available character
         * sets.
         * 
         * @return the available character sets.
         */
        public ListBoxModel doFillEncodingItems() {
            final ListBoxModel items = new ListBoxModel();

            for (final String charSet : Charset.availableCharsets().keySet()) {
                items.add(charSet, charSet);
            }

            return items;
        }
    }
}
