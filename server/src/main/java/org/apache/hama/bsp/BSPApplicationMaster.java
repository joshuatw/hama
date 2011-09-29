/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hama.bsp;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.ipc.YarnRPC;
import org.apache.hadoop.yarn.util.Records;
import org.apache.hama.HamaConfiguration;

/**
 * BSPApplicationMaster is an application master for Apache Hamas BSP Engine.
 */
public class BSPApplicationMaster {

	private static final Log LOG = LogFactory
			.getLog(BSPApplicationMaster.class);

	private Configuration localConf;
	private Configuration jobConf;

	private FileSystem fs;

	private YarnRPC yarnRPC;
	private ApplicationId appId;

	private BSPApplicationMaster(String[] args) throws IOException {
		if (args.length != 1) {
			throw new IllegalArgumentException();
		}

		localConf = new YarnConfiguration();
		jobConf = getSubmitConfiguration(args[0]);
		appId = Records.newRecord(ApplicationId.class);
		yarnRPC = YarnRPC.create(localConf);
		fs = FileSystem.get(localConf);
	}

	private Configuration getSubmitConfiguration(String path) {
		Path jobSubmitPath = new Path(path);
		Configuration jobConf = new HamaConfiguration();
		jobConf.addResource(jobSubmitPath);
		return jobConf;
	}

	private void start() {

	}

	public static void main(String[] args) {
		// TODO we expect getting the qualified path of the job.xml as the first
		// element in the arguments
		try {
			new BSPApplicationMaster(args).start();
		} catch (Exception e) {
			LOG.fatal("Error starting BSPApplicationMaster", e);
			System.exit(1);
		}
	}

}
