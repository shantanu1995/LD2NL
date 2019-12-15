/*
 * #%L
 * Triple2NL
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
package org.aksw.triple2nl.converter;

/**
 * Converts IRIs into natural language.
 * @author Lorenz Buehmann
 *
 */
public interface IRIConverter {
	
	/**
	 * Convert the IRI into natural language.
	 * @param iri the IRI
	 * @return a natural language representation
	 */
	String convert(String iri);
	
	/**
	 * Convert the IRI into a natural language.
	 * @param iri the IRI to convert
	 * @param useDereferencing whether to try Linked Data dereferencing of the IRI
	 * @return a natural language representation
	 */
	String convert(String iri, boolean useDereferencing);
}
