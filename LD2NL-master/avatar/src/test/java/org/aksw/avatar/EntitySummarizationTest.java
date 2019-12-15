/*
 * #%L
 * AVATAR
 * %%
 * Copyright (C) 2015 Agile Knowledge Engineering and Semantic Web (AKSW)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
/**
 * 
 */
package org.aksw.avatar;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;

import org.aksw.avatar.dump.DBpediaDumpProcessor;
import org.aksw.avatar.dump.DumpProcessor;
import org.aksw.avatar.dump.LogEntry;
import org.aksw.avatar.dump.LogEntryGrouping;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.base.Charsets;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import org.junit.Test;

/**
 * @author Lorenz Buehmann
 *
 */
public class EntitySummarizationTest {
	
	SparqlEndpointKS ks = new SparqlEndpointKS(SparqlEndpoint.getEndpointDBpediaLiveAKSW());
	String queryLog = "resources/dbpediaLog/dbpedia.log-valid-select.gz";
	DumpProcessor dumpProcessor = new DBpediaDumpProcessor();
	Collection<LogEntry> logEntries;
	int maxNrOfLogEntries = -1;//-1 means load all entries
	EntitySummarizationModelGenerator generator = new EntitySummarizationModelGenerator(ks);

	/**
	 * @throws java.lang.Exception
	 */
//	@Before
	public void setUp() throws Exception {
		if(maxNrOfLogEntries == -1){
			logEntries = dumpProcessor.processDump(queryLog);
		} else {
			logEntries = dumpProcessor.processDump(queryLog, maxNrOfLogEntries);
		}
		new File("summarization").mkdir();
	}

	/**
	 * Test method for {@link org.aksw.avatar.EntitySummarizationModelGenerator#generateModel(java.util.Collection)}.
	 */
//	@Test
	public void testGenerateModel() {
		EntitySummarizationModel model = generator.generateModel(logEntries);
		System.out.println(model);
	}
	
	/**
	 * Test method for {@link org.aksw.avatar.EntitySummarizationModelGenerator#generateModel(java.util.Collection)},
	 * but this time generating the model for each user agent occurring in the query log dump
	 */
//	@Test
	public void testGenerateModelByUserAgent() {
		Multimap<String, LogEntry> groupedByUserAgent = LogEntryGrouping.groupByUserAgent(logEntries);
		
		//generate an entity summarization model for each user agent that occurs in the query log dump
		for (Entry<String, Collection<LogEntry>> entry : groupedByUserAgent.asMap().entrySet()) {
			String userAgent = entry.getKey();
			Collection<LogEntry> entries = entry.getValue();
			
			EntitySummarizationModel model = generator.generateModel(entries);
			try {
				Files.write(model.toString(), new File("summarization/" + userAgent + ".txt"), Charsets.UTF_8);
			} catch (IOException e) {
				e.printStackTrace();
			}
//			System.out.println(userAgent + "\n" + model);
		}
	}

}
