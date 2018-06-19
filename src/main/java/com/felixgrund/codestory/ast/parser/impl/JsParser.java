package com.felixgrund.codestory.ast.parser.impl;import com.felixgrund.codestory.ast.changes.*;import com.felixgrund.codestory.ast.entities.Ycommit;import com.felixgrund.codestory.ast.entities.Yparameter;import com.felixgrund.codestory.ast.exceptions.ParseException;import com.felixgrund.codestory.ast.parser.AbstractParser;import com.felixgrund.codestory.ast.parser.Yfunction;import com.felixgrund.codestory.ast.parser.Yparser;import jdk.nashorn.internal.ir.FunctionNode;import jdk.nashorn.internal.ir.visitor.SimpleNodeVisitor;import jdk.nashorn.internal.parser.Parser;import jdk.nashorn.internal.runtime.Context;import jdk.nashorn.internal.runtime.ErrorManager;import jdk.nashorn.internal.runtime.Source;import jdk.nashorn.internal.runtime.options.Options;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import java.util.ArrayList;import java.util.List;public class JsParser extends AbstractParser implements Yparser {	private Logger log = LoggerFactory.getLogger(JsParser.class);	private Options parserOptions;	private FunctionNode rootFunctionNode;	public JsParser(String repoName, String filePath, String fileContent, String commitName) {		super(repoName, filePath, fileContent, commitName);		this.parserOptions = new Options("nashorn");		this.parserOptions.set("anon.functions", true);		this.parserOptions.set("parse.only", true);		this.parserOptions.set("scripting", true);		this.parserOptions.set("language", "es6");	}	@Override	public Object parse() throws ParseException {		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();		ErrorManager errorManager = new ErrorManager();		Context context = new Context(this.parserOptions, errorManager, classLoader);		Source source = Source.sourceFor(this.filePath, this.fileContent);		Parser originalParser = new Parser(context.getEnv(), source, errorManager);		this.rootFunctionNode = originalParser.parse();		if (this.rootFunctionNode == null) {			throw new ParseException("Could not parse root function node", this.filePath, this.fileContent);		}		return this.rootFunctionNode;	}	@Override	public boolean functionNamesConsideredEqual(String aName, String bName) {		return aName != null && bName != null &&				(aName.equals(bName) || (aName.startsWith("L:") && bName.startsWith("L:")));	}	@Override	public List<Ysignaturechange> getMajorChanges(Ycommit commit, Yfunction compareFunction) throws Exception {		List<Ysignaturechange> changes = new ArrayList<>();		Yparameterchange yparameterchange = getParametersChange(commit, compareFunction);		Yinfilerename yinfilerename = getFunctionRename(commit, compareFunction);		if (yinfilerename != null) {			changes.add(yinfilerename);		}		if (yparameterchange != null) {			changes.add(yparameterchange);		}		return changes;	}	@Override	public List<Ychange> getMinorChanges(Ycommit commit, Yfunction compareFunction) throws Exception {		List<Ychange> changes = new ArrayList<>();		Ybodychange ybodychange = getBodyChange(commit, compareFunction);		if (ybodychange != null) {			changes.add(ybodychange);		}		return changes;	}	@Override	public Yfunction findFunctionByNameAndLine(String name, int line) {		Yfunction ret = null;		FunctionNode node = findFunction(new FunctionNodeVisitor() {			@Override			public boolean nodeMatches(FunctionNode functionNode) {				return functionNode.getLineNumber() == line && functionNode.getIdent().getName().equals(name);			}		});		if (node != null) {			ret = new JsFunction(node, this.commitName, this.filePath, this.fileContent);		}		return ret;	}	@Override	public List<Yfunction> findFunctionsByLineRange(int beginLine, int endLine) {		List<FunctionNode> matchedFunctions = findAllFunctions(new FunctionNodeVisitor() {			@Override			public boolean nodeMatches(FunctionNode functionNode) {				int lineNumber = functionNode.getLineNumber();				return lineNumber >= beginLine && lineNumber <= endLine;			}		});		return transformNodes(matchedFunctions);	}	@Override	public List<Yfunction> getAllFunctions() {		List<FunctionNode> nodes = findAllFunctions(new FunctionNodeVisitor() {			@Override			public boolean nodeMatches(FunctionNode functionNode) {				return !functionNode.isProgram();			}		});		return transformNodes(nodes);	}	@Override	public Yfunction findFunctionByOtherFunction(Yfunction otherFunction) {		Yfunction function = null;		FunctionNode otherFunctionNode = (FunctionNode) otherFunction.getRawFunction();//		List<Yfunction> candidatesByFunctionPath = findFunctionsByFunctionPath(otherFunctionNode.getName());//		if (candidatesByFunctionPath.size() == 1) {//			function = candidatesByFunctionPath.get(0);//		} else {		List<Yfunction> candidatesByNameAndParams = findFunctionsByNameAndParams(otherFunction);		if (candidatesByNameAndParams.size() == 1) {			function = candidatesByNameAndParams.get(0);		} else if (candidatesByNameAndParams.size() > 1) {			log.warn("Found more than one matches for name and parameters. Finding candidate with highest body similarity");			function = getMostSimilarFunction(candidatesByNameAndParams, otherFunction, true);		}//		}		return function;	}	private List<String> getScopeParts(Yfunction compareFunction) {		List<String> scopeParts = new ArrayList<>();		FunctionNode functionNode = (FunctionNode) compareFunction.getRawFunction();		String functionPath = functionNode.getName();		if (functionPath.contains("#")) {			String[] scopeSplit = functionPath.split("#");			for (int i = 0; i < scopeSplit.length - 1; i++) { // ignore last element because it's the function name				String scopeName = scopeSplit[i];				if (!scopeName.startsWith("L:")) {					scopeParts.add(scopeName);					scopeParts.add(scopeName);				}			}		}		return scopeParts;	}	@Override	public double getScopeSimilarity(Yfunction function, Yfunction compareFunction) {		List<String> functionScopeParts = getScopeParts(function);		List<String> compareFunctionScopeParts = getScopeParts(compareFunction);		double scopeSimilarity;		boolean hasCompareFunctionScope = !compareFunctionScopeParts.isEmpty();		boolean hasThisFunctionScope = !functionScopeParts.isEmpty();		if (!hasCompareFunctionScope && !hasThisFunctionScope) {			scopeSimilarity = 1;		} else if (hasCompareFunctionScope && hasThisFunctionScope) {			int matchedScopeParts = 0;			for (String part : compareFunctionScopeParts) {				if (functionScopeParts.contains(part)) {					matchedScopeParts += 1;				}			}			scopeSimilarity = matchedScopeParts / (double) compareFunctionScopeParts.size();		} else {			scopeSimilarity = 0;		}		return scopeSimilarity;	}	private List<Yfunction> findFunctionsByNameAndParams(Yfunction otherFunction) {		return transformNodes(findAllFunctions(new FunctionNodeVisitor() {			@Override			public boolean nodeMatches(FunctionNode functionNode) {				Yfunction currentFunction = new JsFunction(functionNode, commitName, filePath, fileContent);				List<Yparameter> parametersCurrent = currentFunction.getParameters();				String functionNameCurrent = currentFunction.getName();				boolean nameMatches = functionNamesConsideredEqual(functionNameCurrent, otherFunction.getName());				boolean paramsMatch = parametersCurrent.equals(otherFunction.getParameters());				return nameMatches && paramsMatch;			}		}));	}	private List<Yfunction> findFunctionsByFunctionPath(String functionPath) {		return transformNodes(findAllFunctions(new FunctionNodeVisitor() {			@Override			public boolean nodeMatches(FunctionNode functionNode) {				return functionNode.getName().equals(functionPath);			}		}));	}	private List<Yfunction> transformNodes(List<FunctionNode> nodes) {		List<Yfunction> functions = new ArrayList<>();		for (FunctionNode node : nodes) {			functions.add(new JsFunction(node, this.commitName, this.filePath, this.fileContent));		}		return functions;	}	private FunctionNode findFunction(FunctionNodeVisitor visitor) {		FunctionNode ret = null;		visitor.setOnlyFirstMatch(true);		this.rootFunctionNode.accept(visitor);		List<FunctionNode> matchedNodes = visitor.getMatchedNodes();		if (matchedNodes.size() > 0) {			ret = matchedNodes.get(0);		}		return ret;	}	private List<FunctionNode> findAllFunctions(FunctionNodeVisitor visitor) {		this.rootFunctionNode.accept(visitor);		return visitor.getMatchedNodes();	}	public static abstract class FunctionNodeVisitor extends SimpleNodeVisitor {		private List<FunctionNode> matchedNodes = new ArrayList<>();		private boolean onlyFirstMatch;		public abstract boolean nodeMatches(FunctionNode functionNode);		public FunctionNodeVisitor() {		}		@Override		public boolean enterFunctionNode(FunctionNode functionNode) {			if (nodeMatches(functionNode)) {				matchedNodes.add(functionNode);				if (this.onlyFirstMatch) {					return false;				}			}			return true;		}		public void setOnlyFirstMatch(boolean onlyFirstMatch) {			this.onlyFirstMatch = onlyFirstMatch;		}		public List<FunctionNode> getMatchedNodes() {			return matchedNodes;		}	}}