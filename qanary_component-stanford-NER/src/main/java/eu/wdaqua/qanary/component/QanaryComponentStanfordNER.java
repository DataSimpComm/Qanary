package eu.wdaqua.qanary.component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Map.Entry;

import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import eu.wdaqua.qanary.message.QanaryMessage;

/**
 * represents a wrapper of the Stanford NER tool used here as a spotter
 * 
 * @author Dennis Diefenbach
 *
 */

@Component
public class QanaryComponentStanfordNER implements QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(QanaryComponentStanfordNER.class);
	/**
	 * default processor of a QanaryMessage
	 */
	public QanaryMessage process(QanaryMessage QanaryMessage) {
		//org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.OFF);
		logger.info("process: {}", QanaryMessage);
		// TODO: implement processing of question
		
		try {
			
			//STEP1: Retrive the named graph end the endpoint
			String endpoint=QanaryMessage.get(new URL(QanaryMessage.endpointKey)).toString();
			String namedGraph=QanaryMessage.get(new URL(QanaryMessage.inGraph)).toString();
			logger.info("store data at endpoint {}", QanaryMessage.get(new URL(QanaryMessage.endpointKey)));
			logger.info("store data in graph {}", namedGraph);
			
			//STEP2: Retrive information that are needed for the computations
			//TODO when "/question" is properly implemented and all things are loaded into the named graph 
			// - The question
			String uriQuestion="http://wdaqua.eu/dummy";
			String question="Brooklyn Bridge was designed by Alfred";
			
			//STEP3: Pass the informations to the component and execute it	
			//TODO: ATTENTION: This should be done only ones when the component is started 
				//Define the properties needed for the pipeline of the Stanford parser
				Properties props = new Properties();
				props.put("annotators", "tokenize, ssplit, pos, lemma, ner");
				//Create a new pipline
				StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
			
			//Create an empty annotation just with the given text
			Annotation document = new Annotation(question);
			//Ru the annotator on question
			pipeline.annotate(document);
			
			
			//Identify which parts of the question is tagged by the NER tool
			//Go ones though the question, 
			ArrayList<Selection> selections = new ArrayList<Selection>();
			CoreLabel startToken=null; //stores the last token with non-zero tag, if it does not exist set to null
			CoreLabel endToken=null; //stores the last found token with non-zero tag, if it does not exist set to null
			//Iterate over the tags
			for (CoreLabel token: document.get(TokensAnnotation.class)) {
				logger.info("tagged question {}", token.toString()+token.get(NamedEntityTagAnnotation.class));
	            //System.out.println(token.get(NamedEntityTagAnnotation.class));
	            if (token.get(NamedEntityTagAnnotation.class).equals("O")==false){
	            	if (startToken==null){
	            		startToken=token;
	            		endToken=token;
	            	} else {
	            		if (startToken.get(NamedEntityTagAnnotation.class)==token.get(NamedEntityTagAnnotation.class)){
	            			endToken=token;
	            		} else {
	            			Selection s = new Selection();
	            			s.begin=startToken.beginPosition();
	                		s.end=endToken.endPosition();
	                		selections.add(s);
	                		startToken=token;
	            		}
	            	}
	            } else {
	            	if (startToken!=null){ 
	            		Selection s = new Selection();
	            		s.begin=startToken.beginPosition();
	            		s.end=endToken.endPosition();
	            		selections.add(s);
	            		startToken=null;
	            		endToken=null;
	            	}
	            }
			}
			if (startToken!=null){ 
	    		Selection s = new Selection();
	    		s.begin=startToken.beginPosition();
	    		s.end=endToken.endPosition();
	    		selections.add(s);
	    	}
			
			logger.info("apply vocabulary alignment on outgraph");
			
			//STEP4: Push the result of the component to the triplestore
			long startTime = System.currentTimeMillis();
			for (Selection s: selections){
				String sparql="prefix qa: <http://www.wdaqua.eu/qa#> "
						 +"prefix oa: <http://www.w3.org/ns/openannotation/core/> "
						 +"prefix xsd: <http://www.w3.org/2001/XMLSchema#> "
						 +"INSERT { "
						 +"GRAPH <"+namedGraph+"> { "
						 +"  ?a a qa:AnnotationOfNamedEntity . "
						 +"  ?a oa:hasTarget [ "
						 +"           a    oa:SpecificResource; "
						 +"           oa:hasSource    <"+uriQuestion+">; "
						 +"           oa:hasSelector  [ "
						 +"                    a oa:TextPositionSelector ; "
						 +"                    oa:start \""+s.begin+"\"^^xsd:nonNegativeInteger ; "
						 +"                    oa:end  \""+s.end+"\"^^xsd:nonNegativeInteger  "
						 +"           ] "
						 +"  ] ; "
						 +"     oa:annotatedBy <http://nlp.stanford.edu/software/CRF-NER.shtml> ; "
						 +"	    oa:AnnotatedAt ?time  "
						 +"}} "
						 +"WHERE { " 
						 +"BIND (IRI(str(RAND())) AS ?a) ."
						 +"BIND (now() as ?time) "
					     +"}";
				loadTripleStore(sparql, endpoint);
			}
			long estimatedTime = System.currentTimeMillis() - startTime;
			logger.info("Time {}", estimatedTime);		

		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		return QanaryMessage;
	}
	
	public void loadTripleStore(String sparqlQuery, String endpoint){
		UpdateRequest request = UpdateFactory.create(sparqlQuery) ;
		UpdateProcessor proc = UpdateExecutionFactory.createRemote(request, endpoint);
	    proc.execute() ;
	}
	
	class Selection {
		public int begin;
		public int end;
	}
	
}