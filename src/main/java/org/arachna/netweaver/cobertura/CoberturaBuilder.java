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

import java.io.IOException;
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
    private int junitTimeOut = 0;

    /**
     * Create a new instance of a <code></code> using the given timeout for
     * running the junit ant task.
     * 
     * @param junitTimeOut
     *            timeout for junit ant task
     */
    @DataBoundConstructor
    public CoberturaBuilder(final String junitTimeOut) {
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
        throws InterruptedException, IOException {
        final VelocityEngine velocityEngine = getVelocityEngine(listener.getLogger());
        String coberturaDir =
            String.format("%s/plugins/NWDI-Cobertura-Plugin/WEB-INF/lib", Hudson.getInstance().root.getAbsolutePath()
                .replace("\\", "/"));
        final BuildFileGenerator generator =
            new BuildFileGenerator(getAntHelper(), velocityEngine, listener.getLogger(), coberturaDir,
                this.junitTimeOut);
        final NWDIBuild nwdiBuild = (NWDIBuild)build;
        final Map<DevelopmentComponent, String> buildFiles =
            generator.execute(nwdiBuild.getAffectedDevelopmentComponents(new DCWithJavaSourceAcceptingFilter()));

        boolean result = true;

        for (Map.Entry<DevelopmentComponent, String> entry : buildFiles.entrySet()) {
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
     * @param component
     * @return
     */
    private String getTargetName(DevelopmentComponent component) {
        return String.format("cobertura-report-%s~%s", component.getVendor(), component.getName().replace('/', '~'));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getAntProperties() {
        // return
        // String.format("cobertura.dir=%s/plugins/NWDI-Cobertura-Plugin/WEB-INF/lib",
        // Hudson.getInstance().root
        // .getAbsolutePath().replace("\\", "/"));
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
    public void setJunitTimeOut(int junitTimeOut) {
        this.junitTimeOut = junitTimeOut;
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
         */
        @Override
        public String getDisplayName() {
            return "NWDI Cobertura Builder";
        }
    }
}
