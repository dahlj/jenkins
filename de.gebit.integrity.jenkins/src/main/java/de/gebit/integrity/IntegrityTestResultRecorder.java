/*******************************************************************************
 * Copyright (c) 2013 Rene Schneider, GEBIT Solutions GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package de.gebit.integrity;

import java.io.IOException;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;

/**
 * The result recorder for Integrity test results. This is the main class of the Integrity Test Result plugin - this
 * class is plugged into the Jenkins as a custom result recorder.
 * 
 * @author Rene Schneider - initial API and implementation
 */
public class IntegrityTestResultRecorder extends Recorder implements SimpleBuildStep {

	/**
	 * The file name pattern string.
	 */
	private final String testResultFileNamePattern;

	/**
	 * Whether "no results" should be ignored.
	 */
	private final Boolean ignoreNoResults;

	/**
	 * Fail the build on test errors.
	 */
	private final Boolean failOnTestErrors;

	/**
	 * Creates a new instance.
	 * 
	 * @param testResultFileNamePattern
	 *            the result file name pattern to use
	 */
	@DataBoundConstructor
	// SUPPRESS CHECKSTYLE LONG ParameterNames
	public IntegrityTestResultRecorder(String testResultFileNamePattern, Boolean ignoreNoResults,
			Boolean failOnTestErrors) {
		this.testResultFileNamePattern = testResultFileNamePattern;
		this.ignoreNoResults = ignoreNoResults;
		this.failOnTestErrors = failOnTestErrors;
	}

	public String getTestResultFileNamePattern() {
		return testResultFileNamePattern;
	}

	public Boolean getIgnoreNoResults() {
		return ignoreNoResults;
	}

	public Boolean getFailOnTestErrors() {
		return failOnTestErrors;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	// @Override
	// public Collection<Action> getProjectActions(AbstractProject<?, ?> aProject) {
	// // SUPPRESS CHECKSTYLE Whitespace
	// return Collections.<Action> singleton(new IntegrityProjectAction(aProject));
	// }

	@Override
	public void perform(Run<?, ?> aRun, FilePath aWorkspace, Launcher aLauncher, TaskListener aListener)
			throws InterruptedException, IOException {
		aListener.getLogger().println("Recording Integrity Test Results");
		IntegrityTestResultAction tempResultAction;

		final String tempExpandedTestResults = aRun.getEnvironment(aListener).expand(this.testResultFileNamePattern);

		try {
			IntegrityCompoundTestResult tempResult = (IntegrityCompoundTestResult) new IntegrityTestResultParser()
					.parseResult(tempExpandedTestResults, aRun, aWorkspace, aLauncher, aListener);

			try {
				tempResultAction = new IntegrityTestResultAction(aRun, tempResult, aListener);
			} catch (NullPointerException exc) {
				throw new AbortException(
						de.gebit.integrity.Messages.integrityTestResultRecorder_BadXML(testResultFileNamePattern));
			}
		} catch (AbortException exc) {
			if (aRun.getResult() == Result.FAILURE) {
				return;
			}

			aListener.getLogger().println(exc.getMessage());
			if (!Boolean.TRUE.equals(ignoreNoResults)) {
				aRun.setResult(Result.FAILURE);
			} else {
				aListener.getLogger().println(
						"Not failing the build because the plugin is configured to ignore if test results are not found");
			}
			return;
		} catch (IOException exc) {
			exc.printStackTrace(aListener.error("Failed to archive test reports"));
			aRun.setResult(Result.FAILURE);
			return;
		}

		aRun.addAction(tempResultAction);

		if (tempResultAction.getResult().getFailCount() > 0 || tempResultAction.getResult().getSkipCount() > 0
				|| tempResultAction.getResult().getExceptionCount() > 0) {
			aRun.setResult(Boolean.TRUE.equals(failOnTestErrors) ? Result.FAILURE : Result.UNSTABLE);
		}
	}

	// @Override
	// public boolean perform(AbstractBuild<?, ?> aBuild, Launcher aLauncher, BuildListener aListener)
	// throws InterruptedException, IOException {
	//
	// }

	/**
	 * This descriptor is used to integrate the {@link IntegrityTestResultRecorder} as a post-build step into Jenkins.
	 * 
	 * @author Rene Schneider - initial API and implementation
	 */
	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		/**
		 * Creates an instance.
		 */
		public DescriptorImpl() {
			load();
		}

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aJobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Publish Integrity Test Results";
		}

		/**
		 * Performs on-the-fly validation on the file mask wildcard.
		 */
		@SuppressWarnings("rawtypes")
		public FormValidation doCheckTestResultFileNamePattern(@AncestorInPath AbstractProject aProject,
				@QueryParameter String aValue) throws IOException {
			return FilePath.validateFileMask(aProject.getSomeWorkspace(), aValue);
		}

	}

}
