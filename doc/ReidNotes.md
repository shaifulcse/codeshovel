# Shaiful data edits

Shaiful edited several data files: https://github.com/shaifulcse/codeshovel/commits/master

I'm not clear on what was the nature of these edits? Was it to fix errors in the oracle, or was it for some other reason?

QUESTION: Right now, in `codeshovel/reid_icse2021` only one of these tests currently fails (`Z_elasticsearch-RestRequest-method`), all others pass with the data files that do _not_ have these changes. Should these changes be in the official oracle (aka are the `shaiful/master` the right oracle files, or are the files in `codeshovel/reid_icse2021` the right ones)?

Below are the list of files that are different in `shaiful/master` than `codeshovel/reid_icse2021`:

### test set

* `okhttp-JavaNetAuthenticator-authenticate.json`

* `spring-framework-GenericConversionService-getConverter.json`

### validation set

* `Z_commons-io-CopyUtils-copy.json`

* `Z_elasticsearch-BulkRequest-add.json`

	* https://github.com/shaifulcse/codeshovel/commit/e5207d836a1d16c79f040e1b22e71c572d47b759

* `Z_elasticsearch-RestRequest-method.json`

	* this still fails in reid branch

* `Z_hibernate-search-FullTextQueryImpl-setTimeout.json`

* `Z_intellij-community-PositionManagerImpl-createPrepareRequests.json`

* `Z_jetty-StdErrLog-escape.json` 

* `Z_lucene-solr-Field-tokenStream.json` 

* `Z_lucene-solr-IndexWriter-shutdown.json` 

* `Z_mockito-MatchersBinder-bindMatchers.json`

* `Z_mockito-ReturnsArgumentAt-answer.json`

* `Z_pmd-AbstractJavaRule-visit.json`

* `Z_spring-boot-ConfigurationPropertiesBinder-getBindHandler.json`

* `Z_spring-boot-JsonParserFactory-getJsonParser.json`

* `Z_spring-boot-TomcatDataSourcePoolMetadata-getMin.json`


# Parse errors

QUESTION: These are the two parse errors on `codeshovel/reid_icse2021`. Is this better or worse than what happens in `shaiful/master`?

### Z_lucene-solr-IndexWriter-shutdown - expecting 5 changes

JavaParser::parseMethods() - parse error. path: lucene/queryparser/src/test/org/apache/lucene/queryparser/analyzing/TestAnalyzingQueryParser.java


### flink-core/src/main/java/org/apache/flink/core/fs/FileSystem.java

JavaParser::parseMethods() - parse error. path: stratosphere-quickstart/quickstart-java/src/main/resources/archetype-resources/src/main/java/WordCountJob.java


# Shaiful's Final fails

### These are all in the evaluation (Z_ groups)

`shaiful/master` lists the following failures:

* https://github.com/shaifulcse/codeshovel/commit/28379d0a11c90d52180aef0427c49f5c5ad74040

	* spring-boot: 2
	* pmd: 1
	* mockito: 1
	* lucene: 1
	* jetty: 1
	* Intellij: 1
	* hibernate: 1
	* elasticsearch: 2
	* hadoop: 0
	* commons-io: 0

`codeshovel/icse_2021` has only one error:

* At HEAD commit for branch:
	* spring-boot: 0
	* pmd: 0
	* mockito: 0
	* lucene: 0
	* jetty: 0
	* Intellij: 0
	* hibernate: 0
	* elasticsearch: 1
	* hadoop: 0
	* commons-io: 0

QUESTION: is the Reid performance because his oracle files are wrong (and they should be failing)?

