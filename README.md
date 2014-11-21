javaperfagent
=============

An agent to track performances. Results are written into a file on the server in a JSON format. A GUI is available to help analysing results.
To plug the agent, add to JVM option -javaagent:<PATH_TO_JAR>=<PATH_TO_CONFIG_FILE>.
Configuration file is a simple text file.

If line starts with '//' then this is a comment line

You should specify where to write results with a line starting with ':' followed by the path to the config file. Example:

	:/tmp/stats.json

You can add some options starting with the character '$'. Options available are:

	$minTimeToTrackInMs=<TIME IN MS>

	  specifies the minimum time to match in order to log results from this method

	$trackParameters

	  specifies that parameters should be tracked

You should add some classes or methods to track with: 
* A full class name (means package with class name) starting with '+'. for example:

	+java.util.ArrayList

* You can also specify an ending pattern for the package terminating the line with the character '*'. For example:

	+com.test.Test*

will activate tracking for all classes from package 'com.test' with a name starting with 'Test'.

* You can also specify a method name like

	+java.util.ArrayList.add()

to track a particular method. Notice that '*' suffix do not work with method names

* For each of this configuration, you can start the line with the character '-' in order to specify that you don't want to track this entry. Example:

	-com.test.Test.remove(int)

will not track the method 'remove(int)' from class 'com.test.Test'

* For each of this configuration, you can start the line with the character '#' in order to specify that you want to track parameters for this entry. Example:
	
	#com.test.Test.remove(int)
	
will track the method 'remove(int)' from class 'com.test.Test' and the parameter value from type 'int' will be tracked also


Next steps
* implments on GUI copy to file
* implements on GUI auto expand
* implements on GUI search on name
* implements on GUI search on time spent with expand 
* implements output to http socket / implements GUI input socket listener
* implement linking calls from differents thread
* implemnts filtering
