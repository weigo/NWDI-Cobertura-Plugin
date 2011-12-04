/**
 * 
 */
package org.arachna.netweaver.cobertura;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.Collection;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
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
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * timeout for running junit tasks.
     */
    private int junitTimeOut = 2;

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
        final BuildFileGenerator generator =
            new BuildFileGenerator(getAntHelper(), velocityEngine, listener.getLogger());
        final NWDIBuild nwdiBuild = (NWDIBuild)build;
        final Collection<String> buildFiles =
            generator.execute(nwdiBuild.getAffectedDevelopmentComponents(new DCWithJavaSourceAcceptingFilter()));

        boolean result = createMasterBuildFile(nwdiBuild, listener.getLogger(), velocityEngine, buildFiles);

        if (result) {
            result = execute(build, launcher, listener, "cobertura-all", "cobertura-all.xml", getAntProperties());
        }

        return result;
    }

    private boolean createMasterBuildFile(final NWDIBuild nwdiBuild, final PrintStream logger,
        final VelocityEngine velocityEngine, final Collection<String> buildFiles) {
        boolean result = false;
        final Context context = new VelocityContext();
        context.put("buildFiles", buildFiles);
        final StringWriter masterBuildFile = new StringWriter();

        try {
            velocityEngine
                .evaluate(context, masterBuildFile, "", getTemplateReader("org/arachna/netweaver/cobertura/"));
            result = true;
            nwdiBuild.getWorkspace().child("cobertura-all.xml").write(masterBuildFile.toString(), "UTF-8");
        }
        catch (final ParseErrorException e) {
            e.printStackTrace(logger);
        }
        catch (final MethodInvocationException e) {
            e.printStackTrace(logger);
        }
        catch (final ResourceNotFoundException e) {
            e.printStackTrace(logger);
        }
        catch (final IOException e) {
            e.printStackTrace(logger);
        }
        catch (final InterruptedException e) {
            e.printStackTrace(logger);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getAntProperties() {
        return String.format("cobertura.dir=%s/plugins/NWDI-Cobertura-Plugin/WEB-INF/lib junit.timeout=%d",
            Hudson.getInstance().root.getAbsolutePath().replace("\\", "/"), junitTimeOut);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
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
