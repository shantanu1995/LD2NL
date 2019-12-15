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
package org.aksw.triple2nl.gender;

import java.util.HashSet;
import java.util.Set;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.web.HttpSC;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

/**
 * @author Lorenz Buehmann
 *
 */
public class TypeAwareGenderDetector implements GenderDetector{
	
	private static final Logger logger = LoggerFactory.getLogger(TypeAwareGenderDetector.class);

	private QueryExecutionFactory qef;
	
	private GenderDetector genderDetector;
	private Set<String> personTypes = new HashSet<>();
	
	private boolean useInference = true;

	public TypeAwareGenderDetector(QueryExecutionFactory qef, GenderDetector genderDetector) {
		this.qef = qef;
		this.genderDetector = genderDetector;
	}
	
	public TypeAwareGenderDetector(SparqlEndpoint endpoint, GenderDetector genderDetector) {
		this(new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs()), genderDetector);
	}
	
	public void setPersonTypes(Set<String> personTypes){
		this.personTypes = personTypes;
		
		//get the inferred sub types as well
		if(useInference){
			Set<String> inferredTypes = new HashSet<>();
			String queryTemplate = "select ?sub where{?sub <http://www.w3.org/2000/01/rdf-schema#subClassOf>* <%s>.}";
			for (String type : personTypes) {
				String query = String.format(queryTemplate, type);
				try(QueryExecution qe = qef.createQueryExecution(query)) {
					ResultSet rs = qe.execSelect();
					while(rs.hasNext()){
						inferredTypes.add(rs.next().getResource("sub").getURI());
					}
				}
			}
			personTypes.addAll(inferredTypes);
		}
	}
	
	public Gender getGender(String uri, String label) {
		if(isPerson(uri)){
			return genderDetector.getGender(label);
		}
		return Gender.UNKNOWN;
	}

	/* (non-Javadoc)
	 * @see org.aksw.sparql2nl.entitysummarizer.gender.GenderDetector#getGender(java.lang.String)
	 */
	@Override
	public Gender getGender(String name) {
		return genderDetector.getGender(name);
	}
	
	private boolean isPerson(String uri){
		if(personTypes.isEmpty()){
			return true;
		} else {
			//g et types of URI
			Set<String> types = new HashSet<>();
			try {
				String query = "SELECT ?type WHERE {<" + uri + "> a ?type.}";
				try(QueryExecution qe = qef.createQueryExecution(query)) {
					ResultSet rs = qe.execSelect();
					while(rs.hasNext()){
						types.add(rs.next().getResource("type").getURI());
					}
				}
			} catch (Exception e) {
				int code = ((QueryExceptionHTTP)e.getCause()).getResponseCode();
				logger.warn("SPARQL query execution failed: " + code + " - " + HttpSC.getCode(code).getMessage());
			}
			// check for overlap between types of entity and person types
			return !Sets.intersection(personTypes, types).isEmpty();
		}
	}
}
