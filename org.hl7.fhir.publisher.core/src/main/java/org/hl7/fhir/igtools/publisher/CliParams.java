package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.List;

public class CliParams {

	public static final String DEBUG_LOG = "-debug-log";
	public static final String TRACE_LOG = "-trace-log";

	public static String getNamedParam(String[] args, String param) {
	  boolean found = false;
	  for (String a : args) {
		if (found)
		  return a;
		if (a.equals(param)) {
		  found = true;
		}
	  }
	  return null;
	}

	/**
	 * Returns every value supplied after a repeated flag, in command-line order.
	 * Use for flags that may be specified more than once, e.g. {@code -po a.po -po b.po}.
	 * A trailing occurrence of the flag with no following value is ignored.
	 */
	public static List<String> getNamedParams(String[] args, String param) {
	  List<String> values = new ArrayList<>();
	  for (int i = 0; i < args.length - 1; i++) {
		if (args[i].equals(param)) {
		  values.add(args[i + 1]);
		}
	  }
	  return values;
	}

	public static boolean hasNamedParam(String[] args, String param) {
	  for (String a : args) {
		if (a.equals(param)) {
		  return true;
		}
	  }
	  return false;
	}
}
