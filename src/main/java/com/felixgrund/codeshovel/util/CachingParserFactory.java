package com.felixgrund.codeshovel.util;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.felixgrund.codeshovel.exceptions.NoParserFoundException;
import com.felixgrund.codeshovel.exceptions.ParseException;
import com.felixgrund.codeshovel.parser.Yparser;
import com.felixgrund.codeshovel.parser.impl.JavaParser;
import com.felixgrund.codeshovel.parser.impl.JsParser;
import com.felixgrund.codeshovel.wrappers.Commit;
import com.felixgrund.codeshovel.wrappers.StartEnvironment;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class CachingParserFactory {

	private static final Logger log = LoggerFactory.getLogger(CachingParserFactory.class);

	private static final String FILE_EXTENSION_PATTERN = "\\.";

	private static final String CACHE_BASE_DIR = System.getenv("CACHE_DIR") != null ?
			System.getenv("CACHE_DIR") : (System.getProperty("user.dir") + "/cache");


	public static Yparser getParser(StartEnvironment startEnv, String filePath, String fileContent, Commit commit)
			throws NoParserFoundException, ParseException {

		Yparser parser = null;
		String filePathSplit[] = StringUtils.split(filePath, FILE_EXTENSION_PATTERN);
		if (filePathSplit.length > 0) {
			String fileExtension = filePathSplit[filePathSplit.length - 1];
			parser = loadFromCache(fileExtension, fileContent);
			if (parser == null) {
				if (JsParser.ACCEPTED_FILE_EXTENSION.equals("."+fileExtension)) {
					parser = new JsParser(startEnv, filePath, fileContent, commit);
				} else if (JavaParser.ACCEPTED_FILE_EXTENSION.equals("."+fileExtension)) {
					parser = new JavaParser(startEnv, filePath, fileContent, commit);
				} else {
					throw new NoParserFoundException("No parser found for filename " + filePath);
				}

				// If no parser was created this won't be reached because exception is thrown before.
				saveToCache(fileExtension, fileContent, parser);
			}
		} else {
			throw new NoParserFoundException("File has no file extension. Don't know what parser to use.");
		}

		return parser;
	}

	private static Yparser loadFromCache(String fileExtension, String fileContent) {
		Yparser ret = null;
		Kryo kryo = new Kryo();
		kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
		Input input = null;
		try {
			String cacheFilePath = createCacheFilePath(fileExtension, fileContent);
			FileInputStream inputStream = new FileInputStream(cacheFilePath);
			input = new Input(inputStream);
			ret = kryo.readObject(input, Yparser.class);
			input.close();
		} catch (FileNotFoundException e) {
			log.trace("File was not found when attempting to load from cache");
			// return null
		}
		return ret;
	}

	private static String createCacheFilePath(String fileExtension, String fileContent) {
		return CACHE_BASE_DIR + "/" + fileExtension + "-" + DigestUtils.md5Hex(fileContent) + ".bin";
	}

	private static void saveToCache(String fileExtension, String fileContent, Yparser parser) {
		Kryo kryo = new Kryo();
		kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
		String cacheFilePath = createCacheFilePath(fileExtension, fileContent);
		Output output = null;
		try {
			FileOutputStream outputStream = new FileOutputStream(cacheFilePath);
			output = new Output(outputStream);
			kryo.writeObject(output, parser);
			output.close();
		} catch (FileNotFoundException e) {
			log.warn("Error creating FileOutputStream while saving to cache. Skipping.");
			// Nothing will be saved
		}
	}

}
