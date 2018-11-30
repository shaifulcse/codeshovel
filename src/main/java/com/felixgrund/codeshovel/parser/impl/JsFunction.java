package com.felixgrund.codeshovel.parser.impl;

import com.felixgrund.codeshovel.entities.Yexceptions;
import com.felixgrund.codeshovel.entities.Ymodifiers;
import com.felixgrund.codeshovel.entities.Yparameter;
import com.felixgrund.codeshovel.parser.AbstractFunction;
import com.felixgrund.codeshovel.parser.Yfunction;
import com.felixgrund.codeshovel.util.Utl;
import com.felixgrund.codeshovel.wrappers.Commit;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.FunctionNode;

import java.util.ArrayList;
import java.util.List;

public class JsFunction extends AbstractFunction<FunctionNode> implements Yfunction {


	public JsFunction(FunctionNode node, Commit commit, String sourceFilePath, String sourceFileContent) {
		super(node, commit, sourceFilePath, sourceFileContent);
	}

	@Override
	public List<Yparameter> getInitialParameters(FunctionNode method) {
		List<Yparameter> parameters = new ArrayList<>();
		List<AstNode> parameterNodes = method.getParams();
		for (AstNode node : parameterNodes) {
			parameters.add(new Yparameter(node.getString(), Yparameter.TYPE_NONE));
		}
		return parameters;
	}

	@Override
	protected String getInitialName(FunctionNode method) {
		return method.getName();
	}

	@Override
	protected String getInitialType(FunctionNode method) {
		return null;
	}

	@Override
	protected Ymodifiers getInitialModifiers(FunctionNode method) {
		return Ymodifiers.NONE;
	}

	@Override
	protected Yexceptions getInitialExceptions(FunctionNode method) {
		return Yexceptions.NONE;
	}

	@Override
	protected String getInitialBody(FunctionNode method) {
		return method.getBody().toSource();
	}

	@Override
	protected int getInitialBeginLine(FunctionNode method) {
		return method.getFunctionName().getLineno(); // TODO: verify
	}

	@Override
	protected int getInitialEndLine(FunctionNode method) {
		int numLines = Utl.countLineNumbers(method.getBody().toSource());
		return method.getFunctionName().getLineno() + numLines; // TODO: verify
	}

	@Override
	protected String getInitialParentName(FunctionNode method) {
		return null;
	}

	@Override
	protected String getInitialFunctionPath(FunctionNode method) {
		return method.getName();
	}

}
