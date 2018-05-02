package com.felixgrund.codestory.ast.tasks;

import com.felixgrund.codestory.ast.changes.Ychange;
import com.felixgrund.codestory.ast.changes.Ymetachange;
import com.felixgrund.codestory.ast.changes.Ynochange;
import com.felixgrund.codestory.ast.entities.*;
import com.felixgrund.codestory.ast.exceptions.NoParserFoundException;
import com.felixgrund.codestory.ast.exceptions.ParseException;
import com.felixgrund.codestory.ast.interpreters.InterpreterLevel1;
import com.felixgrund.codestory.ast.parser.Yparser;
import com.felixgrund.codestory.ast.util.ParserFactory;
import com.felixgrund.codestory.ast.util.Utl;
import com.google.common.collect.Lists;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class AnalysisLevel1Task {

	private static boolean CACHE_ENABLED = false;

	private Git git;
	private Repository repository;
	private String filePath;
	private String fileName;
	private String branchName;
	private String startCommitName;
	private String functionName;
	private int functionStartLine;

	private List<RevCommit> fileHistory;

	private Yfunction startFunction;
	private Ycommit startCommit;

	private Yhistory currentHistory;
	private Yresult currentResult;

	private List<Yresult> allResults;

	private Ymetachange lastMetaChange;

	private HashMap<String, Ycommit> currentCommitCache;

	public AnalysisLevel1Task() {
		this.allResults = new ArrayList<>();
	}

	public void run() throws Exception {
		this.currentHistory = new Yhistory();
		this.currentCommitCache = new HashMap<>();
		this.lastMetaChange = null;
		this.printAnalysisRun();
		long start = new Date().getTime();
		this.buildAndValidate();

		this.createCommitCollection();
		this.createResult();
		this.printMethodHistory();

		long timeTakenSeconds = (new Date().getTime() - start) / 1000;
//		System.out.println("MEASURE AnalysisLevel1Task in seconds: " + timeTakenSeconds);


		if (this.lastMetaChange != null) {
			Yfunction compareFunction = this.lastMetaChange.getCompareFunction();
			if (compareFunction != null) {
				this.setFunctionName(compareFunction.getName());
				this.setFunctionStartLine(compareFunction.getNameLineNumber());
				this.setStartCommitName(this.lastMetaChange.getCompareCommit().getName());
				this.run();
			}
		}
	}

	public void printMethodHistory() {
		System.out.println("\nMethod history...");
		for (Ycommit ycommit : currentResult.keySet()) {
			System.out.println(ycommit.getCommit().getName() + ": " + currentResult.get(ycommit));
		}
	}

	private void printAnalysisRun() {
		System.out.println("\n====================================================");
		System.out.println(String.format("Running Level 1 Analysis\nCommit: %s\nMethod: %s\nLine: %s",
				this.startCommitName, this.functionName, this.functionStartLine));
		System.out.println("====================================================");
	}

	private void createResult() throws IOException {
		this.currentResult = new Yresult(this.startCommitName, this.functionName, this.functionStartLine);
		for (Ycommit ycommit : this.getCurrentHistory()) {
			if (ycommit.getDate().before(this.startCommit.getDate())) {
				InterpreterLevel1 interpreter = new InterpreterLevel1(ycommit);
				interpreter.interpret();
				Ychange ychange = interpreter.getInterpretation();
				if (!(ychange instanceof Ynochange)) {
					this.currentResult.put(ycommit, interpreter.getInterpretation());
				}
				if (ychange instanceof Ymetachange) {
					this.lastMetaChange = (Ymetachange) ychange;
				}
			}
		}
		this.allResults.add(this.currentResult);
	}

	private void buildAndValidate() throws Exception {
		Utl.checkNotNull("repository", this.repository);
		Utl.checkNotNull("startCommitName", this.startCommitName);
		Utl.checkNotNull("filePath", this.filePath);
		Utl.checkNotNull("fileName", this.fileName);
		Utl.checkNotNull("functionName", this.functionName);
		Utl.checkNotNull("functionStartLine", this.functionStartLine);

		RevCommit startCommitRaw = Utl.findCommitByName(this.repository, this.startCommitName);
		Utl.checkNotNull("startCommit", startCommitRaw);

		String startFileContent = Utl.findFileContent(this.repository, startCommitRaw, this.filePath);
		Utl.checkNotNull("startFileContent", startFileContent);

		Yparser startParser = ParserFactory.getParser(this.fileName, startFileContent);
		startParser.parse();
		this.startFunction = startParser.findFunctionByNameAndLine(this.functionName, this.functionStartLine);
		Utl.checkNotNull("startFunctionNode", this.startFunction);

		String functionPath = this.startFunction.getName();
		Utl.checkNotNull("functionPath", functionPath);

		this.startCommit = getOrCreateYcommit(startCommitRaw);
		Utl.checkNotNull("startCommit", startCommit);
	}

	private void createCommitCollection() throws IOException, GitAPIException, NoParserFoundException {

		if (this.fileHistory == null) {
			LogCommand logCommand = this.git.log().addPath(this.filePath).setRevFilter(RevFilter.NO_MERGES);
			Iterable<RevCommit> revisions = logCommand.call();
			this.fileHistory = Lists.newArrayList(revisions);
		}

		for (RevCommit commit : this.fileHistory) {
			try {
				Ycommit ycommit = getOrCreateYcommit(commit);
				if (commit.getParentCount() > 0) {
					RevCommit parentCommit = commit.getParent(0);
					Ycommit parentYcommit = getOrCreateYcommit(parentCommit);
					ycommit.setParent(parentYcommit);
					ycommit.setYdiff(createDiffInfo(commit, parentCommit));
				}
				this.currentHistory.add(ycommit);
			} catch (ParseException e) {
				System.err.println("ParseException occurred for commit or its parent. Skipping. Commit: " + commit.getName());
			}
		}

	}

	private Ycommit getOrCreateYcommit(RevCommit commit) throws ParseException, IOException, NoParserFoundException {
		String commitName = commit.getName();
		Ycommit ycommit = currentCommitCache.get(commitName);
		if (ycommit != null) {
			return ycommit;
		}

		ycommit = createBaseYcommit(commit);
		if (ycommit.isFileFound()) {
			Yparser parser = ParserFactory.getParser(ycommit.getFileName(), ycommit.getFileContent());
			parser.parse();
			ycommit.setParser(parser);
			List<Yfunction> matchedFunctions = parser.findFunctionsByOtherFunction(this.startFunction);
			int numMatchedNodes = matchedFunctions.size();
			if (numMatchedNodes >= 1) {
				ycommit.setMatchedFunction(matchedFunctions.get(0));
				if (numMatchedNodes > 1) {
					System.err.println("More than one matching function found. Taking first.");
				}
			}
		}
		currentCommitCache.put(commitName, ycommit);
		return ycommit;
	}

	private Ycommit createBaseYcommit(RevCommit commit) throws IOException {
		Ycommit ret = new Ycommit(commit);
		ret.setFileName(this.fileName);
		ret.setFilePath(this.filePath);

		RevTree tree = commit.getTree();
		TreeWalk treeWalk = new TreeWalk(this.repository);
		treeWalk.addTree(tree);
		treeWalk.setRecursive(true);
		treeWalk.setFilter(PathFilter.create(this.filePath));

		if (treeWalk.next()) {
			ObjectId objectId = treeWalk.getObjectId(0);
			String fileContent = Utl.getFileContentByObjectId(this.repository, objectId);
			ret.setFileContent(fileContent);
		}

		return ret;

	}

	private Ydiff createDiffInfo(RevCommit commit, RevCommit prevCommit) throws IOException, GitAPIException {
		Ydiff ret = null;
		ObjectReader objectReader = this.repository.newObjectReader();
		CanonicalTreeParser treeParserNew = new CanonicalTreeParser();
		OutputStream outputStream = System.out;
		DiffFormatter formatter = new DiffFormatter(outputStream);
		formatter.setRepository(this.repository);
		formatter.setDiffComparator(RawTextComparator.DEFAULT);
		treeParserNew.reset(objectReader, commit.getTree());
		CanonicalTreeParser treeParserOld = new CanonicalTreeParser();
		treeParserOld.reset(objectReader, prevCommit.getTree());
		List<DiffEntry> diff = formatter.scan(treeParserOld, treeParserNew);
		for (DiffEntry entry : diff) {
			if (entry.getOldPath().equals(this.filePath)) {
				FileHeader fileHeader = formatter.toFileHeader(entry);
				ret = new Ydiff(entry, fileHeader.toEditList(), formatter);
				break;
			}
		}
		return ret;
	}

	private String createUuidHash() {
		StringBuilder builder = new StringBuilder();
		builder.append(branchName)
				.append(filePath)
				.append(fileName)
				.append(functionName)
				.append(functionStartLine)
				.append(startCommitName);
		return DigestUtils.md5Hex(builder.toString());
	}

	public Yhistory getCurrentHistory() {
		return currentHistory;
	}

	public void setRepository(String repositoryPath) throws IOException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		this.repository = builder.setGitDir(new File(repositoryPath))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build();
		this.git = new Git(repository);
	}

	public void setRepository(Repository repository) throws IOException {
		this.repository = repository;
		this.git = new Git(repository);
	}

	public AnalysisLevel1Task cloneTask() throws IOException {
		AnalysisLevel1Task task = new AnalysisLevel1Task();
		task.setRepository(this.repository);
		task.setBranchName(this.branchName);
		task.setFilePath(this.filePath);
		task.setFileName(this.fileName);
		task.setFunctionName(this.functionName);
		task.setFunctionStartLine(this.functionStartLine);
		task.setStartCommitName(this.startCommitName);
		task.setFileHistory(this.fileHistory);
		task.setCurrentHistory(this.currentHistory);
		return task;
	}

	public void setStartCommitName(String startCommitName) {
		this.startCommitName = startCommitName;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public void setFunctionStartLine(int functionStartLine) {
		this.functionStartLine = functionStartLine;
	}

	public void setFunctionName(String functionName) {
		this.functionName = functionName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public List<RevCommit> getFileHistory() {
		return fileHistory;
	}

	public Yresult getCurrentResult() {
		return currentResult;
	}

	public static boolean isCacheEnabled() {
		return CACHE_ENABLED;
	}

	public static void setCacheEnabled(boolean cacheEnabled) {
		CACHE_ENABLED = cacheEnabled;
	}

	public void setFileHistory(List<RevCommit> fileHistory) {
		this.fileHistory = fileHistory;
	}

	public void setCurrentHistory(Yhistory currentHistory) {
		this.currentHistory = currentHistory;
	}

}
