package com.felixgrund.codestory.ast.tasks;

import com.felixgrund.codestory.ast.entities.Ycommit;
import com.felixgrund.codestory.ast.entities.Yresult;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.*;

public class MainAnalysis1Task {

	private static final String LANG = "js";
//	private static final String TEST_CONFIG = "checkstyle-WhitespaceAroundCheck-shouldCheckSeparationFromNextToken";
	private static final String TEST_CONFIG = "pocketquery-admin-entityToForm";

	private static final String CODESTORY_REPO_DIR = System.getenv("codestory.repo.dir");

	public static void main(String[] args) throws Exception {
		String configName = TEST_CONFIG;
		ClassLoader classLoader = MainAnalysis1Task.class.getClassLoader();
		File file = new File(classLoader.getResource("stubs/" + LANG + "/" + configName + ".json").getFile());
		String json = FileUtils.readFileToString(file, "utf-8");
		Gson gson = new Gson();
		RunConfig runConfig = gson.fromJson(json, RunConfig.class);
		runConfig.setConfigName(configName);

		String[] pathSplit = runConfig.getFilePath().split("/");
		String filename = pathSplit[pathSplit.length-1];

		System.out.println("Running dynamic test for config: " + configName);

		AnalysisLevel1Task task = new AnalysisLevel1Task();
		task.setRepository(CODESTORY_REPO_DIR + "/" + runConfig.getRepoName() + "/.git");
		task.setBranchName(runConfig.getBranchName());
		task.setFilePath(runConfig.getFilePath());
		task.setFileName(filename);
		task.setFunctionName(runConfig.getFunctionName());
		task.setFunctionStartLine(runConfig.getFunctionStartLine());
		task.setStartCommitName(runConfig.getStartCommitName());

		task.run();

		Yresult yresult = task.getYresult();
		System.out.println("File history...");
		for (Ycommit ycommit : task.getYhistory()) {
			System.out.println(ycommit.getCommit().getName());
		}


		List<String> shortNamesMessages = new ArrayList<>();
		List<String> jsonMessages = new ArrayList<>();
		for (Ycommit ycommit : yresult.keySet()) {
			String message = "\"" + ycommit.getCommit().getName() + "\"";
			shortNamesMessages.add(message);
			message = "\"" + ycommit.getCommit().getName() + "\" : \"" + yresult.get(ycommit) + "\"";
			jsonMessages.add(message);
		}

		System.out.println("\nMethod history...");
		System.out.println(StringUtils.join(shortNamesMessages, ",\n"));

		System.out.println("\nMethod history JSON...");
		System.out.println(StringUtils.join(jsonMessages, ",\n"));

		Set<String> intellijLog = runConfig.getIntellijLog();
		Set<String> codestoryLog = runConfig.getCodestoryLog();
		if (intellijLog != null && codestoryLog != null) {
			Set<String> onlyIntellijLog = new LinkedHashSet<>(intellijLog);
			onlyIntellijLog.removeAll(codestoryLog);
			Set<String> onlyCodestoryLog = new LinkedHashSet<>(codestoryLog);
			onlyCodestoryLog.removeAll(intellijLog);

			System.out.println("\nOnly found in IntelliJ log...");
			System.out.println(StringUtils.join(onlyIntellijLog, "\n"));
			System.out.println("\nOnly found in CodeStory log...");
			System.out.println(StringUtils.join(onlyCodestoryLog, "\n"));
		}

	}

}