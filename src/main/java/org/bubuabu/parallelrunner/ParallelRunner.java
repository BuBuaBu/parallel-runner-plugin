/*
 * Copyright 2012 Vivien HENRIET
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bubuabu.parallelrunner;

import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.twdata.maven.mojoexecutor.MojoExecutor;
import org.twdata.maven.mojoexecutor.MojoExecutor.ExecutionEnvironment;

/**
 * @goal test
 * 
 * @phase test
 * @requiresDependencyResolution  test
 */
public class ParallelRunner extends AbstractMojo
{
	/**
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject project;

	/**
	 * @parameter expression="${session}"
	 * @required
	 * @readonly
	 */
	private MavenSession session;

	/**
	 * @parameter expression="${session}"
	 * @component
	 * @required
	 */
	private BuildPluginManager pluginManager;

	/**
	 * Plugins List.
	 *
	 * @parameter
	 * @required
	 */
	private Plugin plugin;

	/**
	 * Plugin configurations to use in the execution.
	 *
	 * @parameter
	 * @required
	 */
	private List<XmlPlexusConfiguration> configurations;

	private class MojoThread extends Thread
	{
		private XmlPlexusConfiguration execConfiguration;
		private Plugin execPlugin;
		private MavenProject execProject;
		private MavenSession execSession;
		private BuildPluginManager execPluginManager;
		private Exception exception = null;

		public MojoThread(XmlPlexusConfiguration tConfig, Plugin tPlugin, MavenProject tProject, MavenSession tSession, BuildPluginManager tPluginManager)
		{
			this.execConfiguration = tConfig;
			this.execPlugin = tPlugin;
			this.execProject = tProject;
			this.execSession = tSession;
			this.execPluginManager = tPluginManager;
		}

		public Exception getException()
		{
			return this.exception;
		}

		private void executeMojo(Plugin threadPlugin, String goal, XmlPlexusConfiguration config, ExecutionEnvironment env)
		{
			try 
			{
				MojoExecutor.executeMojo(threadPlugin, goal, config, env);
			}
			catch (Exception e)
			{
				this.exception = e;
			}
		}

		public void run()
		{
			List<PluginExecution> executions = this.execPlugin.getExecutions();
			getLog().info("There is " + executions.size() + " executions.");
			if(executions.size()==0)
			{
				this.executeMojo(this.execPlugin, null, this.execConfiguration, executionEnvironment(this.execProject, this.execSession, this.execPluginManager));
			}
			for(PluginExecution execution: executions)
			{
				List<String> goals = execution.getGoals();
				getLog().info("There is " + goals.size() + " goals.");
				for(String goal: goals)
				{
					this.executeMojo(this.execPlugin, goal, this.execConfiguration, executionEnvironment(this.execProject, this.execSession, this.execPluginManager));
				}
			}
		}	
	}

	public void execute() throws MojoExecutionException, MojoFailureException
	{
		ArrayList<MojoThread> threads = new ArrayList<MojoThread>();
		ArrayList<Exception> exceptions = new ArrayList<Exception>();

		getLog().info("There is " + this.configurations.size() + " configurations.");
		for(XmlPlexusConfiguration config: this.configurations)
		{
			MojoThread thread = new MojoThread(config, this.plugin, this.project, this.session, this.pluginManager);
			threads.add(thread);
		}

		for(MojoThread thread: threads)
		{
			thread.start();
		}
		
		for(MojoThread thread: threads)
		{
			try
			{
				thread.join();
				Exception e = thread.getException();
				if(e != null)
				{
					exceptions.add(e);
				}
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
				throw new MojoFailureException("Unhandled Exception");
			}
		}
		if(exceptions.size()>0)
		{
			Exception e = exceptions.get(0);
			if (e instanceof MojoFailureException)
			{
				throw (MojoFailureException)e;
			}
			else if (e instanceof MojoExecutionException)
			{
				throw (MojoExecutionException)e;
			}
			else
			{
				throw new MojoFailureException("Unhandled Exception");
			}
		}
	}
}
