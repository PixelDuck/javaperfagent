javaperfagent
=============

 An agent to track performances. Results are written into a file on the server in a JSON format.
 The agent requires to pass the path to the configuration file.
 Ths configuration file is a text file based where each line can specify:
 * the path for the file used to output data: This line should start with the character {@code :} followed by the path. If the line ends with the character {@code +}, it means that the content should be append to the actual content of the file
 * a command starting with the sign {@code $}. Actual commands supported is {@code $nozero} which indicates to not log results if the duration is equals to 0 ms
 * a full class name (means package with class name). for example {@code java.util.ArrayList}. You cam also specify an ending pattern for the  *  package terminating the line with the character {@code *}. For example the line {@code com.test.Test*} will activate tracking for all classes  *  from package {@code com.test} with a name starting with {@code Test}. You can also specify a method name like  *  {@code java.util.ArrayList.add()} to tracked a particular method. For each of this configuration, you can also start the line with the character  *  {@code -} in order to specify that you don't want to track this entry.
