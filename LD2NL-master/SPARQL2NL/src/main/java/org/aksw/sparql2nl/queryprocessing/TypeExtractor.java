/*
 * #%L
 * SPARQL2NL
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
package org.aksw.sparql2nl.queryprocessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;
import org.dllearner.kb.sparql.SparqlEndpoint;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.aggregate.AggCountVar;
import org.apache.jena.sparql.expr.aggregate.AggCountVarDistinct;
import org.apache.jena.sparql.expr.aggregate.Aggregator;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementOptional;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;
import org.apache.jena.sparql.syntax.ElementUnion;
import org.apache.jena.sparql.syntax.ElementVisitorBase;
import org.apache.jena.sparql.syntax.PatternVars;
import org.apache.jena.sparql.util.VarUtils;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

public class TypeExtractor extends ElementVisitorBase {
	
	private static final Node TYPE_NODE = NodeFactory.createURI(RDF.type.getURI());

	private List<Var> projectionVars;
	private Map<Var, Set<Triple>> var2Triples;
	public Set<String> explicitTypedVars;
        
	private Map<String, Set<String>> var2TypesMap;
	
	private DomainExtractor domainExtractor;
	private RangeExtractor rangeExtractor;
	
	private boolean inferTypes = false;
	
	private Query query;
	private SparqlEndpoint endpoint;
	private Model model;
	
	private boolean isCount = false;
	
	private int unionDepth = 0;
	
	private QueryExecutionFactory qef;
	
	public TypeExtractor(SparqlEndpoint endpoint) {
		this.endpoint = endpoint;
		
		qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
	}
	
	public TypeExtractor(Model model) {
		this.model = model;
		
		qef = new QueryExecutionFactoryModel(model);
	}
	
	public TypeExtractor(QueryExecutionFactory qef) {
		this.qef = qef;
	}

       
	public Map<String, Set<String>> extractTypes(Query query) {
		this.query = query;
		
		var2TypesMap = new HashMap<>();
		var2Triples = new HashMap<>();
                explicitTypedVars = new HashSet<>();
		projectionVars = query.getProjectVars();
		isCount = false;
		//handle COUNT aggregator by replacing generic var name with var name in COUNT construct
		for(Var v : new ArrayList<>(projectionVars)){
			if(query.getProject().hasExpr(v)){
				Expr expr = query.getProject().getExpr(v);
				if(expr instanceof ExprAggregator){
					Aggregator aggr = ((ExprAggregator) expr).getAggregator();
					if(aggr instanceof AggCountVar || aggr instanceof AggCountVarDistinct){
						projectionVars.remove(v);
						projectionVars.add(aggr.getExprList().get(0).asVar());
						isCount = true;
					}
				}
			}
		}
		
		//if query is ASK query use all variables
		if(query.isAskType()){
			projectionVars = new ArrayList<>(PatternVars.vars(query.getQueryPattern()));
		}
		
		ElementGroup wherePart = (ElementGroup) query.getQueryPattern();
		wherePart.visit(this);
		
		//give all projection vars which have no explicit type the most general type owl:Thing or rdfs:Datatype
		for(Var var : projectionVars){
			if(!var2TypesMap.containsKey(var.getName())){
				var2TypesMap.put(var.getName(), Collections.singleton(inferGenericType(var)));
			}
                        else
                        {
                            explicitTypedVars.add(var.getName());
                        }
		}
		
		return var2TypesMap;
	}
	
	public boolean isCount() {
		return isCount;
	}
	
	/**
	 * Returns the generic type, i.e whether it is owl:Thing(entity) or rdfs:Literal(value)
	 * @param var
	 * @return
	 */
	private String inferGenericType(Var var){
		Set<Triple> triples = var2Triples.get(var);
	
                if (triples == null) return OWL.Thing.getURI();
                
		//if var is in subject position it should not be a literal, but an entity
		for(Triple triple : triples){
			if(triple.getSubject().sameValueAs(var)){
				return OWL.Thing.getURI();
			}
		}
		
		//TODO check if var is used in FILTER
		
		//else we try to infer the type by the predicate type, i.e. whether predicate is of type owl:Data(type)Property or owl:ObjectProperty
		for(Triple triple : triples){
			if(triple.getPredicate().isURI()){
				//if rdfs:label return rdfs:Literal
				if(triple.getPredicate().getURI().equals(RDFS.label.getURI())){
					return RDFS.Literal.getURI();
				}
				Set<Resource> types = getPropertyTypes(triple.getPredicate().getURI());
				for (Resource type : types) {
					if(type.equals(OWL.ObjectProperty)){
						return OWL.Thing.getURI();
					} else if(type.equals(OWL.DatatypeProperty)){
						return RDFS.Literal.getURI();
					}
				}
			} else {
				return RDF.Property.getURI();
			}
		}
		return OWL.Thing.getURI();
		
	}
	
	private Set<Resource> getPropertyTypes(String propertyURI){
		Set<Resource> types = new HashSet<>();
		String query = String.format("SELECT ?type WHERE {<%s> a ?type}", propertyURI);
		
		QueryExecution qe = qef.createQueryExecution(query);
		ResultSet rs = qe.execSelect();
    	while(rs.hasNext()){
    		QuerySolution qs = rs.next();
    		if(!qs.getResource("type").isAnon()){
    			types.add(qs.getResource("type"));
    		}
    	}
    	qe.close();
		return types;
	}
	
	public void setDomainExtractor(DomainExtractor domainExtractor) {
		this.domainExtractor = domainExtractor;
	}
	
	public void setRangeExtractor(RangeExtractor rangeExtractor) {
		this.rangeExtractor = rangeExtractor;
	}
	
	public void setInferTypes(boolean inferTypes) {
		this.inferTypes = inferTypes;
	}

	@Override
	public void visit(ElementGroup el) {
		ElementPathBlock bgp = null;
		for (Iterator<Element> iterator = el.getElements().iterator(); iterator.hasNext();) {
			Element e = iterator.next();
			e.visit(this);
			if(e instanceof ElementUnion){
				if(((ElementUnion) e).getElements().size() == 1){
					Element subElement = ((ElementGroup)((ElementUnion) e).getElements().get(0)).getElements().get(0);
					if(subElement instanceof ElementPathBlock){
						bgp = (ElementPathBlock)subElement;
						iterator.remove();
					}
				}
			} else if(e instanceof ElementPathBlock){
				if(((ElementPathBlock) e).getPattern().getList().size() == 0){
					iterator.remove();
				}
			}
		}
		if(bgp != null){
			el.addElement(bgp);
		}
	}

	@Override
	public void visit(ElementTriplesBlock el) {
		for (Iterator<Triple> iter = el.patternElts(); iter.hasNext();) {
			Triple t = iter.next();
			processTriple(t);
		}
	}

	@Override
	public void visit(ElementPathBlock el) {
		for (Iterator<TriplePath> iter = el.patternElts(); iter.hasNext();) {
			TriplePath tp = iter.next();
			if (tp.isTriple()) {
				Triple t = tp.asTriple();
				boolean toRemove = processTriple(t);
				if(toRemove){
					iter.remove();
				}
			}
		}
	}
	
	@Override
	public void visit(ElementUnion el) {
		unionDepth++;
		for (Iterator<Element> iterator = el.getElements().iterator(); iterator.hasNext();) {
			Element e = iterator.next();
			e.visit(this);
//			if(e instanceof ElementUnion){
//				if(((ElementUnion)e).getElements().isEmpty() || ((ElementPathBlock)((ElementUnion)e).getElements().get(0)).isEmpty()){
//					iterator.remove();
//				}
//			} else if(e instanceof ElementOptional){
//				ElementGroup eg = ((ElementGroup)((ElementOptional)e).getOptionalElement());
//				if(eg.isEmpty() || ((ElementPathBlock)eg.getElements().get(0)).isEmpty()){
//					iterator.remove();
//				}
//			} else {
//				
//			}
			if(isEmptyElement(e) || isEmptyElement(((ElementGroup)e).getElements().get(0))){
				iterator.remove();
			}
		}
		unionDepth--;
	}
	
	private boolean isEmptyElement(Element e){
		if(e instanceof ElementUnion){
			if(((ElementUnion)e).getElements().isEmpty()){
				return true;
			}
		} else if(e instanceof ElementOptional){
			ElementGroup eg = ((ElementGroup)((ElementOptional)e).getOptionalElement());
			if(eg.isEmpty()){
				return true;
			}
		} else if(e instanceof ElementGroup){
			return ((ElementGroup)e).isEmpty();
		} else {
			return ((ElementPathBlock)e).isEmpty();
		}
		return false;
	}
	
	@Override
	public void visit(ElementOptional el) {
		//TODO handle separately
		el.getOptionalElement().visit(this);
	}
	
	/**
	 * Returns TRUE if triple has as predicate rdf:type and a projection variable in the subject position, otherwise FALSE.
	 * @param triple
	 * @return
	 */
	private boolean processTriple(Triple triple){
		Node subject = triple.getSubject();
		Node object = triple.getObject();
		if(unionDepth != 0){
			return false;
		}
		if (triple.predicateMatches(TYPE_NODE)) {//process rdf:type triples	
			if(subject.isVariable()){
				Var subjectVar = Var.alloc(subject.getName());
				for(Var projectVar : projectionVars){
					if(projectVar.equals(subjectVar)){
						if(object.isURI()){
							addType(subjectVar.getName(), object.getURI());
							return true;
						} else if(object.isVariable()){
							addType(subjectVar.getName(), OWL.Thing.getURI());
							addType(object.getName(), RDF.type.getURI());
							return true;
						}
						
					} else if(object.isVariable() && projectVar.equals(Var.alloc(object.getName()))){
						addType(object.getName(), RDF.type.getURI());
					}
					//TODO handle case where object is not a URI
					
				}
			}
		} else if(inferTypes && triple.getPredicate().isURI()){//process triples where predicate is not rdf:type, i.e. use rdfs:domain and rdfs:range for inferencing the type
			Node predicate = triple.getPredicate();
			for(Var projectVar : projectionVars){
				if(subject.isVariable() && projectVar.equals(Var.alloc(subject.getName()))){
					if(domainExtractor != null){
						String domain = domainExtractor.getDomain(predicate.getURI());
						if(domain != null){
							addType(subject.getName(), domain);
						}
					}
				} else if(object.isVariable() && projectVar.equals(Var.alloc(object.getName()))){
					if(rangeExtractor != null){
						String range = rangeExtractor.getRange(predicate.getURI());
						if(range != null){
							addType(object.getName(), range);
						}
					}
				}
			}
		} else {
			for(Var var : VarUtils.getVars(triple)){
				Set<Triple> triples = var2Triples.get(var);
				if(triples == null){
					triples = new HashSet<>();
					var2Triples.put(var, triples);
				}
				triples.add(triple);
			}
		}
		return false;
	}
	
	private void addType(String variable, String type){
		Set<String> types = var2TypesMap.get(variable);
		if(types == null){
			types = new HashSet<>();
			var2TypesMap.put(variable, types);
		}
		types.add(type);
	}
	
	public static void main(String[] args) {
		SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
		
		TypeExtractor extr = new TypeExtractor(endpoint);
		extr.setDomainExtractor(new SPARQLDomainExtractor(endpoint));
		extr.setRangeExtractor(new SPARQLRangeExtractor(endpoint));
		
		String queryString = "PREFIX dbo:<http://dbpedia.org/ontology/> "
			+ "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> "
			+ "SELECT ?s ?o1 WHERE {" 
			+ "{?s ?p ?o. "
			+ "?s1 ?p ?o1. " 
			+ "?s a dbo:Book."
			+ "?s a ?y. ?y rdfs:subClassOf dbo:Film."
			+ "?o1 a dbo:Bridge." 
			+ "?o1 a dbo:Musican."
			+ "?s dbo:birthPlace ?o2."
			+ "?o a dbo:City.}" +
			"UNION{?s a dbo:Table.}" +
			"}";
		org.apache.jena.query.Query q = QueryFactory.create(queryString);
	
		queryString = "PREFIX  res: <http://dbpedia.org/resource/>" +
				"PREFIX  dbo: <http://dbpedia.org/ontology/>" +
				"PREFIX  rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" +
				"SELECT DISTINCT  * WHERE{" +
				"{ res:Abraham_Lincoln dbo:deathPlace ?uri }" +
				"UNION" +
				"{ res:Abraham_Lincoln dbo:birthPlace ?uri }" +
				"?uri rdf:type dbo:Place " +
				"FILTER(regex(?uri, \"France\")) " +
				"OPTIONAL{ ?uri dbo:description ?x }  " +
				"} ";
		q = QueryFactory.create(queryString);
		
		queryString = "PREFIX  res: <http://dbpedia.org/resource/>" +
		"PREFIX  dbo: <http://dbpedia.org/ontology/>" +
		"PREFIX  rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" +
		"ASK{" +
		"{ res:Abraham_Lincoln dbo:deathPlace ?uri }" +
		"UNION" +
		"{ res:Abraham_Lincoln dbo:birthPlace ?uri }" +
		"?uri rdf:type dbo:Place " +
		"FILTER(regex(?uri, \"France\")) " +
		"OPTIONAL{ ?uri dbo:description ?x }  " +
		"} ";
		queryString = "SELECT DISTINCT  ?person ?height WHERE  { " +
        		"?person <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/Person>." +
        		"?person <http://dbpedia.org/ontology/height> ?height.}";
		q = QueryFactory.create(queryString, Syntax.syntaxARQ);
		System.out.println(extr.extractTypes(q));
		System.out.println(q);
		
		
	}
	
	
		
}
