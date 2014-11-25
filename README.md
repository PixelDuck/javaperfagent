javaperfagent
=============

An agent to track performances. Results are written into a file on the server in a JSON format. A GUI is available to help analysing results.
To plug the agent, add to JVM option -javaagent:\<PATH_TO_JAR\>=\<PATH_TO_CONFIG_FILE\>.
Configuration file is a simple text file:
* If line starts with '//' then this is a comment line
* You should specify where to write results with a line starting with ':' followed by the path to the config file. Example:
<code>:/tmp/stats.json</code>
* You can add some options starting with the character '$'. Options available are:
  * <code>$minRootTimeToTrackInMicros=<TIME IN microseconds></code>
specifies the minimum time for the root tracker to match in order to log results from this method
  * <code>$minTimeToTrackInMicros=<TIME IN microseconds></code>
specifies the minimum time to match for a subcall in order to log results from this method
  * <code>$trackParameters</code>
specifies that parameters should be tracked
  * <code>$debugConfigFile</code>
specifies that configuration loading should output debug informaiton
* You should add some classes or methods to track with: 
  * A full class name (means package with class name) starting with '+'. for example:
  <code>+java.util.ArrayList</code>
  * You can also specify an ending pattern for the package terminating the line with the character '\*'. For example:  <code>+com.test.Test*</code> will activate tracking for all classes from package 'com.test' with a name starting with 'Test'.
  * You can also specify a method name like <code>+java.util.ArrayList.add()</code> to track a particular method.
  * For each of this configuration, you can start the line with the character '-' in order to specify that you don't want to   track this entry. Example: <code>-com.test.Test.remove(int)</code> will not track the method 'remove(int)' from class 'com.test.Test'
  * For each of this configuration, you can start the line with the character '#' in order to specify that you want to track parameters for this entry. Example: <code>#com.test.Test.remove(int)</code> will track the method 'remove(int)' from class 'com.test.Test' and the parameter value from type 'int' will be tracked also

Config file example:
```
 // output file
  :/tmp/stats.json
 // options
 $minRootTimeToTrackInMicros=200
 // classes and methods to track
 \+com.mypackage.\*
 \#com.mypackage.service.\*
 \-com.mypackage.dao.MyDao.create\*()
```

Next steps
* implements on GUI auto expand when selecting a root node
* implements on GUI a field to search on name or on time spent
* implements output to http socket, create a socket listener collector
* make production of report asynchronous to minimize impact on performances
* link calls from differents threads (propagate threadlocal or rely on an ID)
* Maybe switch to a web interface to analyse results
