package com.felixgrund.codestory.ast.interpreters;

import com.felixgrund.codestory.ast.entities.CommitInfo;
import com.felixgrund.codestory.ast.entities.DiffInfo;
import com.felixgrund.codestory.ast.entities.FunctionInfo;
import jdk.nashorn.internal.ir.FunctionNode;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Interpreter {

	private List<String> findings;

	private CommitInfo commitInfo;

	public Interpreter(CommitInfo commitInfo) {
		this.findings = new ArrayList<>();
		this.commitInfo = commitInfo;
	}

	public void interpret() throws IOException {
		if (commitInfo.isFirstFunctionOccurrence()) {
			findings.add("Function was first introduced");
		}
		if (isFunctionNotFoundAnymore()) {
			findings.add("Function was removed");
		}
		if (isFunctionFoundAgain()) {
			findings.add("Function was added");
		}
		if (isFunctionModified()) {
			findings.add("function.modified");
			findEdits();
		}
	}

	private List<Edit> findEdits() throws IOException {
		List<Edit> ret = new ArrayList<>();
		DiffInfo diffInfo = commitInfo.getDiffInfo();
		RawText aSource = new RawText(commitInfo.getPrev().getFileContent().getBytes());
		RawText bSource = new RawText(commitInfo.getFileContent().getBytes());
//		diffInfo.getFormatter().format(diffInfo.getEditList(), aSource, bSource);
		return ret;
	}

	private boolean isFunctionModified() {
		boolean ret = false;
		if (commitInfo.isFunctionFound() && commitInfo.getPrev() != null && commitInfo.getPrev().isFunctionFound()) {
			String bodyCurrent = commitInfo.getMatchedFunctionInfo().getBodyString();
			String bodyPrev = commitInfo.getPrev().getMatchedFunctionInfo().getBodyString();
			if (!bodyCurrent.equals(bodyPrev)) {
				ret = true;
			} else {
				ret = false; // debugger
			}
		}
		return ret;
	}

	private boolean isFunctionNotFoundAnymore() {
		boolean isNotFoundAnymore = false;
		if (isFunctionNotFoundAnymoreUsingFunctionName()) {
			isNotFoundAnymore = true;
			FunctionInfo prev = commitInfo.getPrev().getMatchedFunctionInfo();
			FunctionNode function = commitInfo.getParser().findFunctionByNameAndBody("data", prev.getBodyString());
			int a = 1;
		}
		return isNotFoundAnymore;
	}

	private boolean isFunctionNotFoundAnymoreUsingFunctionName() {
		return !commitInfo.isFunctionFound()
				&& commitInfo.getPrev() != null
				&& commitInfo.getPrev().isFunctionFound();
	}

	private boolean isFunctionFoundAgain() {
		return !commitInfo.isFirstFunctionOccurrence()
				&& commitInfo.isFunctionFound()
				&& commitInfo.getPrev() != null
				&& !commitInfo.getPrev().isFunctionFound();
	}

	public List<String> getFindings() {
		return findings;
	}

}
